package com.rag.rag_service.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

@Component
public class ExactTermGuard {

    private static final Pattern PATH_PATTERN = Pattern.compile("(/[\\w\\-./]+)");
    private static final Pattern FILE_PATTERN = Pattern.compile("\\b([\\w\\-]+\\.\\w{2,4})\\b");
    private static final Pattern CONSTANT_PATTERN = Pattern.compile("\\b[A-Z_]{2,}\\b");
    private static final Pattern CAMEL_PATTERN = Pattern.compile("\\b[a-z]+([A-Z][a-z]+)+\\b");

    public Set<String> extractTechnicalTokens(String query) {
        Set<String> tokens = new HashSet<>();
        tokens.addAll(findMatches(PATH_PATTERN, query));
        tokens.addAll(findMatches(FILE_PATTERN, query));
        tokens.addAll(findMatches(CONSTANT_PATTERN, query));
        tokens.addAll(findMatches(CAMEL_PATTERN, query));
        return tokens;
    }

    private List<String> findMatches(Pattern pattern, String text) {
        return pattern.matcher(text).results()
                .map(m -> m.group(1) != null ? m.group(1) : m.group())
                .collect(Collectors.toList());
    }

    public boolean containsAnyToken(String chunkText, Set<String> tokens) {
        if (tokens.isEmpty()) return false;
        String lowerChunk = chunkText.toLowerCase();
        return tokens.stream().anyMatch(tok -> 
            lowerChunk.contains(tok.toLowerCase())
        );
    }

    public List<Document> boostExactMatches(List<Document> documents, String query) {
    Set<String> tokens = extractTechnicalTokens(query);
    if (tokens.isEmpty()) {
        return documents;
    }

    List<Document> exactMatches = new ArrayList<>();
    List<Document> others = new ArrayList<>();

    for (Document doc : documents) {
        String content = doc.getContent();
        if (content == null) {
            continue;
        }
        if (containsAnyToken(content, tokens)) {
            Map<String, Object> newMeta = new HashMap<>(doc.getMetadata());
            newMeta.put("score", 1.0);
            newMeta.put("exact_match", true);
            exactMatches.add(new Document(doc.getId(), content, newMeta));
        } else {
            others.add(doc);
        }
    }

    if (!exactMatches.isEmpty()) {
        exactMatches.addAll(others);
        return exactMatches;
    }
    return documents;
}
}