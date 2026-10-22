
package com.albaro.rag.demo.eval;

import com.albaro.rag.demo.llm.TokenCounter;
import com.albaro.rag.demo.prompt.PromptTemplateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@SpringBootTest
class TokenCountingTest {

    @Autowired
    PromptTemplateService promptTemplateService;

    @Autowired
    TokenCounter tokenCounter;

    @Test
    void shouldCompareTokenEfficiency() {

        String systemPrompt = promptTemplateService.loadSystemPrompt();

        Map<String, String> texts = new LinkedHashMap<>();

        texts.put("Español",
                "¿Cómo bloqueo mi tarjeta de crédito si la perdí?");

        texts.put("Inglés",
                "How do I block my credit card if I lost it?");

        texts.put("Número de tarjeta",
                "4532 8891 1234 5678");

        texts.put("JSON",
                "{\"cliente\":\"Ana\",\"saldo\":1520.75}");

        texts.put("Prompt RAG completo", systemPrompt);


        Map<String, Long> tokenCounts = new LinkedHashMap<>();

        System.out.printf(
                "%-24s %12s %12s %18s%n",
                "Texto", "Caracteres", "Tokens", "Caracteres/token"
        );
        System.out.println("-".repeat(70));

        long caja = tokenCounter.countTokens("a") - 1;

        for (Map.Entry<String, String> entry : texts.entrySet()) {

            String label = entry.getKey();
            String text = entry.getValue();
            long tokens = tokenCounter.countTokens(text) - caja;


            int characters = text.length();

            double charsPerToken =
                    tokens == 0 ? 0.0 : (double) characters / tokens;

            tokenCounts.put(label, tokens);

            System.out.printf(
                    Locale.US,
                    "%-24s %12d %12d %18.2f%n",
                    label, characters, tokens, charsPerToken
            );
        }

        long spanishTokens = tokenCounts.get("Español");
        long englishTokens = tokenCounts.get("Inglés");

        if (englishTokens > 0) {
            double difference =
                    ((double) spanishTokens / englishTokens - 1) * 100;

            System.out.printf(
                    Locale.US,
                    "%nEspañol frente a inglés: %.2f%% más tokens.%n",
                    difference
            );
        }
    }
}
