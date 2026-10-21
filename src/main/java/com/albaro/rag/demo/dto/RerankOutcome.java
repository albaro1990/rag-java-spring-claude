package com.albaro.rag.demo.dto;

import com.albaro.rag.demo.llm.LlmUsage;

import java.util.List;

// Lo que devuelve el reranker: los chunks seleccionados y su consumo.
public record RerankOutcome(
        List<RetrievalResult> selected,
        LlmUsage usage
) {
}