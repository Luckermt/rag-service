package com.rag.rag_service.controller;

import com.rag.rag_service.model.openai.ChatRequest;
import com.rag.rag_service.model.openai.ChatResponse;
import com.rag.rag_service.model.openai.ModelInfo;
import com.rag.rag_service.model.openai.ModelsResponse;
import com.rag.rag_service.service.RagService;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class OpenAiController {
    private final RagService ragService;

    @GetMapping("/models")
    public ResponseEntity<ModelsResponse> listModels() {
        var models = List.of(new ModelInfo("minimax-m2.5:cloud", "model", "local"));
        return ResponseEntity.ok(new ModelsResponse("list", models));
    }

    @PostMapping("/chat/completions")
    public ResponseEntity<?> chatCompletions(@RequestBody ChatRequest request) {
        String requestId = UUID.randomUUID().toString();
        try {
            MDC.put("requestId", requestId);
            if (Boolean.TRUE.equals(request.getStream())) {
                Flux<ServerSentEvent<String>> stream = ragService.streamChat(request, requestId);
                return ResponseEntity.ok()
                        .contentType(MediaType.TEXT_EVENT_STREAM)
                        .body(stream);
            } else {
                ChatResponse response = ragService.chat(request, requestId);
                return ResponseEntity.ok(response);
            }
        } finally {
            MDC.remove("requestId");
        }
    }
}