package com.albaro.rag.demo.dto;

public record DocumentChunkResponse(
        Long id,
        String content,
        String source,
        Integer chunkIndex
) {}