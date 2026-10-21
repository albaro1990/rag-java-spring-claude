package com.albaro.rag.demo.ingestion;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DocumentChunker {

    private final int chunkSize;
    private final int chunkOverlap;

    public DocumentChunker(
            @Value("${rag.chunk-size}") int chunkSize,
            @Value("${rag.chunk-overlap}") int chunkOverlap) {

        if (chunkSize <= 0) {
            throw new IllegalArgumentException(
                    "El tamaño del chunk debe ser mayor que cero");
        }

        if (chunkOverlap < 0 || chunkOverlap >= chunkSize) {
            throw new IllegalArgumentException(
                    "El overlap debe ser mayor o igual a cero y menor que el tamaño del chunk");
        }

        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    public List<String> split(String text) {

        List<String> chunks = new ArrayList<>();

        if (text == null || text.isBlank()) {
            return chunks;
        }

        int start = 0;

        while (start < text.length()) {

            int idealEnd = Math.min(
                    start + chunkSize,
                    text.length()
            );

            if (idealEnd == text.length()) {
                chunks.add(text.substring(start).trim());
                break;
            }

            int end = findNaturalBreak(text, start, idealEnd);

            String chunk = text.substring(start, end).trim();

            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }



            start = findNextStart(text, start, end);
        }

        return chunks;
    }

    // Decide dónde empieza el siguiente chunk.
    // El overlap se hace con oraciones completas: el siguiente chunk
    // empieza al inicio de la oración que contiene la posición
    // "end - chunkOverlap". Así repite al menos chunkOverlap caracteres
    // y nunca empieza a mitad de una oración.
    private int findNextStart(String text, int start, int end) {

        int target = Math.max(start + 1, end - chunkOverlap);

        int sentenceStart = findSentenceStartBefore(text, start, target);

        // Si el chunk es una sola oración, no hay oración que repetir:
        // el siguiente chunk empieza donde terminó este (sin overlap).
        if (sentenceStart <= start || sentenceStart >= end) {
            return end;
        }

        return sentenceStart;
    }

    private int findSentenceStartBefore(
            String text,
            int lowerLimit,
            int position) {

        for (int i = position; i > lowerLimit; i--) {

            char previous = text.charAt(i - 1);

            boolean afterSentenceEnd =
                    (previous == '.' || previous == '!' || previous == '?')
                            && Character.isWhitespace(text.charAt(i));

            boolean afterParagraph =
                    previous == '\n' && text.charAt(i) == '\n';

            if (afterSentenceEnd || afterParagraph) {

                int next = i;

                while (next < text.length()
                        && Character.isWhitespace(text.charAt(next))) {
                    next++;
                }

                return next;
            }
        }

        return -1;
    }

    private int findNaturalBreak(
            String text,
            int start,
            int idealEnd) {

        // 1. Buscar el final de un párrafo.
        int paragraph = text.lastIndexOf("\n\n", idealEnd);

        if (paragraph > start) {
            return paragraph;
        }

        // 2. Buscar el final de una oración.
        int sentence = findLastSentenceEnd(
                text,
                start,
                idealEnd
        );

        if (sentence > start) {
            return sentence;
        }

        // 3. Buscar un espacio entre palabras.
        int space = text.lastIndexOf(' ', idealEnd);

        if (space > start) {
            return space;
        }

        // 4. Si no hay un límite natural, usar el límite ideal.
        return idealEnd;
    }

    private int findLastSentenceEnd(
            String text,
            int start,
            int end) {

        for (int i = end - 1; i > start; i--) {

            char character = text.charAt(i);

            if (character == '.'
                    || character == '!'
                    || character == '?') {

                return i + 1;
            }
        }

        return -1;
    }
}