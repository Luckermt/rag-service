package com.rag.rag_service.util;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.rag.rag_service.model.openai.QueryIntent;

@Component
public class QueryClassifier {

    private static final Pattern GREETING_PATTERN = Pattern.compile(
            "(?i)(привет|здравствуй|доброе утро|добрый день|добрый вечер|спасибо|благодарю|пока|до свидания|как дела|как ты|что нового)");
    private static final Pattern QUESTION_PATTERN = Pattern.compile(
            "(?i)(кто|что|где|когда|почему|зачем|какой|какая|какое|какие|сколько|куда|откуда|чей|чья|чье|чьи)\\s+");
    private static final Pattern CURRENT_PATTERN = Pattern.compile(
            "(?i)(сегодня|сейчас|новости|последние|недавно|текущий|этой неделе|этом месяце)");

    public QueryIntent classify(String query) {
        if (query == null || query.isBlank()) {
            return QueryIntent.UNKNOWN;
        }

        String normalized = query.trim().toLowerCase();

        if (GREETING_PATTERN.matcher(normalized).find() && normalized.length() < 30) {
            return QueryIntent.CHIT_CHAT;
        }

        if (CURRENT_PATTERN.matcher(normalized).find()) {
            return QueryIntent.CURRENT;
        }

        if (QUESTION_PATTERN.matcher(normalized).find()) {
            return QueryIntent.FACTUAL;
        }

        if (normalized.length() > 20) {
            return QueryIntent.FACTUAL;
        }

        return QueryIntent.CHIT_CHAT;
    }
}