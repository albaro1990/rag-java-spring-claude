package com.albaro.rag.demo.intent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@SpringBootTest
class IntentClassifierEvaluationTest {

    private static final List<IntentCategory> CATEGORIES = List.of(
            IntentCategory.consulta,
            IntentCategory.reclamo,
            IntentCategory.bloqueo,
            IntentCategory.fuera_de_tema
    );

    @Autowired
    private IntentClassifier intentClassifier;

    @Test
    void evaluateIntentClassifier() throws IOException {
        List<IntentEvalCase> cases = IntentEvalCaseLoader.load();
        if (cases.isEmpty()) {
            throw new IllegalStateException("No se cargaron casos de evaluación de intención");
        }

        Map<IntentCategory, Map<IntentCategory, Integer>> confusion = new EnumMap<>(IntentCategory.class);
        for (IntentCategory expected : CATEGORIES) {
            Map<IntentCategory, Integer> row = new EnumMap<>(IntentCategory.class);
            for (IntentCategory predicted : CATEGORIES) {
                row.put(predicted, 0);
            }
            confusion.put(expected, row);
        }

        int correct = 0;
        List<String> errors = new ArrayList<>();
        long parseFailuresBefore = intentClassifier.getParseFailureCount();
        System.out.println("\n===== EVALUACIÓN DEL CLASIFICADOR DE INTENCIÓN =====");

        for (IntentEvalCase evalCase : cases) {
            IntentCategory expected = IntentCategory.fromModel(evalCase.expectedCategory());
            IntentCategory predicted = intentClassifier.classify(evalCase.message());
            confusion.get(expected).merge(predicted, 1, Integer::sum);

            boolean isCorrect = expected == predicted;
            if (isCorrect) {
                correct++;
            } else {
                errors.add(evalCase.id() + " esperada=" + expected + " obtenida=" + predicted
                        + " mensaje=\"" + evalCase.message() + "\"");
            }

            System.out.printf("[%s] esperada=%-15s obtenida=%-15s %s | %s%n",
                    evalCase.id(), expected, predicted, isCorrect ? "OK" : "ERROR", evalCase.message());
        }

        double accuracy = (double) correct / cases.size();
        System.out.printf("\nAccuracy: %d/%d = %.2f%%%n", correct, cases.size(), accuracy * 100.0);
        System.out.println("\nMatriz de confusión (filas = esperada, columnas = predicha):");
        System.out.printf("%-18s", "esperada \\ predicha");
        for (IntentCategory category : CATEGORIES) {
            System.out.printf("%18s", category);
        }
        System.out.println();
        for (IntentCategory expected : CATEGORIES) {
            System.out.printf("%-18s", expected);
            for (IntentCategory predicted : CATEGORIES) {
                System.out.printf("%18d", confusion.get(expected).get(predicted));
            }
            System.out.println();
        }

        long parseFailures = intentClassifier.getParseFailureCount() - parseFailuresBefore;
        System.out.printf("Fallos de parseo del clasificador: %d/%d%n", parseFailures, cases.size());

        if (!errors.isEmpty()) {
            System.out.println("\nErrores de clasificación:");
            errors.forEach(System.out::println);
        }
        System.out.println("====================================================\n");
    }
}
