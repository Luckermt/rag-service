package com.rag.rag_service.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.IntStream;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.rag.rag_service.model.document.DocumentMetadata;
import com.rag.rag_service.model.document.DocumentStatus;
import com.rag.rag_service.model.document.DocumentUploadResult;
import com.rag.rag_service.parser.DocumentParserFactory;
import com.rag.rag_service.repository.DocumentMetadataRepository;
import com.rag.rag_service.util.ChunkUtil;

import static io.qdrant.client.ConditionFactory.matchKeyword;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points;
import io.qdrant.client.grpc.Points.DeletePoints;
import io.qdrant.client.grpc.Points.PointsSelector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {
    private final DocumentMetadataRepository docRepo;
    private final DocumentParserFactory parserFactory;
    private final EmbeddingModel embeddingModel;
    private final VectorStore vectorStore;
    private final EmbeddingCacheService cache;
    private final QdrantClient qdrantClient;

    @Value("${spring.ai.vectorstore.qdrant.collection-name}")
    private String collectionName;

    @Qualifier("documentProcessingExecutor")
    private final Executor asyncExecutor;

    @Value("${rag.chunk.size}")
    private int chunkSize;

    @Value("${rag.chunk.overlap}")
    private int overlap;

    @Value("${rag.documents.max-file-size}")
    private long maxFileSize;

    public DocumentUploadResult upload(MultipartFile file) throws IOException {
        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException("File size exceeds " + maxFileSize + " bytes");
        }
        return uploadFromBytes(file.getBytes(), file.getOriginalFilename());
    }

    public DocumentUploadResult uploadFromBytes(byte[] content, String fileName) {
        UUID id = UUID.randomUUID();
        DocumentMetadata doc = DocumentMetadata.builder()
                .id(id)
                .fileName(fileName)
                .status(DocumentStatus.PROCESSING)
                .chunkCount(0)
                .build();
        docRepo.save(doc);

        CompletableFuture.runAsync(() -> processDocument(doc, content, fileName), asyncExecutor)
                .exceptionally(e -> {
                    log.error("Processing failed for doc {}", id, e);
                    updateStatus(id, DocumentStatus.FAILED);
                    return null;
                });

        return new DocumentUploadResult(id.toString(), fileName, DocumentStatus.PROCESSING.name(), 0);
    }

    private void processDocument(DocumentMetadata doc, byte[] content, String fileName) {
        try {
            String text = parserFactory.getParser(fileName).parse(
                    new java.io.ByteArrayInputStream(content));
            List<String> chunks = ChunkUtil.chunk(text, chunkSize, overlap);

            List<Document> docsToStore = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                String chunk = chunks.get(i);
                List<Double> embedding = cache.computeIfAbsent(chunk, () -> {
                float[] embArray = embeddingModel.embed(chunk);
                return IntStream.range(0, embArray.length)
                        .mapToObj(k -> (double) embArray[k])
                        .toList();
            });
                Map<String, Object> metadata = Map.of(
                        "file_name", fileName,
                        "document_id", doc.getId().toString(),
                        "position", String.valueOf(i),
                        "chunk_text", chunk
                );
                Document vectorDoc = new Document(chunk, metadata);
                docsToStore.add(vectorDoc);
            }
            vectorStore.add(docsToStore);

            doc.setChunkCount(chunks.size());
            doc.setStatus(DocumentStatus.COMPLETED);
            docRepo.save(doc);
            log.info("Document {} processed: {} chunks", doc.getId(), chunks.size());
        } catch (Exception e) {
            log.error("Error processing document {}", doc.getId(), e);
            updateStatus(doc.getId(), DocumentStatus.FAILED);
        }
    }

    @Transactional
    public void updateStatus(UUID id, DocumentStatus status) {
        docRepo.findById(id).ifPresent(doc -> {
            doc.setStatus(status);
            docRepo.save(doc);
        });
    }

    public List<DocumentMetadata> listDocuments() {
        return docRepo.findAll();
    }

    @Transactional
    public void deleteDocument(UUID id) {
        docRepo.findById(id).ifPresent(doc -> {
            try {
                qdrantClient.deleteAsync(
                    DeletePoints.newBuilder()
                        .setCollectionName(collectionName)
                        .setPoints(PointsSelector.newBuilder()
                            .setFilter(Points.Filter.newBuilder()
                                .addMust(matchKeyword("document_id", id.toString()))
                                .build())
                            .build())
                        .build()
                ).get();
                log.info("Deleted Qdrant points for document {}", id);
            } catch (Exception e) {
                log.error("Failed to delete chunks for document {}", id, e);
            }
            docRepo.deleteById(id);
        });
    }
}