package com.albaro.rag.demo.eval;

import com.albaro.rag.demo.dto.ChatRequest;
import com.albaro.rag.demo.dto.ChatResponse;
import com.albaro.rag.demo.dto.RagResponse;
import com.albaro.rag.demo.entity.ChatMessage;
import com.albaro.rag.demo.repository.ChatMessageRepository;
import com.albaro.rag.demo.service.ChatService;
import com.albaro.rag.demo.service.RagService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Evalúa el chat con conversaciones de varios turnos.
// Por cada mensaje comprueba 3 cosas:
//   1. decisión: ¿respondió o se abstuvo como debía?
//   2. fuente: ¿la evidencia viene de un documento esperado?
//   3. condensada: ¿la pregunta reescrita tiene lo que debe y NO tiene lo que no debe?
// Usa la base de datos real, así que crea las filas de cada sesión y las borra al terminar.
@SpringBootTest
class ChatConversationEvaluationTest {

    private static final String NO_INFO =
            "No se encontró información relevante para responder la pregunta.";

    // -Deval.baseline=true: salta el historial y manda cada mensaje solo al RAG.
    // Sirve para comparar "con historial" contra "sin historial".
    private static final boolean BASELINE = System.getProperty("eval.baseline") != null;

    @Autowired
    private ChatService chatService;

