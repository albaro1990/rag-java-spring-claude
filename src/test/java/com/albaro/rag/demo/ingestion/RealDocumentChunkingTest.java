package com.albaro.rag.demo.ingestion;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealDocumentChunkingTest {

    private final DocumentLoader loader = new DocumentLoader();
    private final DocumentChunker chunker = new DocumentChunker(500, 100);

    @Test
    void realDocumentsHaveRealOverlap() {

        for (String name : List.of(
                "proceso_bloqueo_tarjeta.txt",
                "proceso_cambio_tarjeta.txt",
                "proceso_obtener_tarjeta.txt")) {

            String text = loader.load(
                    Path.of("src/main/resources/documents", name));

            assertFalse(text.contains("\r"), "Quedaron \\r en " + name);

            List<String> chunks = chunker.split(text);

            System.out.println("=== " + name + " -> " + chunks.size() + " chunks");

            for (int i = 0; i < chunks.size(); i++) {
                System.out.println("--- chunk " + i + " (" + chunks.get(i).length() + " chars)");
                System.out.println(chunks.get(i));
            }

            for (int i = 0; i < chunks.size() - 1; i++) {
                String firstSentenceOfNext = chunks.get(i + 1).split("(?<=[.!?])\\s")[0];

                assertTrue(
                        chunks.get(i).contains(firstSentenceOfNext),
                        name + ": el chunk " + (i + 1) + " no repite texto del chunk " + i
                );
            }
        }
    }
}