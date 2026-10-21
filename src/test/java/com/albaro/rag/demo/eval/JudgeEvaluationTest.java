package com.albaro.rag.demo.eval;

import com.albaro.rag.demo.dto.RagResponse;
import com.albaro.rag.demo.service.DocumentChunkService;
import com.albaro.rag.demo.service.RagService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.List;

@SpringBootTest
class JudgeEvaluationTest {

    private static final String NO_INFO =
            "No se encontró información relevante para responder la pregunta.";

    @Autowired
    private RagService ragService;

    @Autowired
    private DocumentChunkService documentChunkService;

    @Value("${anthropic.api-key}")
    private String apiKey;

    @Value("${anthropic.judge-model}")
    private String judgeModel;

    @Value("${rag.llm.provider}")
    private String provider;

    @Test
    void judgeAnswers() throws IOException {

        ClaudeJudge judge = new ClaudeJudge(apiKey, judgeModel);

        List<EvalCase> cases = EvalCaseLoader.load();

        int judged = 0;
        int sumFidelidad = 0;
        int sumCompletitud = 0;
        int sumRelevancia = 0;

        System.out.println();
        System.out.println("===== EVALUACIÓN CON JUEZ =====");
        System.out.println("Generador: " + provider + " | Juez: " + judgeModel);

        for (EvalCase evalCase : cases) {

            RagResponse response = ragService.generate(evalCase.question());

            boolean abstained = response.getAnswer().trim()
                    .replace("\"", "")
                    .equals(NO_INFO);

            // El juez solo califica respuestas que debían darse y se dieron.
            // La decisión responder/abstenerse ya la mide GenerationEvaluationTest.
            if (!evalCase.shouldAnswer() || abstained) {
                continue;
            }

            JudgeVerdict verdict = judge.evaluate(
                    evalCase.question(),
                    response.getContext(),
                    evalCase.expectedAnswer(),
                    response.getAnswer()
            );

            judged++;
            sumFidelidad += verdict.fidelidad();
            sumCompletitud += verdict.completitud();
            sumRelevancia += verdict.relevancia();

            System.out.println();
            System.out.printf("[%s] fidelidad %d | completitud %d | relevancia %d%n",
                    evalCase.id(),
                    verdict.fidelidad(),
                    verdict.completitud(),
                    verdict.relevancia());
            System.out.println("  " + verdict.justificacion());
        }

        System.out.println();
        System.out.println("-------------------------------");
        System.out.printf("Respuestas evaluadas: %d%n", judged);
        System.out.printf("Promedio fidelidad:   %.2f%n", (double) sumFidelidad / judged);
        System.out.printf("Promedio completitud: %.2f%n", (double) sumCompletitud / judged);
        System.out.printf("Promedio relevancia:  %.2f%n", (double) sumRelevancia / judged);
        System.out.println("===============================");
    }

//    // Reconstruye el mismo contexto que RagService le envió al LLM.
//    private String buildContext(String question) {
//
//        List<RetrievalResult> results = documentChunkService.findSimilar(question);
//
//        return results.stream()
//                .map(result -> "[FUENTE: %s | CHUNK: %d]%n%s%n[FIN DEL FRAGMENTO]".formatted(
//                        result.source(),
//                        result.chunkIndex(),
//                        result.content()))
//                .collect(Collectors.joining("\n\n"));
//    }
}