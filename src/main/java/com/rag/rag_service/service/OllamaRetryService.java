package com.rag.rag_service.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

@Component
public class OllamaRetryService {

    private final OllamaChatModel chatModel;

    public OllamaRetryService(OllamaChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Retry(name = "ollamaRetry")
    @CircuitBreaker(name = "ollamaCB")
    // @TimeLimiter(name = "ollamaTL")
    public ChatResponse call(Prompt prompt) {
        return chatModel.call(prompt);
    }
}