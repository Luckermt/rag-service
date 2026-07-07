package com.rag.rag_service.util;

import java.util.ArrayList;
import java.util.List;

public class ChunkUtil {

    public static List<String> chunk(String text, int chunkSize, int overlap) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        String normalized = text
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[ \t]+", " ");

        List<String> chunks = new ArrayList<>();
        int length = normalized.length();
        int start = 0;

        while (start < length) {
            int end = Math.min(start + chunkSize, length);

            if (end == length) {
                chunks.add(normalized.substring(start));
                break;
            }

            int split = findBestSplit(normalized, start, end);

            if (split <= start) {
                split = end;
            }

            chunks.add(normalized.substring(start, split));

            if (overlap > 0) {
                start = Math.max(split - overlap, split);
            } else {
                start = split;
            }

            if (start >= split && start < length) {
                start = split + 1;
            }
        }
        return chunks;
    }

    private static int findBestSplit(String text, int start, int end) {
        int idx = text.lastIndexOf("\n\n", end - 1);
        if (idx >= start) {
            int split = idx + 2;
            if (split <= end && split > start) {
                return split;
            }
        }

        idx = text.lastIndexOf('\n', end - 1);
        if (idx >= start) {
            int split = idx + 1;
            if (split <= end && split > start) {
                return split;
            }
        }

        for (int i = end - 1; i >= start; i--) {
            char c = text.charAt(i);
            if (c == '.' || c == '!' || c == '?') {
                int j = i + 1;
                while (j < text.length() && Character.isWhitespace(text.charAt(j))) {
                    j++;
                }
                if (j <= end) {
                    return j;
                } else {
                    if (i + 1 <= end) {
                        return i + 1;
                    }
                }
            }
        }

        for (int i = end - 1; i >= start; i--) {
            if (Character.isWhitespace(text.charAt(i))) {
                int split = i + 1; // after the whitespace
                if (split <= end && split > start) {
                    return split;
                }
            }
        }

        return end;
    }
}