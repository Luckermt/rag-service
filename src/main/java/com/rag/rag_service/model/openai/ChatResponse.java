package com.rag.rag_service.model.openai;

import lombok.Data;
import java.util.List;

@Data
public class ChatResponse {
    private String id;
    private String object = "chat.completion";
    private long created;
    private String model;
    private List<Choice> choices;
    private Usage usage;
}