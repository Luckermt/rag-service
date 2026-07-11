package com.rag.rag_service.service;

import org.springframework.stereotype.Component;

import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;

@Component
public class WebSearchRetryService {

    private final WebSearchService webSearchService;

    public WebSearchRetryService(WebSearchService webSearchService) {
        this.webSearchService = webSearchService;
    }

    @Retry(name = "webSearchRetry")
    // @TimeLimiter(name = "webSearchTL")
    public String search(String query) {
        return webSearchService.search(query);
    }
}