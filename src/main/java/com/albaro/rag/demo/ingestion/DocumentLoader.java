package com.albaro.rag.demo.ingestion;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class DocumentLoader {

    public String load(Path file) {

        try {
            String content = Files.readString(file);

            // Normalizar saltos de línea de Windows (\r\n) y Mac antiguo (\r)
            // para que el resto del pipeline siempre trabaje con \n.
            return content
                    .replace("\r\n", "\n")
                    .replace('\r', '\n');

        } catch (IOException e) {
            throw new IllegalStateException(
                    "No se pudo leer el archivo: " + file,
                    e
            );
        }
    }
}