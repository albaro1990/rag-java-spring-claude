package com.albaro.rag.demo.prompt;

import com.albaro.rag.demo.config.PromptProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PromptTemplateService {

    private final PromptProperties promptProperties;
    private final PromptTemplateRenderer renderer;

    // Cada prompt se lee del disco una sola vez y se guarda en memoria.
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public PromptTemplateService(PromptProperties promptProperties, PromptTemplateRenderer renderer) {
        this.promptProperties = promptProperties;
        this.renderer = renderer;
    }

    public String loadSystemPrompt() {
        return loadPrompt(promptProperties.getSystemPath());
    }

    public String buildUserPrompt(String context, String question) {

        String template =
                loadPrompt(promptProperties.getUserPath());

        return renderer.render(
                template,
                Map.of(
                        "context", context,
                        "question", question
                )
        );
    }

    public String loadRewriterSystemPrompt() {
        return loadPrompt(promptProperties.getRewriterSystemPath());
    }

    public String loadRerankerSystemPrompt() {
        return loadPrompt(promptProperties.getRerankerSystemPath());
    }

    public String buildRerankerUserPrompt(String question, String fragments) {

        String template =
                loadPrompt(promptProperties.getRerankerUserPath());

        return renderer.render(
                template,
                Map.of(
                        "question", question,
                        "fragments", fragments
                )
        );
    }

    // Si el prompt ya está en memoria lo devuelve; si no, lo lee del disco y lo guarda.
    private String loadPrompt(String path) {
        return cache.computeIfAbsent(path, this::readFromClasspath);
    }

    private String readFromClasspath(String path) {

        try {
            ClassPathResource resource =
                    new ClassPathResource(path);

            return resource.getContentAsString(
                    StandardCharsets.UTF_8
            );

        } catch (IOException e) {
            throw new IllegalStateException(
                    "No se pudo cargar el prompt: " + path,
                    e
            );
        }
    }
}