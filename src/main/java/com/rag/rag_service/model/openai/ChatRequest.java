package com.rag.rag_service.model.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
public class ChatRequest {
    private String model;
    private List<Message> messages;
    private Boolean stream = false;
    private Double temperature;
    @JsonProperty("max_tokens")
    private Integer maxTokens;
}