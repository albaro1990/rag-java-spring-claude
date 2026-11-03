package com.albaro.rag.demo.service;

import com.albaro.rag.demo.entity.ChatMessage;
import com.albaro.rag.demo.llm.LlmClient;
import com.albaro.rag.demo.llm.LlmResult;
import com.albaro.rag.demo.llm.TokenMeter;
import com.albaro.rag.demo.prompt.PromptTemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

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

    // Versión sin historial: la usa el multi-query del RetrievalService.
    public LlmResult rewrite(String question) {
        return rewrite(question, null, List.of());
    }

    // Devuelve la pregunta reescrita (ya validada) y los tokens que costó.
    public LlmResult rewrite(
            String question,
            String summary,
            List<ChatMessage> history
    ) {

        long start = System.nanoTime();

        String summaryText = summary == null || summary.isBlank()
                ? "Sin resumen previo."
                : summary;


        String historyText = buildHistory(history);
        String safeQuestion = escapeDelimiters(question);

        String userPrompt = """
        <resumen_conversacion>
        %s
        </resumen_conversacion>

        <historial_reciente>
        %s
        </historial_reciente>

        <pregunta_actual>
        %s
        </pregunta_actual>
        """.formatted(
                escapeDelimiters(summaryText),
                historyText,
                escapeDelimiters(question)
        );

        LlmResult result = llmClient.generate(
                promptTemplateService.loadRewriterSystemPrompt(),
                userPrompt
        );

        long latencyMs = (System.nanoTime() - start) / 1_000_000;
        tokenMeter.record("rewrite", result, latencyMs);

        String rewritten = sanitize(result.text(), question);

        log.debug("Pregunta reescrita: '{}' -> '{}'", question, rewritten);

        return new LlmResult(
                rewritten,
                result.inputTokens(),
                result.outputTokens(),
                result.model()
        );
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

    private String buildHistory(List<ChatMessage> history) {

        if (history == null || history.isEmpty()) {
            return "Sin historial previo.";
        }

        return history.stream()
                .map(message -> message.getRole()
                        + ": "
                        + escapeDelimiters(message.getContent()))
                .collect(Collectors.joining("\n"));
    }

    // Evita que un mensaje cierre antes de tiempo los bloques <historial> o
    // <pregunta_actual> (el texto del cliente es un dato, no una instrucción).
    private String escapeDelimiters(String text) {
        return text.replaceAll(
                "(?i)</(resumen_conversacion|historial_reciente|pregunta_actual)>",
                "< /$1>"
        );
    }
}