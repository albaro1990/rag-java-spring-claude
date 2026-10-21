package com.albaro.rag.demo.service;

import com.albaro.rag.demo.dto.RerankOutcome;
import com.albaro.rag.demo.dto.RetrievalOutcome;
import com.albaro.rag.demo.dto.RetrievalResult;
import com.albaro.rag.demo.llm.LlmResult;
import com.albaro.rag.demo.llm.LlmUsage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
public class RetrievalService {

    private final DocumentChunkService documentChunkService;
    private final QueryRewriter queryRewriter;
    private final LlmReranker reranker;

    private final boolean rewritingEnabled;
    private final double retrievalThreshold;
    private final int retrievalLimit;

    private final boolean rerankEnabled;
    private final double candidateThreshold;
    private final int candidateLimit;
    private final int rerankTopN;

    public RetrievalService(
            DocumentChunkService documentChunkService,
            QueryRewriter queryRewriter,
            LlmReranker reranker,
            @Value("${rag.query-rewriting.enabled:false}") boolean rewritingEnabled,
            @Value("${rag.retrieval.threshold}") double retrievalThreshold,
            @Value("${rag.retrieval.limit}") int retrievalLimit,
            @Value("${rag.rerank.enabled:false}") boolean rerankEnabled,
            @Value("${rag.rerank.candidate-threshold:0.45}") double candidateThreshold,
            @Value("${rag.rerank.candidates:15}") int candidateLimit,
            @Value("${rag.rerank.top-n:5}") int rerankTopN) {

        this.documentChunkService = documentChunkService;
        this.queryRewriter = queryRewriter;
        this.reranker = reranker;
        this.rewritingEnabled = rewritingEnabled;
        this.retrievalThreshold = retrievalThreshold;
        this.retrievalLimit = retrievalLimit;
        this.rerankEnabled = rerankEnabled;
        this.candidateThreshold = candidateThreshold;
        this.candidateLimit = candidateLimit;
        this.rerankTopN = rerankTopN;
    }

    public RetrievalOutcome retrieve(String question) {

        if (!rerankEnabled) {
            return search(question, retrievalThreshold, retrievalLimit);
        }

        // Etapa 1: búsqueda amplia (más candidatos, umbral más flexible).
        RetrievalOutcome candidates =
                search(question, candidateThreshold, candidateLimit);

        // Etapa 2: el reranker lee cada candidato junto con la pregunta
        // y se queda solo con los que de verdad ayudan a responder.
        RerankOutcome reranked =
                reranker.rerank(question, candidates.results(), rerankTopN);

        // El consumo total es la suma de las dos etapas.
        return new RetrievalOutcome(
                reranked.selected(),
                candidates.usage().plus(reranked.usage())
        );
    }

    private RetrievalOutcome search(String question, double threshold, int limit) {

        List<RetrievalResult> original =
                documentChunkService.findSimilar(question, threshold, limit);

        if (!rewritingEnabled) {
            return new RetrievalOutcome(original, LlmUsage.ZERO);
        }

        // Multi-query: buscamos con la pregunta original Y con la reescrita.
        LlmResult rewritten = queryRewriter.rewrite(question);

        List<RetrievalResult> fromRewritten =
                documentChunkService.findSimilar(rewritten.text(), threshold, limit);

        // Unimos ambas listas: si un chunk aparece dos veces,
        // nos quedamos con su menor distancia.
        Map<Long, RetrievalResult> best = new HashMap<>();

        Stream.concat(original.stream(), fromRewritten.stream())
                .forEach(result -> best.merge(
                        result.id(),
                        result,
                        (a, b) -> a.distance() <= b.distance() ? a : b));

        List<RetrievalResult> merged = best.values().stream()
                .sorted(Comparator.comparingDouble(RetrievalResult::distance))
                .limit(limit)
                .toList();

        return new RetrievalOutcome(merged, LlmUsage.of(rewritten));
    }
}