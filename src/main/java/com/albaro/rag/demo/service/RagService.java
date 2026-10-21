package com.albaro.rag.demo.service;


import com.albaro.rag.demo.dto.*;
import com.albaro.rag.demo.llm.LlmClient;
import com.albaro.rag.demo.llm.LlmResult;
import com.albaro.rag.demo.llm.LlmUsage;
import com.albaro.rag.demo.prompt.PromptTemplateService;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;

@Slf4j
@Service
public class RagService {

    private final LlmClient llmClient;
    private final RetrievalService retrievalService;
    private final PromptTemplateService promptTemplateService;

    private static final String NO_INFO =
            "No se encontró información relevante para responder la pregunta.";

    public RagService(
            LlmClient  llmClient,
            RetrievalService retrievalService,
            PromptTemplateService promptTemplateService
    ) {

        this.llmClient = llmClient;
        this.retrievalService = retrievalService;
        this.promptTemplateService = promptTemplateService;
    }

    public RagResponse generate(String question) {

        long retrievalStart = System.nanoTime();

        RetrievalOutcome retrieval =
                retrievalService.retrieve(question);

        List<RetrievalResult> results = retrieval.results();
        LlmUsage retrievalUsage = retrieval.usage();

        long retrievalMs = (System.nanoTime() - retrievalStart) / 1_000_000;

        if (results.isEmpty()) {
            return new RagResponse(
                    "No se encontró información relevante para responder la pregunta.",
                    List.of(),
                    new RagMetrics(retrievalMs, 0, 0, 0,
                            retrievalUsage.inputTokens(), retrievalUsage.outputTokens()),
                    ""
            );
        }
        String contexto = buildContext(results);


        log.debug("Contexto enviado al LLM:\n{}", contexto);

        String systemPrompt =
                promptTemplateService.loadSystemPrompt();

        String userPrompt =
                promptTemplateService.buildUserPrompt(
                        contexto,
                        question
                );

        long llmStart = System.nanoTime();

        LlmResult llmResult =
                llmClient.generate(systemPrompt, userPrompt);


        long llmMs = (System.nanoTime() - llmStart) / 1_000_000;

        RagMetrics metrics = new RagMetrics(
                retrievalMs,
                llmMs,
                llmResult.inputTokens(),
                llmResult.outputTokens(),
                retrievalUsage.inputTokens(),
                retrievalUsage.outputTokens()
        );

        log.info("Pregunta respondida: retrieval {} ms, LLM {} ms, tokens totales {} + {}",
                retrievalMs, llmMs, metrics.totalInputTokens(), metrics.totalOutputTokens());

        List<RagSource> sources = results.stream()
                .map(result -> new RagSource(
                        result.source(),
                        result.chunkIndex(),
                        result.distance()
                ))
                .toList();

        return new RagResponse(llmResult.text(), sources, metrics, contexto);
    }


    // Igual que generate, pero entrega la respuesta en pedazos a medida que
    // el LLM la va escribiendo. El retrieval no cambia: el streaming solo
    // aplica a la etapa de generación.
    public void stream(String question, Consumer<String> onText) {

        List<RetrievalResult> results =
                retrievalService.retrieve(question).results();

        if (results.isEmpty()) {
            onText.accept(NO_INFO);
            return;
        }

        String systemPrompt = promptTemplateService.loadSystemPrompt();
        String userPrompt = promptTemplateService.buildUserPrompt(
                buildContext(results),
                question
        );

        llmClient.stream(systemPrompt, userPrompt, onText);
    }

    @NotNull
    private static String buildContext(List<RetrievalResult> results) {
        String contexto = results.stream()
                .map(result -> """
                [FUENTE: %s | CHUNK: %d]
                %s
                [FIN DEL FRAGMENTO]
                """.formatted(
                        result.source(),
                        result.chunkIndex(),
                        result.content()
                ))
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse("");
        return contexto;
    }
}