package com.albaro.rag.demo.controller;

import com.albaro.rag.demo.dto.ChunkRequest;
import com.albaro.rag.demo.dto.CreateChunkRequest;
import com.albaro.rag.demo.dto.RetrievalResult;
import com.albaro.rag.demo.entity.DocumentChunk;
import com.albaro.rag.demo.ingestion.DocumentChunkData;
import com.albaro.rag.demo.ingestion.DocumentChunker;
import com.albaro.rag.demo.ingestion.DocumentIngestionService;
import com.albaro.rag.demo.service.DocumentChunkService;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentChunker chunker;
    private final DocumentChunkService service;
    private final DocumentIngestionService ingestionService;


    public DocumentController(DocumentChunkService service, DocumentChunker chunker,DocumentIngestionService ingestionService) {
        this.service = service;
        this.chunker = chunker;
        this.ingestionService = ingestionService;
    }

    @PostMapping("/chunks")
    public DocumentChunk saveChunk(@RequestBody CreateChunkRequest request) {
        return null;
        //service.save(request.content());
    }

    @GetMapping("/search")
    public List<RetrievalResult> search(
            @RequestParam String question) {

        return service.findSimilar(question);
    }

    @PostMapping("/chunk")
    public List<String> chunk(@RequestBody ChunkRequest request) {
        return chunker.split(request.text());
    }

    @GetMapping("/ingestion-test")
    public List<DocumentChunkData> testIngestion() {

        return ingestionService.processDocuments();
    }

    @PostMapping("/ingest")
    public List<DocumentChunk> ingest() {
        return ingestionService.ingest();
    }
}
