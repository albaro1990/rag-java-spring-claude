package com.albaro.rag.demo.service;

import com.albaro.rag.demo.dto.RerankOutcome;
import com.albaro.rag.demo.dto.RetrievalResult;
import com.albaro.rag.demo.llm.LlmClient;
import com.albaro.rag.demo.llm.LlmResult;
import com.albaro.rag.demo.llm.LlmUsage;
import com.albaro.rag.demo.prompt.PromptTemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class LlmReranker {



    private static final Pattern NUMBER = Pattern.compile("\\d+");

    private final LlmClient llmClient;
    private final PromptTemplateService promptTemplateService;

    public LlmReranker(LlmClient llmClient, PromptTemplateService promptTemplateService) {
        this.llmClient = llmClient;
        this.promptTemplateService = promptTemplateService;
    }

    public RerankOutcome rerank(
            String question,
            List<RetrievalResult> candidates,
            int topN) {

        if (candidates.isEmpty()) {
            return new RerankOutcome(candidates, LlmUsage.ZERO);
        }

        StringBuilder fragments = new StringBuilder();

        for (int i = 0; i < candidates.size(); i++) {
            fragments.append("[").append(i + 1).append("]\n")
                    .append(candidates.get(i).content())
                    .append("\n\n");
        }

        LlmResult result = llmClient.generate(
                promptTemplateService.loadRerankerSystemPrompt(),
                promptTemplateService.buildRerankerUserPrompt(question, fragments.toString())
        );
        String answer = result.text().trim();

        List<RetrievalResult> selected = parse(answer, candidates, topN);

        log.info("Reranker: {} candidatos -> {} seleccionados (tokens {} + {})",
                candidates.size(), selected.size(),
                result.inputTokens(), result.outputTokens());
        log.debug("Reranker respondió: {}", answer);

        return new RerankOutcome(selected, LlmUsage.of(result));
    }

    // Convierte "3,1,7" en los fragmentos 3, 1 y 7, en ese orden.
    private List<RetrievalResult> parse(
            String answer,
            List<RetrievalResult> candidates,
            int topN) {

        if (answer.toUpperCase().contains("NINGUNO")) {
            return List.of();
        }

        Set<Integer> indexes = new LinkedHashSet<>();
        Matcher matcher = NUMBER.matcher(answer);

        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group()) - 1;

            // Ignorar números que no corresponden a ningún fragmento.
            if (index >= 0 && index < candidates.size()) {
                indexes.add(index);
            }
        }

        List<RetrievalResult> selected = new ArrayList<>();

        for (int index : indexes) {
            if (selected.size() == topN) {
                break;
            }
            selected.add(candidates.get(index));
        }

        return selected;
    }
}