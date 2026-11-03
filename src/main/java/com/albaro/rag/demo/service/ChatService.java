package com.albaro.rag.demo.service;

import com.albaro.rag.demo.dto.ChatRequest;
import com.albaro.rag.demo.dto.ChatResponse;
import com.albaro.rag.demo.dto.ConversationSummary;
import com.albaro.rag.demo.dto.RagResponse;
import com.albaro.rag.demo.entity.ChatMessage;
import com.albaro.rag.demo.entity.ChatSummary;
import com.albaro.rag.demo.enums.ChatRole;
import com.albaro.rag.demo.llm.LlmResult;
import com.albaro.rag.demo.repository.ChatMessageRepository;
import com.albaro.rag.demo.repository.ChatSummaryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final RagService ragService;
    private final QueryRewriter queryRewriter;
    private final TransactionTemplate transactionTemplate;
    private final ChatSummaryRepository chatSummaryRepository;
    private final ConversationSummarizer conversationSummarizer;
    private final ObjectMapper objectMapper;

    public ChatResponse sendMessage(ChatRequest request) {

        // 1. Recuperar los últimos 6 mensajes en orden cronológico.
        List<ChatMessage> history =
                new ArrayList<>(
                        chatMessageRepository
                                .findTop6BySessionIdOrderByCreatedAtDescIdDesc(
                                        request.sessionId()
                                )
                );

        Collections.reverse(history);

        // 2. Recuperar el resumen persistido.
        ChatSummary chatSummary = chatSummaryRepository
                .findById(request.sessionId())
                .orElse(null);

        String summaryText = chatSummary != null
                ? chatSummary.getSummary()
                : null;

        long coveredThroughId = chatSummary != null
                ? chatSummary.getCoveredThroughMessageId()
                : 0L;

        // 3. Buscar mensajes antiguos que todavía no están resumidos.
        if (history.size() == 6) {

            Long oldestWindowMessageId = history.get(0).getId();

            List<ChatMessage> pendingMessages =
                    chatMessageRepository
                            .findBySessionIdAndIdGreaterThanAndIdLessThanOrderByIdAsc(
                                    request.sessionId(),
                                    coveredThroughId,
                                    oldestWindowMessageId
                            );

            // 4. Actualizar el resumen solamente si hay mensajes nuevos.
            if (!pendingMessages.isEmpty()) {

                ConversationSummary updatedSummary =
                        conversationSummarizer.summarize(
                                summaryText,
                                pendingMessages
                        );

                try {
                    summaryText = objectMapper.writeValueAsString(updatedSummary);
                } catch (JacksonException e) {
                    throw new IllegalStateException(
                            "No se pudo serializar el resumen de conversación",
                            e
                    );
                }

                // Actualizar o crear el registro.
                if (chatSummary == null) {
                    chatSummary = new ChatSummary();
                    chatSummary.setSessionId(request.sessionId());
                }

                chatSummary.setSummary(summaryText);
                chatSummary.setCoveredThroughMessageId(
                        pendingMessages.get(pendingMessages.size() - 1).getId()
                );

                chatSummaryRepository.save(chatSummary);
            }
        }

        // 5. Condensar la pregunta con resumen + últimos 6 mensajes.
        String condensedQuestion = request.message();

        if (!history.isEmpty()) {
            LlmResult rewritten = queryRewriter.rewrite(
                    request.message(),
                    summaryText,
                    history
            );

            condensedQuestion = rewritten.text();
        }

        // 6. Ejecutar el RAG.
        RagResponse ragResponse = ragService.generate(condensedQuestion);

        // 7. Construir los mensajes originales.
        ChatMessage userMessage = buildMessage(
                request.sessionId(),
                ChatRole.USER,
                request.message()
        );

        ChatMessage assistantMessage = buildMessage(
                request.sessionId(),
                ChatRole.ASSISTANT,
                ragResponse.getAnswer()
        );

        // 8. Persistir el par atómicamente.
        transactionTemplate.executeWithoutResult(status -> {
            chatMessageRepository.save(userMessage);
            chatMessageRepository.save(assistantMessage);
        });

        return new ChatResponse(
                request.sessionId(),
                condensedQuestion,
                ragResponse
        );
    }

    private ChatMessage buildMessage(
            String sessionId,
            ChatRole role,
            String content
    ) {
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setSessionId(sessionId);
        chatMessage.setRole(role);
        chatMessage.setContent(content);
        return chatMessage;
    }
}