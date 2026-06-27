package com.rag.rag_service.parser;

import java.io.IOException;
import java.io.InputStream;

public interface DocumentParser {
    String parse(InputStream inputStream) throws IOException;
}