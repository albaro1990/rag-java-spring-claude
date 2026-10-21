package com.albaro.rag.demo.llm;

// Tokens consumidos por una o varias llamadas a un LLM.
// Permite sumar el consumo de todas las etapas de una pregunta.
public record LlmUsage(long inputTokens, long outputTokens) {

    public static final LlmUsage ZERO = new LlmUsage(0, 0);

    public static LlmUsage of(LlmResult result) {
        return new LlmUsage(result.inputTokens(), result.outputTokens());
    }

    public LlmUsage plus(LlmUsage other) {
        return new LlmUsage(
                inputTokens + other.inputTokens,
                outputTokens + other.outputTokens
        );
    }
}