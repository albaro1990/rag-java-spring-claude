package com.albaro.rag.demo.intent;

import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

final class IntentEvalCaseLoader {

    private IntentEvalCaseLoader() {
    }

    static List<IntentEvalCase> load() throws IOException {
        JsonMapper mapper = new JsonMapper();
        try (InputStream input = new ClassPathResource("eval/intent-cases.json").getInputStream()) {
            IntentEvalCase[] cases = mapper.readValue(input, IntentEvalCase[].class);
            String split = System.getProperty("eval.split");
            List<IntentEvalCase> selected = Arrays.stream(cases)
                    .filter(c -> split == null || split.equals(c.split()))
                    .toList();
            System.out.println("Casos de intención cargados: " + selected.size()
                    + (split == null ? " (todos)" : " (set " + split + ")"));
            return selected;
        }
    }
}
