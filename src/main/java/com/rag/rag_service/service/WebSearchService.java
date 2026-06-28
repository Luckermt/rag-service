package com.rag.rag_service.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.RequiredArgsConstructor;
@Service
@RequiredArgsConstructor
public class WebSearchService {
    private final WebClient.Builder webClientBuilder;

    @Value("${rag.web-search.enabled:false}")
    private boolean enabled;

    @Value("${rag.web-search.searxng-url}")
    private String searxngUrl;

    public boolean isEnabled() {
        return enabled;
    }

    public String search(String query) {
        if (!enabled) return "";
        try {
            WebClient client = webClientBuilder.baseUrl(searxngUrl).build();
            Map<String, Object> params = Map.of(
                    "q", query,
                    "format", "json",
                    "categories", "general"
            );
            Map response = client.get()
                    .uri(uriBuilder -> uriBuilder.path("/search").queryParam("q", query)
                            .queryParam("format", "json").build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            if (response != null && response.containsKey("results")) {
                List<Map<String, String>> results = (List<Map<String, String>>) response.get("results");
                return results.stream()
                        .limit(3)
                        .map(r -> r.getOrDefault("title", "") + ": " + r.getOrDefault("content", ""))
                        .collect(Collectors.joining("\n\n"));
            }
        } catch (Exception e) {
        }
        return "";
    }
}