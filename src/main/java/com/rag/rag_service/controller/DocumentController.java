package com.rag.rag_service.controller;

import com.rag.rag_service.model.document.DocumentMetadata;
import com.rag.rag_service.model.document.DocumentUploadResult;
import com.rag.rag_service.service.DocumentService;
import com.rag.rag_service.util.SsrfProtection;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final SsrfProtection ssrfProtection;

    @Value("${rag.url-download.timeout:30s}")
    private Duration downloadTimeout;

    @Value("${rag.url-download.max-size:10485760}")
    private long maxDownloadSize;

    @PostMapping("/upload")
    public ResponseEntity<DocumentUploadResult> upload(@RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(documentService.upload(file));
    }

    @PostMapping("/upload-from-url")
    public ResponseEntity<DocumentUploadResult> uploadFromUrl(@RequestParam("url") String url) throws IOException {
        ssrfProtection.validateUrl(url);

        byte[] content = downloadContent(url);
        String fileName = extractFileNameFromUrl(url);

        return ResponseEntity.ok(documentService.uploadFromBytes(content, fileName));
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

    private byte[] downloadContent(String urlString) throws IOException {
        WebClient client = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize((int) maxDownloadSize))
                .build();

        return client.get()
                .uri(urlString)
                .header("User-Agent", "RAG-Service")
                .retrieve()
                .bodyToMono(byte[].class)
                .timeout(downloadTimeout)
                .block();
    }

    private String extractFileNameFromUrl(String url) {
        try {
            URL parsed = new URL(url);
            String path = parsed.getPath();
            if (path != null && !path.isEmpty()) {
                String[] segments = path.split("/");
                String last = segments[segments.length - 1];
                if (last.contains(".")) {
                    return last;
                }
            }
            return parsed.getHost() + ".html";
        } catch (MalformedURLException e) {
            return "unknown.html";
        }
    }
}