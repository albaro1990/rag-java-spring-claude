package com.albaro.rag.demo.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Conecta el streaming del RAG con una respuesta SSE.
// Se encarga del hilo, del envío de cada pedazo y de los errores,
// para que el controller solo reciba la petición y devuelva el emitter.
@Slf4j
@Service
public class RagStreamingService {

    private final RagService ragService;
    private final long timeoutMs;

    // Cada respuesta en streaming corre en su propio hilo virtual (Java 21),
    // para no bloquear los hilos que atienden peticiones HTTP.
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public RagStreamingService(
            RagService ragService,
            @Value("${rag.streaming.timeout-ms:120000}") long timeoutMs) {

        this.ragService = ragService;
        this.timeoutMs = timeoutMs;
    }

    public SseEmitter stream(String question) {

        SseEmitter emitter = new SseEmitter(timeoutMs);

        executor.execute(() -> {
            try {
                ragService.stream(question, text -> send(emitter, text));
                emitter.complete();
            } catch (Exception e) {
                log.error("Error durante el streaming de la respuesta", e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private void send(SseEmitter emitter, String text) {
        try {
            emitter.send(text);
        } catch (IOException e) {
            // El cliente cerró la conexión: cortamos el streaming.
            throw new UncheckedIOException(e);
        }
    }
}