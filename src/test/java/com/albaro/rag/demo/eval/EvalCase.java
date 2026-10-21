package com.albaro.rag.demo.eval;

import java.util.List;

public record EvalCase(
        String id,
        String split,
        String category,
        String question,
        boolean shouldAnswer,
        List<Evidence> expectedEvidence,
        String expectedAnswer,
        String incorrectAnswer
) {

    public record Evidence(
            String source,
            String text
    ) {
    }
}