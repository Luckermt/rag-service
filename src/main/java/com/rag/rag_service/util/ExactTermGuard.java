package com.rag.rag_service.util;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class ExactTermGuard {

    // Регулярки для извлечения технических токенов
    private static final Pattern PATH_PATTERN = Pattern.compile("(/[\\w\\-./]+)");
    private static final Pattern FILE_PATTERN = Pattern.compile("\\b([\\w\\-]+\\.\\w{2,4})\\b");
    private static final Pattern CONSTANT_PATTERN = Pattern.compile("\\b[A-Z_]{2,}\\b");
    private static final Pattern CAMEL_PATTERN = Pattern.compile("\\b[a-z]+([A-Z][a-z]+)+\\b");

    /**
     * Извлекает технические токены из запроса.
     */
    public Set<String> extractTechnicalTokens(String query) {
        Set<String> tokens = new HashSet<>();
        tokens.addAll(findMatches(PATH_PATTERN, query));
        tokens.addAll(findMatches(FILE_PATTERN, query));
        tokens.addAll(findMatches(CONSTANT_PATTERN, query));
        tokens.addAll(findMatches(CAMEL_PATTERN, query));
        // Можно добавить и другие паттерны (например, для endpoint'ов)
        return tokens;
    }

    private List<String> findMatches(Pattern pattern, String text) {
        return pattern.matcher(text).results()
                .map(m -> m.group(1) != null ? m.group(1) : m.group())
                .collect(Collectors.toList());
    }

    /**
     * Проверяет, содержит ли текст чанка хотя бы один из токенов.
     */
    public boolean containsAnyToken(String chunkText, Set<String> tokens) {
        if (tokens.isEmpty()) return false;
        String lowerChunk = chunkText.toLowerCase();
        return tokens.stream().anyMatch(tok -> 
            lowerChunk.contains(tok.toLowerCase())
        );
    }

    /**
     * Модифицирует список документов: помечает точные совпадения и поднимает их в начало.
     * Возвращает новый список, где точные совпадения имеют score=1.0 и идут первыми.
     */
    public List<Document> boostExactMatches(List<Document> documents, String query) {
        Set<String> tokens = extractTechnicalTokens(query);
        if (tokens.isEmpty()) {
            return documents; // нет токенов – ничего не делаем
        }

        List<Document> exactMatches = new ArrayList<>();
        List<Document> others = new ArrayList<>();

        for (Document doc : documents) {
            String content = doc.getContent();
            if (containsAnyToken(content, tokens)) {
                // Создаём копию документа с обновлённой метаданными score=1.0
                Map<String, Object> newMeta = new HashMap<>(doc.getMetadata());
                newMeta.put("score", 1.0);
                newMeta.put("exact_match", true);
                Document boosted = new Document(doc.getId(), content, newMeta);
                exactMatches.add(boosted);
            } else {
                others.add(doc);
            }
        }

        // Если есть точные совпадения, возвращаем их первыми, затем остальные
        if (!exactMatches.isEmpty()) {
            exactMatches.addAll(others);
            return exactMatches;
        } else {
            // Если токены были, но ни один чанк их не содержит – возвращаем пустой список (отказ)
            return Collections.emptyList();
        }
    }
}