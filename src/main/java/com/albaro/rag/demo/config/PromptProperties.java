package com.albaro.rag.demo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "rag.prompt")
public class PromptProperties {

    private String systemPath;
    private String userPath;
    private String rewriterSystemPath;
    private String rerankerSystemPath;
    private String rerankerUserPath;
}