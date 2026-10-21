package com.albaro.rag.demo.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.beta.messages.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "rag.llm.provider", havingValue = "claude")
public class ClaudeLlmClient implements LlmClient {

    private final AnthropicClient client;
    private final String model;

    public ClaudeLlmClient(
            @Value("${anthropic.api-key}") String apiKey,
            @Value("${anthropic.model}") String model,
            @Value("${anthropic.timeout:30s}") Duration timeout,
            @Value("${anthropic.max-retries:3}") int maxRetries) {

        this.client = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(timeout)
                .maxRetries(maxRetries)
                .build();
        this.model = model;
    }

    @Override
    public LlmResult  generate(String systemPrompt, String userPrompt) {

        MessageCreateParams params = buildParams(systemPrompt, userPrompt);

        BetaMessage response = client.beta().messages().create(params);

        // Revisar el motivo de parada antes de leer el contenido.
        boolean refused = response.stopReason()
                .map(reason -> reason.equals(BetaStopReason.REFUSAL))
                .orElse(false);

        if (refused) {
            throw new IllegalStateException(
                    "Claude rechazó la solicitud: " + response.stopDetails());
        }

        String text = response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(BetaTextBlock::text)
                .collect(Collectors.joining());

        // La API informa cuántos tokens consumió esta llamada.
        return new LlmResult(
                text,
                response.usage().inputTokens(),
                response.usage().outputTokens()
        );
    }

    @Override
    public void stream(String systemPrompt, String userPrompt, Consumer<String> onText) {

        MessageCreateParams params = buildParams(systemPrompt, userPrompt);

        // La API envía eventos a medida que genera; tomamos solo los pedazos de texto.
        try (StreamResponse<BetaRawMessageStreamEvent> stream =
                     client.beta().messages().createStreaming(params)) {

            stream.stream()
                    .flatMap(event -> event.contentBlockDelta().stream())
                    .flatMap(delta -> delta.delta().text().stream())
                    .forEach(textDelta -> onText.accept(textDelta.text()));
        }
    }

    private MessageCreateParams buildParams(String systemPrompt, String userPrompt) {
        return MessageCreateParams.builder()
                .model(model)
                .maxTokens(16000L)
                .system(systemPrompt)
                .addUserMessage(userPrompt)
                // Si un filtro de seguridad rechaza la solicitud por error,
                // Anthropic la reintenta en otro modelo automáticamente.
                .addBeta("server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .build();
    }
}