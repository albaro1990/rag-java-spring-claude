package com.albaro.rag.demo.dto;

import com.albaro.rag.demo.llm.LlmUsage;

import java.util.List;

// Resultado del retrieval: los chunks elegidos y cuántos tokens
// gastaron los LLM auxiliares (reescritura y reranker) para elegirlos.
public record RetrievalOutcome(
        List<RetrievalResult> results,
        LlmUsage usage
) {
}