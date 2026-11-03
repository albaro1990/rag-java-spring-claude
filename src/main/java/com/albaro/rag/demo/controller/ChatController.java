package com.albaro.rag.demo.controller;

import com.albaro.rag.demo.dto.ChatRequest;
import com.albaro.rag.demo.dto.ChatResponse;
import com.albaro.rag.demo.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    public ChatResponse sendMessage(@Valid @RequestBody ChatRequest request) {
        return chatService.sendMessage(request);
    }
}