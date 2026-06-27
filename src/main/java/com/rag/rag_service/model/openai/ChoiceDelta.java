package com.rag.rag_service.model.openai;

import lombok.Data;

@Data
public class ChoiceDelta {
    private String role;
    private String content;
}