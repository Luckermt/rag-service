package com.rag.rag_service.parser;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.text.TextContentRenderer;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
public class MarkdownParser implements DocumentParser {
    private final Parser parser = Parser.builder().build();
    private final TextContentRenderer renderer = TextContentRenderer.builder().build();

    @Override
    public String parse(InputStream inputStream) throws IOException {
        String md = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        var document = parser.parse(md);
        return renderer.render(document);
    }
}