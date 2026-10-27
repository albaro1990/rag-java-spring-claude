package com.albaro.rag.demo.intent;

import com.albaro.rag.demo.llm.LlmClient;
import com.albaro.rag.demo.llm.LlmResult;
import com.albaro.rag.demo.llm.TokenMeter;
import com.albaro.rag.demo.prompt.PromptTemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class IntentClassifier {

    private final LlmClient llmClient;
    private final PromptTemplateService promptTemplateService;
    private final TokenMeter tokenMeter;
    private final JsonMapper jsonMapper;
    private final AtomicLong parseFailureCount = new AtomicLong();

    public IntentClassifier(
            LlmClient llmClient,
            PromptTemplateService promptTemplateService,
            TokenMeter tokenMeter) {
        this.llmClient = llmClient;
        this.promptTemplateService = promptTemplateService;
        this.tokenMeter = tokenMeter;
        this.jsonMapper = new JsonMapper();
    }


    public IntentCategory classify(String message) {
        if (message == null || message.isBlank()) {
            log.warn("Mensaje vacío recibido por IntentClassifier; se usa fuera_de_tema");
            return IntentCategory.fuera_de_tema;
        }

        String userPrompt = "Clasifica el siguiente mensaje del cliente.\n"
                + "<mensaje_cliente>\n"
                + escapeClosingDelimiter(message.strip())
                + "\n</mensaje_cliente>";

        long startNanos = System.nanoTime();
        LlmResult result = llmClient.generate(
                promptTemplateService.loadIntentClassifierSystemPrompt(),
                userPrompt);
        long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;


        tokenMeter.record("classify", result, latencyMs);

        try {
            IntentClassifierOutput output = jsonMapper.readValue(
                    removeOptionalMarkdownFence(result.text()),
                    IntentClassifierOutput.class);

            IntentCategory category = IntentCategory.fromModel(output.categoria());
            log.debug("Intento clasificado como {}. Justificación: {}", category, output.razonamiento());
            return category;
        } catch (Exception exception) {

            long failures = parseFailureCount.incrementAndGet();
            log.warn("Respuesta inválida del clasificador (fallo de parseo #{}); "
                            + "se usa fallback fuera_de_tema. Motivo: {}",
                    failures, exception.getMessage());
            return IntentCategory.fuera_de_tema;
        }
    }


    public long getParseFailureCount() {
        return parseFailureCount.get();
    }

    private String removeOptionalMarkdownFence(String text) {
        if (text == null) {
            throw new IllegalArgumentException("El modelo devolvió texto null");
        }
        String cleaned = text.strip();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```(?:json)?\\s*", "");
            cleaned = cleaned.replaceFirst("\\s*```$", "");
        }
        return cleaned.strip();
    }

    private String escapeClosingDelimiter(String message) {

        return message.replaceAll("(?i)</mensaje_cliente>", "< /mensaje_cliente>");
    }
}
