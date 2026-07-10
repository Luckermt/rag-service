package com.rag.rag_service.model.openai;

import lombok.Data;

@Data
public class Source {
    private String documentId;
    private String fileName;
    private Integer position;
    private Double similarityScore;
}