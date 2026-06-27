package com.rag.rag_service.model.openai;

import java.util.List;

import lombok.Data;

@Data
public class ChatCompletionChunk {
    private String id;
    private String object = "chat.completion.chunk";
    private long created;
    private String model;
    private List<Choice> choices;
}