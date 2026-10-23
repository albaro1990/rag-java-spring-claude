package com.albaro.rag.demo.llm;

import com.albaro.rag.demo.config.LlmPricingProperties;
import com.albaro.rag.demo.config.ModelPrice;
import org.springframework.stereotype.Service;

@Service
public class CostCalculator {


    private final LlmPricingProperties pricingProperties;

    public CostCalculator(LlmPricingProperties pricingProperties) {
        this.pricingProperties = pricingProperties;
    }

    public double calculateCost(String model, long inputTokens, long outputTokens) {
        ModelPrice price = pricingProperties.getPricing().get(model);
        if (price == null) {
            return 0.0;   // modelo sin precio (Ollama): gratis
        }

        return  (( inputTokens /1000000.0) * price.input() + (outputTokens/1000000.0) * price.output() );

    }
}
