
package com.albaro.rag.demo.ingestion;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DocumentChunkerTest {

    @Test
    void shouldReturnEmptyListForNullOrBlankText() {
        DocumentChunker chunker = new DocumentChunker(100, 20);

        assertTrue(chunker.split(null).isEmpty());
        assertTrue(chunker.split("   ").isEmpty());
    }

    @Test
    void shouldReturnOneChunkWhenTextFitsWithinChunkSize() {
        DocumentChunker chunker = new DocumentChunker(100, 20);
        String text = "Este es un texto corto.";

        List<String> chunks = chunker.split(text);

        assertEquals(1, chunks.size());
        assertEquals(text, chunks.get(0));
    }

    @Test
    void shouldNotProduceEmptyChunks() {
        DocumentChunker chunker = new DocumentChunker(100, 20);
        String text = """
                Primera oración del documento.
                Segunda oración del documento.
                Tercera oración del documento.
                Cuarta oración del documento.
                """;

        List<String> chunks = chunker.split(text);

        assertFalse(chunks.isEmpty());
        assertTrue(chunks.stream().allMatch(chunk -> !chunk.isBlank()));
    }

    @Test
    void shouldRejectInvalidChunkConfiguration() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new DocumentChunker(0, 0)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new DocumentChunker(100, 100)
        );
    }


    @Test
    void shouldPreserveAllSentencesAcrossChunks() {
        DocumentChunker chunker = new DocumentChunker(100, 20);

        String text = """
            Primera oración contiene información importante.
            Segunda oración contiene información importante.
            Tercera oración contiene información importante.
            Cuarta oración contiene información importante.
            Quinta oración contiene información importante.
            """;

        List<String> chunks = chunker.split(text);

        assertTrue(chunks.size() > 1);

        for (String sentence : List.of(
                "Primera oración contiene información importante.",
                "Segunda oración contiene información importante.",
                "Tercera oración contiene información importante.",
                "Cuarta oración contiene información importante.",
                "Quinta oración contiene información importante."
        )) {
            assertTrue(
                    chunks.stream().anyMatch(chunk -> chunk.contains(sentence)),
                    "No se encontró la oración completa: " + sentence
            );
        }
    }


    @Test
    void shouldPreserveOverlapBetweenConsecutiveChunks() {
        DocumentChunker chunker = new DocumentChunker(100, 20);

        String text = """
            Primera oración con información relevante para el cliente.
            Segunda oración con información relevante para el cliente.
            Tercera oración con información relevante para el cliente.
            Cuarta oración con información relevante para el cliente.
            Quinta oración con información relevante para el cliente.
            Sexta oración con información relevante para el cliente.
            """;

        List<String> chunks = chunker.split(text);

        assertTrue(chunks.size() > 1);

        for (int i = 0; i < chunks.size() - 1; i++) {
            String currentChunk = chunks.get(i);
            String nextChunk = chunks.get(i + 1);

            assertTrue(
                    currentChunk.length() > 0 && nextChunk.length() > 0,
                    "Los chunks consecutivos no deben estar vacíos"
            );
        }
    }


    @Test
    void shouldPreserveEverySentenceInLongerText() {
        DocumentChunker chunker = new DocumentChunker(80, 20);

        List<String> sentences = List.of(
                "El cliente solicita el bloqueo de su tarjeta.",
                "La institución valida la identidad del cliente.",
                "El sistema registra el bloqueo solicitado.",
                "El cliente revisa las operaciones recientes.",
                "Las operaciones no reconocidas deben reportarse.",
                "La institución puede investigar posibles fraudes.",
                "El cliente puede solicitar una tarjeta de reemplazo."
        );

        String text = String.join(" ", sentences);

        List<String> chunks = chunker.split(text);

        assertTrue(chunks.size() > 1);

        for (String sentence : sentences) {
            assertTrue(
                    chunks.stream().anyMatch(chunk -> chunk.contains(sentence)),
                    "No se encontró la oración completa: " + sentence
            );
        }
    }


    @Test
    void shouldPreserveContinuityBetweenConsecutiveChunks() {
        DocumentChunker chunker = new DocumentChunker(80, 20);

        String text = """
            El cliente solicita el bloqueo de su tarjeta.
            La institución valida la identidad del cliente.
            El sistema registra el bloqueo solicitado.
            El cliente revisa las operaciones recientes.
            Las operaciones no reconocidas deben reportarse.
            La institución puede investigar posibles fraudes.
            El cliente puede solicitar una tarjeta de reemplazo.
            """;

        List<String> chunks = chunker.split(text);

        assertTrue(chunks.size() > 1);

        int previousEnd = -1;

        for (String chunk : chunks) {
            int position = text.indexOf(chunk);

            assertTrue(
                    position >= 0,
                    "El chunk no se encuentra en el texto original: " + chunk
            );

            if (previousEnd >= 0) {

                if (position > previousEnd) {
                    String gap = text.substring(previousEnd, position);

                    assertTrue(
                            gap.isBlank(),
                            "Se perdió contenido entre chunks: " + gap
                    );
                }
            }

            previousEnd = position + chunk.length();
        }
    }
}