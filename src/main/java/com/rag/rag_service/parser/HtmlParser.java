package com.rag.rag_service.parser;

import de.l3s.boilerpipe.BoilerpipeExtractor;
import de.l3s.boilerpipe.extractors.ArticleExtractor;
import de.l3s.boilerpipe.BoilerpipeProcessingException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
public class HtmlParser implements DocumentParser {

    private final BoilerpipeExtractor extractor = ArticleExtractor.INSTANCE;

    @Override
    public String parse(InputStream inputStream) throws IOException {
        String html = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

        Document jsoupDoc = Jsoup.parse(html);
        jsoupDoc.select("script, style").remove();
        String cleanedHtml = jsoupDoc.body().html();

        try {
            return extractor.getText(cleanedHtml);
        } catch (BoilerpipeProcessingException e) {
            throw new IOException("Failed to extract text from HTML", e);
        }
    }
}