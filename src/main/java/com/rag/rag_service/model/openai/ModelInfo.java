package com.rag.rag_service.model.openai;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ModelInfo {
    private String id;
    private String object = "model";
    private String owned_by;
}
