package com.albaro.rag.demo.dto;

public record ChatResponse(
        String sessionId,
        String condensedQuestion,
        RagResponse ragResponse
) {
}