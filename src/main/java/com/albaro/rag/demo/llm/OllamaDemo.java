package com.albaro.rag.demo.llm;


import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;

public class OllamaDemo {

    public static void main(String[] args) {

        String contexto = """
        Una tarjeta de crédito permite realizar compras utilizando una línea de crédito.
        """;

        String pregunta = "¿Para qué sirve una tarjeta de crédito?";

        String prompt = """
        Utiliza únicamente la siguiente información para responder.

        CONTEXTO:
        %s

        PREGUNTA:
        %s
        """.formatted(contexto, pregunta);

        ChatModel model = OllamaChatModel.builder()
                .baseUrl("http://localhost:11434")
                .modelName("qwen2.5:3b")
                .build();

        String respuesta = model.chat(
                prompt
        );

        System.out.println("RESPUESTA DEL LLM:");
        System.out.println(respuesta);
    }
}