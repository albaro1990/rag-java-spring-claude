package com.albaro.rag.demo.eval;

import com.albaro.rag.demo.prompt.PromptTemplateRenderer;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class ClaudeJudge {

    private static final String SYSTEM_PROMPT_PATH = "prompts/judge-system.txt";
    private static final String USER_PROMPT_PATH = "prompts/judge-user.txt";

    private final AnthropicClient client;
    private final String model;
    private final PromptTemplateRenderer renderer = new PromptTemplateRenderer();

    // Los prompts se leen una sola vez, al crear el juez.
    private final String systemPrompt;
    private final String userTemplate;

    public ClaudeJudge(String apiKey, String model) {
        this.client = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .build();
        this.model = model;
        this.systemPrompt = readFromClasspath(SYSTEM_PROMPT_PATH);
        this.userTemplate = readFromClasspath(USER_PROMPT_PATH);
    }

    public JudgeVerdict evaluate(
            String question,
            String context,
            String expectedAnswer,
            String answer) {

        String userPrompt = renderer.render(
                userTemplate,
                Map.of(
                        "question", question,
                        "context", context,
                        "expectedAnswer", expectedAnswer,
                        "answer", answer
                )
        );

        StructuredMessageCreateParams<JudgeVerdict> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(16000L)
                .system(systemPrompt)
                .addUserMessage(userPrompt)
                // Reintento automático en otro modelo si un filtro rechaza por error.
                .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                // Obliga a Claude a responder con el formato de JudgeVerdict.
                .outputConfig(JudgeVerdict.class)
                .build();

        StructuredMessage<JudgeVerdict> response = client.messages().create(params);

        boolean refused = response.stopReason()
                .map(reason -> reason.equals(StopReason.REFUSAL))
                .orElse(false);

        if (refused) {
            throw new IllegalStateException("El juez rechazó la solicitud");
        }

        return response.content().stream()
                .flatMap(block -> block.text().stream())
                .findFirst()
                .map(typed -> typed.text())
                .orElseThrow(() -> new IllegalStateException("El juez no devolvió un veredicto"));
    }

    private static String readFromClasspath(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo cargar el prompt del juez: " + path, e);
        }
    }
}
