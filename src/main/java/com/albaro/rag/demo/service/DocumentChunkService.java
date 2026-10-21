package com.albaro.rag.demo.service;

import com.albaro.rag.demo.dto.RetrievalResult;
import com.albaro.rag.demo.entity.DocumentChunk;
import com.albaro.rag.demo.ingestion.DocumentChunkData;
import com.albaro.rag.demo.repository.DocumentChunkRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@Slf4j
public class DocumentChunkService {

    private final DocumentChunkRepository repository;
    private final EmbeddingService embeddingService;
    private final double retrievalThreshold;
    private final int retrievalLimit;

    public DocumentChunkService(
            DocumentChunkRepository repository,
            EmbeddingService embeddingService,
            @Value("${rag.retrieval.threshold}") double retrievalThreshold,
            @Value("${rag.retrieval.limit}") int retrievalLimit) {

        this.repository = repository;
        this.embeddingService = embeddingService;
        this.retrievalThreshold = retrievalThreshold;
        this.retrievalLimit = retrievalLimit;
    }

    // Convierte un chunk en entidad calculando su embedding.
    // No toca la base de datos.
    public DocumentChunk toEntity(DocumentChunkData chunkData) {

        float[] embedding =
                embeddingService.generateEmbedding(chunkData.content());

        DocumentChunk documentChunk = new DocumentChunk();

        documentChunk.setContent(chunkData.content());
        documentChunk.setEmbedding(embedding);
        documentChunk.setSource(chunkData.source());
        documentChunk.setChunkIndex(chunkData.chunkIndex());

        return documentChunk;
    }

    // Reemplaza todos los chunks de un documento en una sola transacción:
    // o se borran los viejos y se guardan los nuevos, o no cambia nada.
    @Transactional
    public List<DocumentChunk> replaceDocument(
            String source,
            List<DocumentChunk> chunks) {

        repository.deleteBySource(source);

        return repository.saveAll(chunks);
    }

    public List<RetrievalResult> findSimilar(String question) {
        return findSimilar(question, retrievalThreshold, retrievalLimit);
    }

    public List<RetrievalResult> findSimilar(String question, double threshold, int limit) {

        float[] embedding =
                embeddingService.generateEmbedding(question);

        String vector = convertToVectorString(embedding);

        return repository.findSimilar(
                        vector,
                        threshold,
                        limit
                )
                .stream()
                .map(result -> new RetrievalResult(
                        result.getId(),
                        result.getContent(),
                        result.getSource(),
                        result.getChunkIndex(),
                        result.getDistance()
                ))
                .toList();
    }

    private String convertToVectorString(float[] embedding) {
        return java.util.Arrays.toString(embedding);
    }

    // Borra los chunks de los documentos que ya no están en la carpeta.
// Sin esto, un documento eliminado seguiría apareciendo en las respuestas.
    @Transactional
    public List<String> removeMissingDocuments(Set<String> currentSources) {

        List<String> missing = repository.findAllSources().stream()
                .filter(source -> !currentSources.contains(source))
                .toList();

        for (String source : missing) {
            int deleted = repository.deleteBySource(source);
            log.info("Documento eliminado de la base: {} ({} chunks)", source, deleted);
        }

        return missing;
    }
}