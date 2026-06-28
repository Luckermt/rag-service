package com.rag.rag_service.controller;

import com.rag.rag_service.model.document.DocumentMetadata;
import com.rag.rag_service.model.document.DocumentUploadResult;
import com.rag.rag_service.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {
    private final DocumentService documentService;

    @PostMapping("/upload")
    public ResponseEntity<DocumentUploadResult> upload(@RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(documentService.upload(file));
    }

    @GetMapping
    public ResponseEntity<List<DocumentMetadata>> list() {
        return ResponseEntity.ok(documentService.listDocuments());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        documentService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }
}