package com.albaro.rag.demo.controller;


import com.albaro.rag.demo.dto.AskRequest;
import com.albaro.rag.demo.dto.RagResponse;
import com.albaro.rag.demo.service.RagService;
import com.albaro.rag.demo.service.RagStreamingService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final RagService ragService;
    private final RagStreamingService ragStreamingService;

    public RagController(RagService ragService, RagStreamingService ragStreamingService) {
        this.ragService = ragService;
        this.ragStreamingService = ragStreamingService;
    }

    @PostMapping("/ask")
    public RagResponse ask(@RequestBody AskRequest request) {
        return ragService.generate(request.question());
    }

    // Server-Sent Events: el cliente recibe la respuesta en pedazos.
    @GetMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(@RequestParam String question) {
        return ragStreamingService.stream(question);
    }
}