    @Autowired
    private RagService ragService;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Test
    void evaluateConversations() throws IOException {

        List<ChatEvalConversation> conversations = ChatEvalLoader.load();
        if (conversations.isEmpty()) {
            throw new IllegalStateException("No se cargaron conversaciones de evaluación");
        }

        // [mensajes evaluados, aprobados], por tipo y por conjunto
        Map<String, int[]> byType = new LinkedHashMap<>();
        Map<String, int[]> bySplit = new LinkedHashMap<>();
        List<String> failures = new ArrayList<>();

        System.out.println("\n===== EVALUACIÓN DE CONVERSACIONES ("
                + (BASELINE ? "SIN historial" : "CON historial") + ") =====");

        for (ChatEvalConversation conversation : conversations) {

            String sessionId = "eval-" + conversation.id() + "-" + UUID.randomUUID().toString().substring(0, 8);
            System.out.println("\n--- " + conversation.id() + " (" + conversation.split() + ") sesión " + sessionId);

            try {
                int turnNumber = 0;
                for (ChatEvalTurn turn : conversation.turns()) {
                    turnNumber++;
                    String label = conversation.id() + "." + turnNumber;

                    RagResponse rag;
                    String condensed;
                    if (BASELINE) {
                        // Sin historial: el RAG recibe el mensaje tal cual, como antes del módulo 3.
                        rag = ragService.generate(turn.message());
                        condensed = turn.message();
                    } else {
                        ChatResponse response = chatService.sendMessage(new ChatRequest(sessionId, turn.message()));
                        rag = response.ragResponse();
                        condensed = response.condensedQuestion();
                    }

                    String answer = rag.getAnswer().strip();

                    boolean answered = !answer.equals(NO_INFO);
                    boolean decisionOk = answered == turn.shouldAnswer();

                    boolean sourceOk = true;
                    List<String> expectedSources = orEmpty(turn.expectedSources());
                    if (turn.shouldAnswer() && !expectedSources.isEmpty()) {
                        sourceOk = rag.getSources().stream()
                                .anyMatch(s -> expectedSources.contains(s.getSource()));
                    }

                    // Fuente prohibida: la evidencia no puede venir del documento equivocado.
                    List<String> wrongSources = rag.getSources().stream()
                            .map(s -> s.getSource())
                            .filter(orEmpty(turn.forbiddenSources())::contains)
                            .distinct()
                            .toList();
                    if (!wrongSources.isEmpty()) {
                        sourceOk = false;
                    }

                    List<String> missing = new ArrayList<>();
                    List<String> contaminated = new ArrayList<>();
                    if (!BASELINE && turnNumber > 1) {
                        String text = normalize(condensed);
                        for (List<String> group : orEmpty(turn.mustContain())) {
                            if (group.stream().noneMatch(term -> text.contains(normalize(term)))) {
                                missing.add(group.toString());
                            }
                        }
                        for (String forbidden : orEmpty(turn.mustNotContain())) {
                            if (text.contains(normalize(forbidden))) {
                                contaminated.add(forbidden);
                            }
                        }
                    }
                    boolean condensedOk = missing.isEmpty() && contaminated.isEmpty();

                    boolean passed = decisionOk && sourceOk && condensedOk;

                    count(byType, turn.type(), passed);
                    count(bySplit, conversation.split(), passed);

                    System.out.printf("[%s] %-13s %s | decisión %s fuente %s condensada %s%n",
                            label, turn.type(), passed ? "OK    " : "FALLA ",
                            mark(decisionOk), mark(sourceOk), mark(condensedOk));
                    System.out.println("   Mensaje   : " + turn.message());
                    System.out.println("   Condensada: " + condensed);
                    System.out.println("   Respuesta : " + shorten(answer));

                    if (!passed) {
                        StringBuilder why = new StringBuilder();
                        if (!decisionOk) {
                            why.append("decisión (esperaba ")
                                    .append(turn.shouldAnswer() ? "responder" : "abstenerse")
                                    .append(", hizo ").append(answered ? "responder" : "abstenerse").append("); ");
                        }
                        if (!sourceOk) {
                            why.append("fuente (esperaba ").append(expectedSources);
                            if (!wrongSources.isEmpty()) {
                                why.append(", pero apareció el documento equivocado ").append(wrongSources);
                            }
                            why.append("); ");
                        }
                        if (!missing.isEmpty()) {
                            why.append("falta ").append(missing).append("; ");
                        }
                        if (!contaminated.isEmpty()) {
                            why.append("contaminada con ").append(contaminated).append("; ");
                        }
                        System.out.println("   Motivo    : " + why);
                        failures.add(label + " " + why);
                    }
                }
            } finally {
                // Limpieza: borramos las filas de esta sesión de prueba.
                List<ChatMessage> rows = chatMessageRepository.findBySessionIdOrderByCreatedAtAscIdAsc(sessionId);
                chatMessageRepository.deleteAll(rows);
            }
        }

        System.out.println("\n------------------------------------");
        printSummary("Por tipo de mensaje", byType);
        printSummary("Por conjunto", bySplit);

        if (!failures.isEmpty()) {
            System.out.println("\nFallos:");
            failures.forEach(f -> System.out.println("  " + f));
        }
        System.out.println("====================================\n");
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static void count(Map<String, int[]> map, String key, boolean passed) {
        int[] counter = map.computeIfAbsent(key, k -> new int[2]);
        counter[0]++;
        if (passed) {
            counter[1]++;
        }
    }

    private static void printSummary(String title, Map<String, int[]> map) {
        System.out.println(title + ":");
        int total = 0;
        int ok = 0;
        for (Map.Entry<String, int[]> entry : map.entrySet()) {
            System.out.printf("  %-14s %d/%d%n", entry.getKey(), entry.getValue()[1], entry.getValue()[0]);
            total += entry.getValue()[0];
            ok += entry.getValue()[1];
        }
        System.out.printf("  %-14s %d/%d%n", "TOTAL", ok, total);
    }

    // Sin tildes ni mayúsculas, para comparar palabras.
    private static String normalize(String text) {
        return Normalizer.normalize(text.toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }

    private static String mark(boolean ok) {
        return ok ? "✓" : "✗";
    }

    private static String shorten(String text) {
        String oneLine = text.replace('\n', ' ');
        return oneLine.length() > 110 ? oneLine.substring(0, 110) + "..." : oneLine;
    }
}
