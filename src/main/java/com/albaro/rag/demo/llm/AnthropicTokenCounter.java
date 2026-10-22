package com.albaro.rag.demo.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.MessageCountTokensParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "rag.llm.provider", havingValue = "claude")
public class AnthropicTokenCounter implements TokenCounter {

    private final AnthropicClient client;
    private final String model;

    public AnthropicTokenCounter(
            AnthropicClient client,
            @Value("${anthropic.model}") String model) {
        this.client = client;
        this.model = model;
    }

    @Override
    public long countTokens(String prompt){
        return client.messages().countTokens(
                MessageCountTokensParams.builder()
                        .model(model)
                        .addUserMessage(prompt)
                        .build()
        ).inputTokens();
    }

}
