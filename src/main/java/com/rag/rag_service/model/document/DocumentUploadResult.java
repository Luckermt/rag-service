package com.rag.rag_service.model.document;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DocumentUploadResult {
    private String id;
    private String fileName;
    private String status;
    private int chunkCount;
}