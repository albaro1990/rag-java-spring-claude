package com.albaro.rag.demo.dto;

import java.util.List;

public record ConversationSummary(
        String customerContext,
        String cardLast4,
        String activeProcess,
        List<String> confirmedFacts,
        List<String> pendingQuestions,
        List<String> preferences
) {
}