package com.rag.rag_service.util;

import java.util.ArrayList;
import java.util.List;

public class ChunkUtil {
    public static List<String> chunk(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) return chunks;

        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end));
            start += (chunkSize - overlap);
            if (start >= text.length()) break;
        }
        return chunks;
    }
}