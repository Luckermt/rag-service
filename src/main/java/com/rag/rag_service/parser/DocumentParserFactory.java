package com.rag.rag_service.parser;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DocumentParserFactory {
    private final Map<String, DocumentParser> parserMap;

    public DocumentParserFactory(TxtParser txt, MarkdownParser md, PdfParser pdf, DocxParser docx) {
        parserMap = Map.of(
            "txt", txt,
            "md", md,
            "pdf", pdf,
            "docx", docx
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