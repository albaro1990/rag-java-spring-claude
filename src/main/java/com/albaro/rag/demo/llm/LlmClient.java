package com.albaro.rag.demo.llm;

import java.util.function.Consumer;

// Contrato común para cualquier LLM que genere respuestas.
// RagService depende de esta interfaz, no de Ollama ni de Claude.
public interface LlmClient {

    LlmResult  generate(String systemPrompt, String userPrompt);

    // Streaming: entrega la respuesta en pedazos a medida que se genera.
    // Implementación por defecto para proveedores sin streaming:
    // genera completo y entrega todo de una vez.
    default void stream(String systemPrompt, String userPrompt, Consumer<String> onText) {
        onText.accept(generate(systemPrompt, userPrompt).text());
    }
}
