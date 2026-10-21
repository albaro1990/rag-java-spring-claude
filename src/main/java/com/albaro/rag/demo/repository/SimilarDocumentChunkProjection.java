package com.albaro.rag.demo.repository;

public interface SimilarDocumentChunkProjection {

    Long getId();

    String getContent();

    String getSource();

    Integer getChunkIndex();

    Double getDistance();
}
