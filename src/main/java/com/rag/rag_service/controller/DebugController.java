package com.rag.rag_service.controller;

import com.rag.rag_service.model.openai.ChatRequest;
import com.rag.rag_service.service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/rag")
@RequiredArgsConstructor
public class DebugController {

    private final RagService ragService;

    @PostMapping("/debug")
    public ResponseEntity<Map<String, Object>> debug(@RequestBody ChatRequest request) {
        // Используем специальный метод, который возвращает детализированный ответ
        return ResponseEntity.ok(ragService.debugChat(request));
    }
}