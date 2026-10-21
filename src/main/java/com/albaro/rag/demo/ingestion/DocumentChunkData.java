package com.albaro.rag.demo.ingestion;

public record DocumentChunkData(
        String content,
        String source,
        int chunkIndex
) {
}