package com.albaro.rag.demo.eval;

import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

public final class EvalCaseLoader {

    private EvalCaseLoader() {
    }

    // Carga los casos. Si se corre con -Deval.split=ajuste o
    // -Deval.split=confirmacion, devuelve solo ese set; si no, todos.
    public static List<EvalCase> load() throws IOException {

        JsonMapper mapper = new JsonMapper();

        try (InputStream input =
                     new ClassPathResource("eval/eval-cases.json").getInputStream()) {

            EvalCase[] cases = mapper.readValue(input, EvalCase[].class);
            String split = System.getProperty("eval.split");

            List<EvalCase> selected = Arrays.stream(cases)
                    .filter(c -> split == null || split.equals(c.split()))
                    .toList();

            System.out.println("Casos cargados: " + selected.size()
                    + (split == null ? " (todos)" : " (set " + split + ")"));

            return selected;
        }
    }
}