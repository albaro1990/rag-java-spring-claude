package com.albaro.rag.demo.service;

import com.albaro.rag.demo.llm.LlmClient;
import com.albaro.rag.demo.llm.LlmResult;
import com.albaro.rag.demo.llm.TokenMeter;
import com.albaro.rag.demo.prompt.PromptTemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class QueryRewriter {

    // Una pregunta reescrita más larga que esto probablemente trae texto extra.
    private static final int MAX_LENGTH = 300;

    private final LlmClient llmClient;
    private final PromptTemplateService promptTemplateService;
    private final TokenMeter tokenMeter;

    public QueryRewriter(LlmClient llmClient, PromptTemplateService promptTemplateService,
                         TokenMeter tokenMeter) {
        this.llmClient = llmClient;
        this.promptTemplateService = promptTemplateService;
        this.tokenMeter = tokenMeter;
    }

    // Devuelve la pregunta reescrita (ya validada) y los tokens que costó.
    public LlmResult rewrite(String question) {

        long start = System.nanoTime();
        LlmResult result = llmClient.generate(
                promptTemplateService.loadRewriterSystemPrompt(),
                question
        );

        long latencyMs = (System.nanoTime() - start) / 1_000_000;
        tokenMeter.record("rewrite", result,  latencyMs);

        String rewritten = sanitize(result.text(), question);

        log.debug("Pregunta reescrita: '{}' -> '{}'", question, rewritten);;

        return new LlmResult(rewritten, result.inputTokens(), result.outputTokens(), result.model());
    }

    // Validación de la salida: el LLM no siempre respeta "una sola línea".
    private String sanitize(String text, String original) {

        String firstLine = text.strip().lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .findFirst()
                .orElse("");

        if (firstLine.isEmpty() || firstLine.length() > MAX_LENGTH) {
            log.warn("Reescritura descartada; se usa la pregunta original");
            return original;
        }

        return firstLine;
    }
}