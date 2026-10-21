package com.albaro.rag.demo.repository;

import com.albaro.rag.demo.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {


    @Query(value = """
            SELECT
                id,
                content,
                source,
                chunk_index AS chunkIndex,
                embedding <=> CAST(:embedding AS vector) AS distance
            FROM document_chunks
            WHERE embedding <=> CAST(:embedding AS vector) <= :threshold
            ORDER BY distance
            LIMIT :limit
            """, nativeQuery = true)
    List<SimilarDocumentChunkProjection> findSimilar(
            @Param("embedding") String embedding,
            @Param("threshold") double threshold,
            @Param("limit") int limit
    );

    @Modifying
    @Query("DELETE FROM DocumentChunk c WHERE c.source = :source")
    int deleteBySource(@Param("source") String source);

    @Query("SELECT DISTINCT c.source FROM DocumentChunk c")
    List<String> findAllSources();
}
