package com.rag.rag_service.service;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import io.github.resilience4j.retry.annotation.Retry;

@Component
public class VectorStoreRetryService {

    private final VectorStore vectorStore;

    public VectorStoreRetryService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Retry(name = "qdrantRetry")
    // @TimeLimiter(name = "qdrantTL")
    public List<Document> similaritySearch(SearchRequest request) {
        return vectorStore.similaritySearch(request);
    }
}