package com.albaro.rag.demo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@Getter
@Setter
@ConfigurationProperties(prefix = "llm")
public class LlmPricingProperties {

    private  Map<String, ModelPrice> pricing;
}
