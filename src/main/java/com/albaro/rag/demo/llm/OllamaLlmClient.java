package com.albaro.rag.demo.llm;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "rag.llm.provider", havingValue = "ollama", matchIfMissing = true)
public class OllamaLlmClient implements LlmClient {

    private final ChatModel chatModel;

    public OllamaLlmClient(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public LlmResult generate(String systemPrompt, String userPrompt) {

        ChatResponse response = chatModel.chat(
                SystemMessage.from(systemPrompt),
                UserMessage.from(userPrompt)
        );

        // Ollama también informa los tokens, aunque no se cobren.
        TokenUsage usage = response.tokenUsage();

        return new LlmResult(
                response.aiMessage().text(),
                usage != null && usage.inputTokenCount() != null ? usage.inputTokenCount() : 0,
                usage != null && usage.outputTokenCount() != null ? usage.outputTokenCount() : 0
        );
    }
}