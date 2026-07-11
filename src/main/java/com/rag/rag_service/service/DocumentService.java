package com.rag.rag_service.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.rag.rag_service.model.document.DocumentMetadata;
import com.rag.rag_service.model.document.DocumentStatus;
import com.rag.rag_service.model.document.DocumentUploadResult;
import com.rag.rag_service.parser.DocumentParserFactory;
import com.rag.rag_service.repository.DocumentMetadataRepository;
import com.rag.rag_service.util.ChunkUtil;

import static io.qdrant.client.ConditionFactory.matchKeyword;
import io.qdrant.client.ValueFactory;
import io.qdrant.client.grpc.Points;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final DocumentMetadataRepository docRepo;
    private final DocumentParserFactory parserFactory;
    private final EmbeddingModel embeddingModel;
    private final QdrantRetryService qdrantRetryService;

    @Qualifier("documentProcessingExecutor")
    private final java.util.concurrent.Executor asyncExecutor;

    @Value("${spring.ai.vectorstore.qdrant.collection-name}")
    private String collectionName;

    @Value("${rag.chunk.size}")
    private int chunkSize;

    @Value("${rag.chunk.overlap}")
    private int overlap;

    @Value("${rag.documents.max-file-size}")
    private long maxFileSize;

    private final Cache<String, List<Double>> embeddingCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(java.time.Duration.ofHours(1))
            .recordStats()
            .build();

    private final Map<UUID, Set<String>> documentChunksCache = new ConcurrentHashMap<>();

    public DocumentUploadResult upload(MultipartFile file) throws IOException {
        log.info("Получен запрос на загрузку файла: {}, размер: {} байт",
                file.getOriginalFilename(), file.getSize());
        if (file.getSize() > maxFileSize) {
            log.warn("Файл {} превышает лимит {} байт", file.getOriginalFilename(), maxFileSize);
            throw new IllegalArgumentException("File size exceeds " + maxFileSize + " bytes");
        }
        return uploadFromBytes(file.getBytes(), file.getOriginalFilename());
    }

    public DocumentUploadResult uploadFromBytes(byte[] content, String fileName) {
        UUID id = UUID.randomUUID();
        log.info("Создан документ ID: {}, файл: {}", id, fileName);
        DocumentMetadata doc = DocumentMetadata.builder()
                .id(id)
                .fileName(fileName)
                .status(DocumentStatus.PROCESSING)
                .chunkCount(0)
                .build();
        docRepo.save(doc);

        CompletableFuture.runAsync(() -> processDocument(doc, content, fileName), asyncExecutor)
                .exceptionally(e -> {
                    log.error("Критическая ошибка при асинхронной обработке документа {}", id, e);
                    updateStatus(id, DocumentStatus.FAILED);
                    return null;
                });

        log.info("Документ {} поставлен в очередь на обработку", id);
        return new DocumentUploadResult(id.toString(), fileName, DocumentStatus.PROCESSING.name(), 0);
    }

    private void processDocument(DocumentMetadata doc, byte[] content, String fileName) {
        try {
            String text = parserFactory.getParser(fileName).parse(
                    new java.io.ByteArrayInputStream(content));
            List<String> chunks = ChunkUtil.chunk(text, chunkSize, overlap);

            documentChunksCache.computeIfAbsent(doc.getId(), k -> ConcurrentHashMap.newKeySet())
                    .addAll(chunks);

            List<Points.PointStruct> points = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                String chunk = chunks.get(i);

                List<Double> embedding = embeddingCache.get(chunk, key -> {
                    float[] embArray = embeddingModel.embed(key);
                    return IntStream.range(0, embArray.length)
                            .mapToObj(k -> (double) embArray[k])
                            .toList();
                });

                List<Float> vector = embedding.stream().map(Double::floatValue).toList();

                String uniqueId = doc.getId().toString() + "_" + i;
                UUID pointUuid = UUID.nameUUIDFromBytes(uniqueId.getBytes());
                String pointId = pointUuid.toString();

                Points.PointStruct point = Points.PointStruct.newBuilder()
                        .setId(Points.PointId.newBuilder().setUuid(pointId).build())
                        .setVectors(Points.Vectors.newBuilder()
                                .setVector(Points.Vector.newBuilder().addAllData(vector).build())
                                .build())
                        .putPayload("file_name", ValueFactory.value(fileName))
                        .putPayload("document_id", ValueFactory.value(doc.getId().toString()))
                        .putPayload("position", ValueFactory.value(String.valueOf(i)))
                        .putPayload("chunk_text", ValueFactory.value(chunk))
                        .build();

                points.add(point);
            }

            qdrantRetryService.upsert(
                    Points.UpsertPoints.newBuilder()
                            .setCollectionName(collectionName)
                            .addAllPoints(points)
                            .build()
            );

            doc.setChunkCount(chunks.size());
            doc.setStatus(DocumentStatus.COMPLETED);
            docRepo.save(doc);
            log.info("Документ {} успешно обработан, {} чанков", doc.getId(), chunks.size());

        } catch (Exception e) {
            log.error("Ошибка обработки документа {}", doc.getId(), e);
            updateStatus(doc.getId(), DocumentStatus.FAILED);
        }
    }

    @Transactional
    public void updateStatus(UUID id, DocumentStatus status) {
        docRepo.findById(id).ifPresent(doc -> {
            doc.setStatus(status);
            docRepo.save(doc);
            log.info("Статус документа {} обновлён на {}", id, status);
        });
    }

    public List<DocumentMetadata> listDocuments() {
        log.debug("Запрос списка всех документов");
        return docRepo.findAll();
    }

    public void deleteDocument(UUID id) {
        log.info("Запрос на удаление документа {}", id);
        DocumentMetadata doc = docRepo.findById(id).orElse(null);
        if (doc == null) {
            log.warn("Документ {} не найден в БД, удаление пропущено", id);
            return;
        }

        try {
            log.debug("Удаление точек Qdrant для документа {} из коллекции '{}'", id, collectionName);
            qdrantRetryService.delete(
                    Points.DeletePoints.newBuilder()
                            .setCollectionName(collectionName)
                            .setPoints(Points.PointsSelector.newBuilder()
                                    .setFilter(Points.Filter.newBuilder()
                                            .addMust(matchKeyword("document_id", id.toString()))
                                            .build())
                                    .build())
                            .build()
            );

            log.info("Удалены Qdrant-точки для документа {}", id);

            Set<String> chunkTexts = documentChunksCache.remove(id);
            if (chunkTexts != null && !chunkTexts.isEmpty()) {
                int invalidated = 0;
                for (String text : chunkTexts) {
                    embeddingCache.invalidate(text);
                    invalidated++;
                }
                log.info("Инвалидировано {} записей кэша для документа {}", invalidated, id);
            } else {
                log.debug("Нет чанков в кэше для документа {}", id);
            }

            docRepo.deleteById(id);
            log.info("Документ {} удалён из БД", id);

        } catch (Exception e) {
            log.error("Не удалось удалить Qdrant-точки для документа {}, БД-запись сохранена", id, e);
            throw new RuntimeException("Failed to delete document from vector store", e);
        }
    }

    public Map<String, Object> getCacheStats() {
        var stats = embeddingCache.stats();
        return Map.of(
                "size", embeddingCache.estimatedSize(),
                "hitRate", stats.hitRate(),
                "missRate", stats.missRate(),
                "evictionCount", stats.evictionCount(),
                "totalLoadTime", stats.totalLoadTime()
        );
    }
}