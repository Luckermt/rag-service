package com.rag.rag_service.exception;

public class QuotaExceededException extends RuntimeException {
    private final String resetTime;

    public QuotaExceededException(String message, String resetTime) {
        super(message);
        this.resetTime = resetTime;
    }

    public String getResetTime() {
        return resetTime;
    }
}