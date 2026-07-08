package com.rag.rag_service.parser;

import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class DocumentParserFactory {
    private final Map<String, DocumentParser> parserMap;

    public DocumentParserFactory(TxtParser txt, MarkdownParser md, PdfParser pdf, DocxParser docx, HtmlParser html) {
        parserMap = Map.of(
            "txt", txt,
            "md", md,
            "pdf", pdf,
            "docx", docx,
            "html", html,
            "htm", html
        );
    }

    public DocumentParser getParser(String fileName) {
        String ext = getExtension(fileName).toLowerCase();
        DocumentParser parser = parserMap.get(ext);
        if (parser == null) {
            throw new IllegalArgumentException("Unsupported file extension: " + ext);
        }
        return parser;
    }

    private String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return (dot == -1) ? "" : fileName.substring(dot + 1);
    }
}