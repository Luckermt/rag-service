package com.rag.rag_service.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rag.rag_service.model.openai.ChatRequest;
import com.rag.rag_service.model.openai.ModelInfo;
import com.rag.rag_service.model.openai.ModelsResponse;
import com.rag.rag_service.service.RagService;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

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
        if (Boolean.TRUE.equals(request.getStream())) {
            Flux<org.springframework.http.codec.ServerSentEvent<String>> stream =
                    ragService.streamChat(request);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(stream);
        } else {
            return ResponseEntity.ok(ragService.chat(request));
        }
    }
}
