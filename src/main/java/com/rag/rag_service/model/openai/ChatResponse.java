package com.rag.rag_service.model.openai;

import java.util.List;

import lombok.Data;

@Data
public class ChatResponse {
    private String id;
    private String object = "chat.completion";
    private long created;
    private String model;
    private List<Choice> choices;
    private Usage usage;

    private String requestId;
    private List<Source> sources;
    private Long retrievalMs;
    private Long promptMs;
    private Long generationMs;
    private Long totalMs;
}