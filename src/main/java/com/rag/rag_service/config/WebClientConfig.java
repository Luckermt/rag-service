package com.rag.rag_service.config;

import com.rag.rag_service.service.OllamaQuotaHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${spring.ai.ollama.base-url}")
    private String ollamaBaseUrl;

    @Value("${spring.ai.ollama.api-key}")
    private String apiKey;

    @Bean
    public WebClient ollamaWebClient(OllamaQuotaHandler quotaHandler) {
        return WebClient.builder()
                .baseUrl(ollamaBaseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .filter((request, next) -> {
                    return next.exchange(request);
                })
                .build();
    }
}