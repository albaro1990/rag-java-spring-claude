package com.albaro.rag.demo.controller;

import com.albaro.rag.demo.dto.RetrievalResult;
import com.albaro.rag.demo.entity.DocumentChunk;
import com.albaro.rag.demo.ingestion.DocumentIngestionService;
import com.albaro.rag.demo.service.DocumentChunkService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentChunkService service;
    private final DocumentIngestionService ingestionService;

    public DocumentController(DocumentChunkService service, DocumentIngestionService ingestionService) {
        this.service = service;
        this.ingestionService = ingestionService;
    }

    // Muestra los chunks que devuelve la búsqueda vectorial (útil para depurar retrieval).
    @GetMapping("/search")
    public List<RetrievalResult> search(
            @RequestParam String question) {

        return service.findSimilar(question);
    }

    @PostMapping("/ingest")
    public List<DocumentChunk> ingest() {
        return ingestionService.ingest();
    }
}
