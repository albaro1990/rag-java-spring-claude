package com.albaro.rag.demo.eval;

import com.albaro.rag.demo.dto.RagMetrics;
import com.albaro.rag.demo.dto.RagResponse;
import com.albaro.rag.demo.service.RagService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@SpringBootTest
class GenerationEvaluationTest {

    private static final String NO_INFO =
            "No se encontró información relevante para responder la pregunta.";

    // Precio en dólares por millón de tokens: {entrada, salida}.
    private static final Map<String, double[]> PRICES = Map.of(
            "claude-opus-5-5", new double[]{4.00, 20.00},
            "claude-sonnet-5-5", new double[]{2.00, 10.00},
            "claude-haiku-5-5", new double[]{0.10, 0.50}
    );

    @Autowired
    private RagService ragService;

    @Value("${rag.llm.provider}")
    private String provider;

    @Value("${anthropic.model}")
    private String claudeModel;

    @Test
    void evaluateGeneration() throws IOException {

        List<EvalCase> cases = EvalCaseLoader.load();

        // Ollama corre local: costo 0.
        String model = provider.equals("claude") ? claudeModel : "ollama";
        double[] price = PRICES.getOrDefault(model, new double[]{0, 0});

        int correctDecisions = 0;
        int hallucinationRisk = 0;
        int falseRefusals = 0;
        int formatViolations = 0;

        long totalRetrievalMs = 0;
        long totalLlmMs = 0;
        long maxLlmMs = 0;
        int llmCalls = 0;

        // Tokens de TODAS las llamadas (reescritura + reranker + generación).
        long totalInputTokens = 0;
        long totalOutputTokens = 0;

        // De esos, cuántos fueron de reescritura + reranker.
        long retrievalInputTokens = 0;
        long retrievalOutputTokens = 0;

        System.out.println();
        System.out.println("===== EVALUACIÓN DE GENERACIÓN =====");
        System.out.println("Modelo: " + model);

        for (EvalCase evalCase : cases) {

            RagResponse response = ragService.generate(evalCase.question());
            RagMetrics metrics = response.getMetrics();

            boolean abstained = response.getAnswer().trim()
                    .replace("\"", "")
                    .equals(NO_INFO);

            // Chequeo determinístico: ¿la respuesta trae formato markdown?
            if (response.getAnswer().contains("**")
                    || response.getAnswer().lines().anyMatch(l -> l.trim().startsWith("- "))) {
                formatViolations++;
            }

            boolean answered = !abstained;

            String verdict;

            if (evalCase.shouldAnswer() && answered) {
                verdict = "OK   respondió (revisar contenido)";
                correctDecisions++;
            } else if (!evalCase.shouldAnswer() && abstained) {
                verdict = "OK   se abstuvo";
                correctDecisions++;
            } else if (!evalCase.shouldAnswer()) {
                // Debía abstenerse y respondió: posible alucinación.
                verdict = "FAIL respondió sin tener la información";
                hallucinationRisk++;
            } else {
                // Debía responder y se abstuvo.
                verdict = "FAIL se abstuvo teniendo la información";
                falseRefusals++;
            }

            totalRetrievalMs += metrics.retrievalMs();

            // Una sola suma por pregunta: los totales ya incluyen todas las llamadas.
            totalInputTokens += metrics.totalInputTokens();
            totalOutputTokens += metrics.totalOutputTokens();
            retrievalInputTokens += metrics.retrievalInputTokens();
            retrievalOutputTokens += metrics.retrievalOutputTokens();

            // Si el retrieval vino vacío, el LLM de generación no se llamó.
            if (metrics.llmMs() > 0) {
                llmCalls++;
                totalLlmMs += metrics.llmMs();
                maxLlmMs = Math.max(maxLlmMs, metrics.llmMs());
            }

            System.out.println();
            System.out.printf("[%s] %s | %s%n",
                    evalCase.id(), evalCase.category(), verdict);
            System.out.println("  Pregunta : " + evalCase.question());
            System.out.println("  Esperada : " + evalCase.expectedAnswer());
            System.out.println("  Obtenida : " + response.getAnswer());
            System.out.println("  Fuentes  : " + response.getSources().size());
            System.out.printf("  Métricas : retrieval %d ms | LLM %d ms | generación %d + %d | reescritura y rerank %d + %d%n",
                    metrics.retrievalMs(), metrics.llmMs(),
                    metrics.inputTokens(), metrics.outputTokens(),
                    metrics.retrievalInputTokens(), metrics.retrievalOutputTokens());
        }

        double cost = totalInputTokens / 1_000_000.0 * price[0]
                + totalOutputTokens / 1_000_000.0 * price[1];

        System.out.println();
        System.out.println("------------------------------------");
        System.out.printf("Decisión correcta (responder/abstenerse): %d/%d%n",
                correctDecisions, cases.size());
        System.out.printf("Respondió cuando debía abstenerse:       %d%n",
                hallucinationRisk);
        System.out.printf("Se abstuvo cuando debía responder:       %d%n",
                falseRefusals);
        System.out.printf("Respuestas con markdown:                 %d%n",
                formatViolations);
        System.out.println("------------------------------------");
        System.out.printf("Retrieval promedio:   %d ms%n", totalRetrievalMs / cases.size());
        System.out.printf("LLM promedio:         %d ms (máximo %d ms, %d llamadas)%n",
                llmCalls == 0 ? 0 : totalLlmMs / llmCalls, maxLlmMs, llmCalls);
        System.out.printf("Tokens totales:       %d entrada + %d salida%n",
                totalInputTokens, totalOutputTokens);
        System.out.printf("  de ellos, reescritura y rerank: %d entrada + %d salida%n",
                retrievalInputTokens, retrievalOutputTokens);
        System.out.printf("Costo de la corrida:  US$ %.4f (US$ %.5f por pregunta)%n",
                cost, cost / cases.size());
        System.out.println("====================================");
    }
}
