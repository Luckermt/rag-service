package com.rag.rag_service.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.rag_service.model.openai.ChatCompletionChunk;
import com.rag.rag_service.model.openai.ChatRequest;
import com.rag.rag_service.model.openai.ChatResponse;
import com.rag.rag_service.model.openai.Choice;
import com.rag.rag_service.model.openai.ChoiceDelta;
import com.rag.rag_service.model.openai.Message;
import com.rag.rag_service.model.openai.Usage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class RagService {
    private final OllamaChatModel chatModel;
    private final EmbeddingModel embeddingModel;
    private final VectorStore vectorStore;
    private final EmbeddingCacheService cache;
    private final WebSearchService webSearchService;
    private final WebClient ollamaWebClient;
    private final ObjectMapper objectMapper;

    @Value("${rag.retrieval.top-k}")
    private int topK;

    @Value("${rag.retrieval.similarity-threshold}")
    private double similarityThreshold;

    private static final String SYSTEM_TEMPLATE = """
            Ты — помощник, отвечающий на основе контекста.
            Отвечай только на основе контекста. Если ответа нет — скажи об этом.
            Не придумывай факты. Указывай источник (имя файла), если возможно.
            
            Контекст:
            %s
            """;

    public ChatResponse chat(ChatRequest request) {
        String userMessage = extractLastUserMessage(request.getMessages());
        List<Document> retrieved = retrieveRelevant(userMessage);
        String context = buildContext(retrieved);

        if ((retrieved.isEmpty() || context.length() < 100) && webSearchService.isEnabled()) {
            String webResults = webSearchService.search(userMessage);
            if (webResults != null && !webResults.isBlank()) {
                context += "\n\nРезультаты веб-поиска:\n" + webResults;
            }
        }

        String systemPrompt = String.format(SYSTEM_TEMPLATE, context);
        var prompt = new Prompt(List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(userMessage)
        ));

        var aiResponse = chatModel.call(prompt);
        return mapToChatResponse(aiResponse, request.getModel());
    }

    public Flux<ServerSentEvent<String>> streamChat(ChatRequest request) {
        String userMessage = extractLastUserMessage(request.getMessages());
        List<Document> retrieved = retrieveRelevant(userMessage);
        String context = buildContext(retrieved);
        if ((retrieved.isEmpty() || context.length() < 100) && webSearchService.isEnabled()) {
            String webResults = webSearchService.search(userMessage);
            if (webResults != null && !webResults.isBlank()) {
                context += "\n\nРезультаты веб-поиска:\n" + webResults;
            }
        }

        String systemPrompt = String.format(SYSTEM_TEMPLATE, context);
        Map<String, Object> body = Map.of(
                "model", request.getModel() != null ? request.getModel() : "minimax-m2.5:cloud",
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userMessage)
                ),
                "stream", true
        );

        String chatId = "chatcmpl-" + UUID.randomUUID().toString().substring(0, 8);
        return ollamaWebClient.post()
                .uri("/api/chat")
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(OllamaStreamChunk.class)
                .map(chunk -> convertToOpenAiChunk(chunk, chatId, request.getModel()))
                .concatWith(Mono.just(ServerSentEvent.<String>builder()
                        .data("[DONE]").build()));
    }

    private List<Document> retrieveRelevant(String query) {
        List<Double> queryEmbedding = cache.computeIfAbsent(query, () -> {
            float[] embArray = embeddingModel.embed(query); // returns float[]
            return IntStream.range(0, embArray.length)
                    .mapToObj(i -> (double) embArray[i])
                    .toList();
        });
        return vectorStore.similaritySearch(
                org.springframework.ai.vectorstore.SearchRequest.query(query)
                        .withTopK(topK)
                        .withSimilarityThreshold(similarityThreshold)   // fixed variable name
        );
    }

    private String buildContext(List<Document> docs) {
        return docs.stream()
                .map(d -> d.getContent() + "\n(источник: " + d.getMetadata().getOrDefault("file_name", "unknown") + ")")
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    private String extractLastUserMessage(List<Message> messages) {
        return messages.stream()
                .filter(m -> "user".equals(m.getRole()))
                .map(Message::getContent)
                .reduce((first, second) -> second)
                .orElseThrow(() -> new IllegalArgumentException("No user message found"));
    }

    private ChatResponse mapToChatResponse(org.springframework.ai.chat.model.ChatResponse aiResp, String model) {
        ChatResponse resp = new ChatResponse();
        resp.setId("chatcmpl-" + UUID.randomUUID().toString().substring(0, 8));
        resp.setCreated(Instant.now().getEpochSecond());
        resp.setModel(model != null ? model : "minimax-m2.5:cloud");

        var aiMsg = aiResp.getResult().getOutput().getContent();
        Message assistantMsg = new Message("assistant", aiMsg);
        Choice choice = new Choice();
        choice.setIndex(0);
        choice.setMessage(assistantMsg);
        choice.setFinish_reason("stop");
        resp.setChoices(List.of(choice));

        Usage usage = new Usage();
        usage.setPromptTokens(aiResp.getMetadata().getUsage().getPromptTokens());
        usage.setCompletionTokens(aiResp.getMetadata().getUsage().getGenerationTokens());
        usage.setTotalTokens(usage.getPromptTokens() + usage.getCompletionTokens());
        resp.setUsage(usage);
        return resp;
    }

    private ServerSentEvent<String> convertToOpenAiChunk(OllamaStreamChunk chunk, String chatId, String model) {
        ChatCompletionChunk openAiChunk = new ChatCompletionChunk();
        openAiChunk.setId(chatId);
        openAiChunk.setCreated(Instant.now().getEpochSecond());
        openAiChunk.setModel(model != null ? model : "minimax-m2.5:cloud");

        Choice choice = new Choice();
        choice.setIndex(0);
        ChoiceDelta delta = new ChoiceDelta();
        if (chunk.getMessage() != null) {
            delta.setContent(chunk.getMessage().getContent());
            if (chunk.getMessage().getRole() != null) {
                delta.setRole(chunk.getMessage().getRole());
            }
        }
        choice.setDelta(delta);
        choice.setFinish_reason(chunk.isDone() ? "stop" : null);
        openAiChunk.setChoices(List.of(choice));

        try {
            String json = objectMapper.writeValueAsString(openAiChunk);
            return ServerSentEvent.<String>builder()
                    .data(json)
                    .build();
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize chunk", e);
            return ServerSentEvent.<String>builder()
                    .data("{}")
                    .build();
        }
    }

    @lombok.Data
    private static class OllamaStreamChunk {
        private Message message;
        private boolean done;
    }

    // @lombok.Data
    // private static class Message {
    //     private String role;
    //     private String content;
    // }
}