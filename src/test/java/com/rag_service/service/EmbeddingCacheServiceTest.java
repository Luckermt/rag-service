package com.rag_service.service;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import com.rag.rag_service.service.EmbeddingCacheService;

class EmbeddingCacheServiceTest {

    private final EmbeddingCacheService cacheService = new EmbeddingCacheService();

    @Test
    void computeIfAbsent_shouldCacheEmbeddings() {
        // given
        String text = "hello";
        List<Double> embedding = List.of(0.1, 0.2);

        // when
        List<Double> first = cacheService.computeIfAbsent(text, () -> embedding);
        List<Double> second = cacheService.computeIfAbsent(text, () -> {
            throw new RuntimeException("Should not be called again");
        });

        // then
        assertThat(first).isSameAs(embedding);
        assertThat(second).isSameAs(embedding);
        assertThat(cacheService.computeIfAbsent("other", () -> List.of(0.3))).isNotSameAs(embedding);
    }
}