package com.albaro.rag.demo.dto;

// Cuánto tardó cada etapa y cuántos tokens se usaron en una pregunta.
// inputTokens/outputTokens = generación de la respuesta.
// retrievalInputTokens/retrievalOutputTokens = reescritura + reranker.
public record RagMetrics(
        long retrievalMs,
        long llmMs,
        long inputTokens,
        long outputTokens,
        long retrievalInputTokens,
        long retrievalOutputTokens
) {

    public long totalInputTokens() {
        return inputTokens + retrievalInputTokens;
    }

    public long totalOutputTokens() {
        return outputTokens + retrievalOutputTokens;
    }
}