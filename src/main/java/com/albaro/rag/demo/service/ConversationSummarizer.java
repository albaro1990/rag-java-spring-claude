package com.albaro.rag.demo.service;

import com.albaro.rag.demo.dto.ConversationSummary;
import com.albaro.rag.demo.entity.ChatMessage;
import com.albaro.rag.demo.llm.LlmClient;
import com.albaro.rag.demo.llm.LlmResult;
import com.albaro.rag.demo.llm.TokenMeter;
import com.albaro.rag.demo.prompt.PromptTemplateService;
import com.fasterxml.jackson.core.JsonProcessingException;


import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ConversationSummarizer {

    private static final Pattern CARD_NUMBER_PATTERN =
            Pattern.compile("(?<!\\d)(?:\\d[ -]?){13,19}(?!\\d)");

    private static final Pattern FOUR_DIGITS =
            Pattern.compile("\\d{4}");

    private final LlmClient llmClient;
    private final PromptTemplateService promptTemplateService;
    private final TokenMeter tokenMeter;
    private final ObjectMapper objectMapper;

    public ConversationSummarizer(
            LlmClient llmClient,
            PromptTemplateService promptTemplateService,
            TokenMeter tokenMeter,
            ObjectMapper objectMapper
    ) {
        this.llmClient = llmClient;
        this.promptTemplateService = promptTemplateService;
        this.tokenMeter = tokenMeter;
        this.objectMapper = objectMapper;
    }

    public ConversationSummary summarize(
            String previousSummary,
            List<ChatMessage> messagesToSummarize
    ) {

        if (messagesToSummarize == null || messagesToSummarize.isEmpty()) {
            throw new IllegalArgumentException(
                    "Se necesitan mensajes para generar el resumen"
            );
        }

        long start = System.nanoTime();

        String userPrompt = buildUserPrompt(
                previousSummary,
                messagesToSummarize
        );

        LlmResult result = llmClient.generate(
                promptTemplateService.loadSummarizerSystemPrompt(),
                userPrompt
        );

        long latencyMs = (System.nanoTime() - start) / 1_000_000;

        tokenMeter.record("summarize", result, latencyMs);

        ConversationSummary summary = parseSummary(result.text());

        log.info(
                "Resumen de conversación generado. Mensajes procesados={}",
                messagesToSummarize.size()
        );

        return sanitize(summary);
    }

    private String buildUserPrompt(
            String previousSummary,
            List<ChatMessage> messages
    ) {

        String summaryText =
                previousSummary == null || previousSummary.isBlank()
                        ? "No existe un resumen anterior."
                        : escapeDelimiters(
                        redactCardNumbers(previousSummary)
                );

        String messagesText = messages.stream()
                .map(message -> message.getRole()
                        + ": "
                        + escapeDelimiters(
                        redactCardNumbers(message.getContent())
                ))
                .collect(Collectors.joining("\n"));

        return """
                <resumen_anterior>
                %s
                </resumen_anterior>

                <mensajes_nuevos>
                %s
                </mensajes_nuevos>

                Actualiza el resumen utilizando el resumen anterior
                y los nuevos mensajes.
                """.formatted(
                escapeDelimiters(summaryText),
                messagesText
        );
    }

    private ConversationSummary parseSummary(String response) {

        String json = response.strip();

        // Elimina cercas Markdown si el modelo las devuelve.
        if (json.startsWith("```")) {
            json = json.replaceFirst("^```(?:json)?\\s*", "");
            json = json.replaceFirst("\\s*```$", "");
        }

        if (response == null || response.isBlank()) {
            throw new IllegalStateException(
                    "El LLM devolvió una respuesta vacía"
            );
        }

        try {
            return objectMapper.readValue(
                    json,
                    ConversationSummary.class
            );
        } catch (JacksonException e) {
            throw new IllegalStateException(
                    "El LLM devolvió un resumen JSON inválido",
                    e
            );
        }
    }

    private ConversationSummary sanitize(ConversationSummary summary) {

        String cardLast4 = summary.cardLast4();

        // Solo aceptamos exactamente cuatro dígitos.
        if (cardLast4 != null &&
                !FOUR_DIGITS.matcher(cardLast4).matches()) {

            log.warn("cardLast4 descartado por formato inválido");
            cardLast4 = null;
        }

        return new ConversationSummary(
                redactCardNumbers(summary.customerContext()),
                cardLast4,
                redactCardNumbers(summary.activeProcess()),
                sanitizeList(summary.confirmedFacts()),
                sanitizeList(summary.pendingQuestions()),
                sanitizeList(summary.preferences())
        );
    }

    private List<String> sanitizeList(List<String> values) {

        if (values == null) {
            return List.of();
        }

        return values.stream()
                .map(this::redactCardNumbers)
                .toList();
    }

    private String redactCardNumbers(String text) {

        if (text == null) {
            return null;
        }

        Matcher matcher = CARD_NUMBER_PATTERN.matcher(text);
        StringBuffer sanitized = new StringBuffer();

        while (matcher.find()) {
            String digits = matcher.group().replaceAll("[ -]", "");

            String replacement = "****" +
                    digits.substring(digits.length() - 4);

            matcher.appendReplacement(
                    sanitized,
                    Matcher.quoteReplacement(replacement)
            );
        }

        matcher.appendTail(sanitized);

        return sanitized.toString();
    }

    private String escapeDelimiters(String text) {
        return text.replaceAll(
                "(?i)</(resumen_anterior|mensajes_nuevos)>",
                "< /$1>"
        );
    }
}