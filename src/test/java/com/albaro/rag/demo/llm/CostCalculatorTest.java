package com.albaro.rag.demo.llm;

import com.albaro.rag.demo.config.LlmPricingProperties;
import com.albaro.rag.demo.config.ModelPrice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CostCalculatorTest {

    private CostCalculator calculator;

    @BeforeEach
    void setUp() {
        LlmPricingProperties properties = new LlmPricingProperties();
        properties.setPricing(Map.of(
                "claude-haiku-5-5", new ModelPrice(0.10, 0.50)
        ));
        calculator = new CostCalculator(properties);
    }

    @Test
    void calculatesInputAndOutputCost() {
        // Arrange
        String model = "claude-haiku-5-5";
        int inputTokens = 1000;
        int outputTokens = 300;

        // Act
        double cost = calculator.calculateCost(
                model,
                inputTokens,
                outputTokens
        );

        // Assert
        assertEquals(0.00025, cost, 0.000000001);
    }

    @Test
    void returnsZeroForModelWithoutPrice() {
        // "qwen2.5:3b" debe dar 0.0
        // Arrange
        String model = "qwen2.5:3b";
        int inputTokens = 1000;
        int outputTokens = 300;

        // Act
        double cost = calculator.calculateCost(
                model,
                inputTokens,
                outputTokens
        );

        // Assert
        assertEquals(0.0, cost, 0.0);
    }
}