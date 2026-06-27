package com.rag.rag_service.model.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Choice {
    private int index;
    private Message message;          // for non-stream
    private ChoiceDelta delta;        // for stream
    private String finish_reason;
}