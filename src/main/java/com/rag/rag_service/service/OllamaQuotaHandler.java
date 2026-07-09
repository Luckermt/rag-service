package com.rag.rag_service.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.rag_service.exception.QuotaExceededException;

import reactor.core.publisher.Mono;

@Component
public class OllamaQuotaHandler implements ExchangeFilterFunction {

    private final ObjectMapper objectMapper;

    public OllamaQuotaHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        return next.exchange(request)
                .flatMap(response -> {
                    if (response.statusCode().equals(HttpStatus.TOO_MANY_REQUESTS)) {
                        return response.bodyToMono(String.class)
                                .flatMap(body -> {
                                    String resetTime = extractResetTime(body);
                                    return Mono.error(new QuotaExceededException("Ollama quota exceeded", resetTime));
                                });
                    }
                    return Mono.just(response);
                });
    }

    private String extractResetTime(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode resetNode = root.get("reset_time");
            if (resetNode != null && resetNode.isTextual()) {
                return resetNode.asText();
            }
        } catch (Exception e) {
            // log warn
        }
        return "unknown";
    }
}