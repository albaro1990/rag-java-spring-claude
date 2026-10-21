package com.albaro.rag.demo.eval;

import com.albaro.rag.demo.dto.RetrievalResult;
import com.albaro.rag.demo.service.RetrievalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.List;

@SpringBootTest
class RetrievalEvaluationTest {

    @Autowired
    private RetrievalService retrievalService;

    @Test
    void evaluateRetrieval() throws IOException {

        List<EvalCase> cases = EvalCaseLoader.load();

        int passed = 0;

        System.out.println();
        System.out.println("===== EVALUACIÓN DE RETRIEVAL =====");

        for (EvalCase evalCase : cases) {

            List<RetrievalResult> results =
                    retrievalService.retrieve(evalCase.question()).results();

            boolean ok;
            String detail;

            if (evalCase.shouldAnswer()) {
                // Caso con respuesta: ¿llegaron todas las evidencias?
                int found = countFoundEvidence(evalCase, results);
                int expected = evalCase.expectedEvidence().size();

                ok = found == expected;
                detail = "evidencias encontradas " + found + "/" + expected;
            } else {
                // Caso sin respuesta: lo correcto es no recuperar nada.
                ok = results.isEmpty();
                detail = "chunks recuperados " + results.size() + " (esperado 0)";
            }

            if (ok) {
                passed++;
            }

            System.out.printf(
                    "%s [%s] %-16s %s%n",
                    ok ? "OK  " : "FAIL",
                    evalCase.id(),
                    evalCase.category(),
                    detail
            );

            for (RetrievalResult result : results) {
                System.out.printf(
                        "        %s chunk %d  distancia %.3f%n",
                        result.source(),
                        result.chunkIndex(),
                        result.distance()
                );
            }
        }

        System.out.println("-----------------------------------");
        System.out.printf("RESULTADO: %d/%d casos correctos%n", passed, cases.size());
        System.out.println("===================================");
    }

    private int countFoundEvidence(
            EvalCase evalCase,
            List<RetrievalResult> results) {

        int found = 0;

        for (EvalCase.Evidence evidence : evalCase.expectedEvidence()) {

            boolean present = results.stream()
                    .anyMatch(result ->
                            result.source().equals(evidence.source())
                                    && normalize(result.content())
                                    .contains(normalize(evidence.text())));

            if (present) {
                found++;
            }
        }

        return found;
    }

    // Convierte saltos de línea y espacios repetidos en un solo espacio,
    // para que una frase se encuentre aunque el .txt la parta en dos líneas.
    private String normalize(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }
}