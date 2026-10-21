package com.albaro.rag.demo.dto;

public record RetrievalResult(
        Long id,
        String content,
        String source,
        Integer chunkIndex,
        double distance
) {}