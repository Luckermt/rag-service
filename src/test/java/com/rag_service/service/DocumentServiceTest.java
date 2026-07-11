package com.rag_service.service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import com.rag.rag_service.model.document.DocumentMetadata;
import com.rag.rag_service.model.document.DocumentStatus;
import com.rag.rag_service.model.document.DocumentUploadResult;
import com.rag.rag_service.parser.DocumentParserFactory;
import com.rag.rag_service.repository.DocumentMetadataRepository;
import com.rag.rag_service.service.DocumentService;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentMetadataRepository docRepo;

    @Mock
    private DocumentParserFactory parserFactory;

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private VectorStore vectorStore;

    @Mock
    private QdrantClient qdrantClient;

    @Mock
    private Executor asyncExecutor;

    @InjectMocks
    private DocumentService documentService;

    @BeforeEach
    void setUp() {
        // Устанавливаем значения из @Value через ReflectionTestUtils
        ReflectionTestUtils.setField(documentService, "chunkSize", 1000);
        ReflectionTestUtils.setField(documentService, "overlap", 200);
        ReflectionTestUtils.setField(documentService, "maxFileSize", 52_428_800L);
        ReflectionTestUtils.setField(documentService, "collectionName", "rag_chunks");
    }

    @Test
    void upload_shouldSaveProcessingAndProcessAsync() throws IOException {
        // given
        MultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "content".getBytes());
        UUID docId = UUID.randomUUID();

        ArgumentCaptor<DocumentMetadata> captor = ArgumentCaptor.forClass(DocumentMetadata.class);
        when(docRepo.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        when(parserFactory.getParser("test.txt")).thenReturn(is -> "parsed text");

        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(asyncExecutor).execute(any(Runnable.class));

        float[] embedding = {0.1f, 0.2f};
        when(embeddingModel.embed(anyString())).thenReturn(embedding);

        DocumentUploadResult result = documentService.upload(file);

        assertThat(result.getStatus()).isEqualTo(DocumentStatus.PROCESSING.name());

        DocumentMetadata saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(DocumentStatus.PROCESSING);
        assertThat(saved.getFileName()).isEqualTo("test.txt");

        verify(asyncExecutor).execute(any(Runnable.class));

        ArgumentCaptor<DocumentMetadata> finalCaptor = ArgumentCaptor.forClass(DocumentMetadata.class);
        verify(docRepo, times(2)).save(finalCaptor.capture());
        List<DocumentMetadata> allSaved = finalCaptor.getAllValues();
        assertThat(allSaved).hasSize(2);
        assertThat(allSaved.get(1).getStatus()).isEqualTo(DocumentStatus.COMPLETED);
        assertThat(allSaved.get(1).getChunkCount()).isPositive();

        verify(vectorStore).add(anyList());
    }

    @Test
    void upload_shouldThrowWhenFileTooLarge() {
        // given
        MultipartFile file = new MockMultipartFile("file", "large.txt", "text/plain", new byte[52_428_801]);

        // when / then
        assertThatThrownBy(() -> documentService.upload(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds");
    }

    @Test
    void listDocuments_shouldReturnAll() {
        // given
        List<DocumentMetadata> docs = List.of(DocumentMetadata.builder().id(UUID.randomUUID()).build());
        when(docRepo.findAll()).thenReturn(docs);

        // when
        List<DocumentMetadata> result = documentService.listDocuments();

        // then
        assertThat(result).hasSize(1);
        verify(docRepo).findAll();
    }

    @Test
    void deleteDocument_shouldDeleteFromQdrantAndRepo() throws Exception {
        // given
        UUID docId = UUID.randomUUID();
        DocumentMetadata doc = DocumentMetadata.builder().id(docId).fileName("test.txt").build();
        when(docRepo.findById(docId)).thenReturn(Optional.of(doc));

        // Мокаем deleteAsync – возвращаем CompletableFuture
        // when(qdrantClient.deleteAsync(any(Points.DeletePoints.class)))
        //         .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));

        // when
        documentService.deleteDocument(docId);

        // then
        verify(qdrantClient).deleteAsync(any(Points.DeletePoints.class));
        verify(docRepo).deleteById(docId);
    }

    @Test
    void deleteDocument_shouldNotThrowIfDocNotFound() {
        // given
        UUID docId = UUID.randomUUID();
        when(docRepo.findById(docId)).thenReturn(Optional.empty());

        // when
        documentService.deleteDocument(docId);

        // then
        verify(qdrantClient, never()).deleteAsync(any());
        verify(docRepo, never()).deleteById(any());
    }
}