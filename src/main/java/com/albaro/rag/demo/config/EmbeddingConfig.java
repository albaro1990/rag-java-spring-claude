package com.albaro.rag.demo.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmbeddingConfig {

//    @Bean
//    public EmbeddingModel embeddingModel() {
//        return new AllMiniLmL6V2EmbeddingModel();
//    }

    private final OllamaProperties ollamaProperties;

    public EmbeddingConfig(OllamaProperties ollamaProperties) {
        this.ollamaProperties = ollamaProperties;
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        return OllamaEmbeddingModel.builder()
                .baseUrl(ollamaProperties.getBaseUrl())
                .modelName(ollamaProperties.getEmbeddingModel())
                .timeout(ollamaProperties.getTimeout())
                .build();
    }
}