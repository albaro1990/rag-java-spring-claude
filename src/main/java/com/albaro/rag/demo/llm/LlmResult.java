package com.albaro.rag.demo.llm;

// Lo que devuelve un LLM: el texto y cuántos tokens consumió.
// Los tokens son lo que se cobra en una API comercial.
public record LlmResult(
        String text,
        long inputTokens,
        long outputTokens,
        String model
) {
}