package com.rag.rag_service.service;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.stereotype.Component;

import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points;

@Component
public class QdrantRetryService {

    private final QdrantClient qdrantClient;

    public QdrantRetryService(QdrantClient qdrantClient) {
        this.qdrantClient = qdrantClient;
    }

    @Retry(name = "qdrantRetry")
    // @TimeLimiter(name = "qdrantTL")
    public Points.UpdateResult upsert(Points.UpsertPoints request)
            throws InterruptedException, ExecutionException, TimeoutException {
        return qdrantClient.upsertAsync(request).get(30, TimeUnit.SECONDS);
    }

    @Retry(name = "qdrantRetry")
    // @TimeLimiter(name = "qdrantTL")
    public Points.UpdateResult delete(Points.DeletePoints request)
            throws InterruptedException, ExecutionException, TimeoutException {
        return qdrantClient.deleteAsync(request).get(30, TimeUnit.SECONDS);
    }
}