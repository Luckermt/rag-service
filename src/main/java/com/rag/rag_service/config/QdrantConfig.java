package com.rag.rag_service.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;


@Configuration
public class QdrantConfig {

    @Value("${spring.ai.vectorstore.qdrant.host}")
    private String host;

    @Value("${spring.ai.vectorstore.qdrant.port}")
    private int port;

    @Value("${spring.ai.vectorstore.qdrant.api-key:}")
    private String apiKey;

    @Value("${spring.ai.vectorstore.qdrant.use-tls:false}")
    private boolean useTls;

    @Value("${spring.ai.vectorstore.qdrant.collection-name:vector_store}")
    private String collectionName;

    @Value("${spring.ai.vectorstore.qdrant.initialize-schema:false}")
    private boolean initializeSchema;

    @Bean
    public QdrantClient qdrantClient() {
        QdrantGrpcClient.Builder builder = QdrantGrpcClient.newBuilder(host, port, useTls);
        if (apiKey != null && !apiKey.isBlank()) {
            // Forward the API key as gRPC metadata so every call is authorized.
            builder.withApiKey(apiKey);
        }
        return new QdrantClient(builder.build());
    }

    @Bean
    public VectorStore vectorStore(QdrantClient qdrantClient, EmbeddingModel embeddingModel) {
        return new QdrantVectorStore(qdrantClient, collectionName, embeddingModel, initializeSchema);
    }
}

