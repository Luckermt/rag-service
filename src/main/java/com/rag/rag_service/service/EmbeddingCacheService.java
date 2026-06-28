package com.rag.rag_service.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

@Component
public class EmbeddingCacheService {
    private final Map<String, List<Double>> cache = new ConcurrentHashMap<>();

    public List<Double> computeIfAbsent(String text, Supplier<List<Double>> supplier) {
        return cache.computeIfAbsent(text, k -> supplier.get());
    }
}