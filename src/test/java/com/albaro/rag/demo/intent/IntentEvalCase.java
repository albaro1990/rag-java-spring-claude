package com.albaro.rag.demo.intent;

public record IntentEvalCase(
        String id,
        String split,
        String message,
        String expectedCategory
) {
}
