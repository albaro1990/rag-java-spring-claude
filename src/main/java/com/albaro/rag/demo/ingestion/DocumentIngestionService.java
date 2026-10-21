package com.albaro.rag.demo.ingestion;

import com.albaro.rag.demo.entity.DocumentChunk;
import com.albaro.rag.demo.service.DocumentChunkService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class DocumentIngestionService {

    private final DocumentLoader documentLoader;
    private final DocumentChunker documentChunker;
    private final DocumentChunkService documentChunkService;
    private final Path documentsPath;

    public DocumentIngestionService(
            DocumentLoader documentLoader,
            DocumentChunker documentChunker,
            DocumentChunkService documentChunkService,
            @Value("${rag.documents-path}") String documentsPath) {

        this.documentLoader = documentLoader;
        this.documentChunker = documentChunker;
        this.documentChunkService = documentChunkService;
        this.documentsPath = Path.of(documentsPath);
    }

    private List<DocumentChunkData> processDocuments() {

        List<DocumentChunkData> result = new ArrayList<>();

        try (Stream<Path> files = Files.list(documentsPath)) {

            files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".txt"))
                    .forEach(path -> processFile(path, result));

        } catch (Exception e) {
            throw new IllegalStateException(
                    "No se pudieron procesar los documentos",
                    e
            );
        }

        return result;
    }

    private void processFile(
            Path file,
            List<DocumentChunkData> result) {

        String content = documentLoader.load(file);

        List<String> chunks =
                documentChunker.split(content);

        for (int i = 0; i < chunks.size(); i++) {

            result.add(
                    new DocumentChunkData(
                            chunks.get(i),
                            file.getFileName().toString(),
                            i
                    )
            );
        }
    }

    public List<DocumentChunk> ingest() {

        // Agrupar los chunks por documento, conservando el orden.
        Map<String, List<DocumentChunkData>> chunksBySource =
                processDocuments().stream()
                        .collect(Collectors.groupingBy(
                                DocumentChunkData::source,
                                LinkedHashMap::new,
                                Collectors.toList()
                        ));

        // Protección: si la carpeta está vacía, probablemente la ruta está mal
        // configurada. No borramos toda la base por un error de configuración.
        if (chunksBySource.isEmpty()) {
            throw new IllegalStateException(
                    "No se encontraron documentos en " + documentsPath
            );
        }

        List<DocumentChunk> saved = new ArrayList<>();

        chunksBySource.forEach((source, chunks) -> {

            // 1. Calcular embeddings fuera de la transacción (es lo lento).
            List<DocumentChunk> entities = chunks.stream()
                    .map(documentChunkService::toEntity)
                    .toList();

            // 2. Borrar y guardar en una transacción corta.
            saved.addAll(
                    documentChunkService.replaceDocument(source, entities)
            );
        });
        // 3. Quitar de la base los documentos que ya no existen en la carpeta.
        documentChunkService.removeMissingDocuments(chunksBySource.keySet());

        return saved;
    }
}