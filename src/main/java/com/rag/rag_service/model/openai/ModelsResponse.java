package com.rag.rag_service.model.openai;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public class ModelsResponse {
    private String object = "list";
    private List<ModelInfo> data;
}