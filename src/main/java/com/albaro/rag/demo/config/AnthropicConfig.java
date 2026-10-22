package com.albaro.rag.demo.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class AnthropicConfig {

    @Bean
    @ConditionalOnProperty(name = "rag.llm.provider", havingValue = "claude")
    public AnthropicClient anthropicClient(@Value("${anthropic.api-key}") String apiKey,
                                      @Value("${anthropic.timeout:30s}") Duration timeout,
                                      @Value("${anthropic.max-retries:3}") int maxRetries) {

        return AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(timeout)
                .maxRetries(maxRetries)
                .build();

    }
}
