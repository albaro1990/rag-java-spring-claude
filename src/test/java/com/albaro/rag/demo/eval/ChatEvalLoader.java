package com.albaro.rag.demo.eval;

import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

final class ChatEvalLoader {

    private ChatEvalLoader() {
    }

    // -Deval.split=ajuste o -Deval.split=confirmacion filtra por conjunto.
    static List<ChatEvalConversation> load() throws IOException {
        JsonMapper mapper = new JsonMapper();
        try (InputStream input = new ClassPathResource("eval/chat-cases.json").getInputStream()) {
            ChatEvalConversation[] all = mapper.readValue(input, ChatEvalConversation[].class);
            String split = System.getProperty("eval.split");
            List<ChatEvalConversation> selected = Arrays.stream(all)
                    .filter(c -> split == null || split.equals(c.split()))
                    .toList();
            System.out.println("Conversaciones cargadas: " + selected.size()
                    + (split == null ? " (todas)" : " (set " + split + ")"));
            return selected;
        }
    }
}
