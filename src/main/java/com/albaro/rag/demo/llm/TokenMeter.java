package com.albaro.rag.demo.llm;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class TokenMeter {

    private final CostCalculator costCalculator;
    private final MeterRegistry meterRegistry;

    public TokenMeter(CostCalculator costCalculator, MeterRegistry meterRegistry) {
        this.costCalculator = costCalculator;
        this.meterRegistry = meterRegistry;
    }

    public void record(String operation, LlmResult result, long latencyMs){

        double cost = costCalculator.calculateCost(result.model(), result.inputTokens(), result.outputTokens());

        Counter.builder("llm.tokens")
                .tag("operation", operation)
                .tag("model", result.model())
                .tag("type", "input")
                .register(meterRegistry)
                .increment(result.inputTokens());

        Counter.builder("llm.tokens")
                .tag("operation", operation)
                .tag("model", result.model())
                .tag("type", "output")
                .register(meterRegistry)
                .increment(result.outputTokens());


        Counter.builder("llm.cost.usd")
                .tag("operation", operation)
                .tag("model", result.model())
                .register(meterRegistry)
                .increment(cost);



        Timer.builder("llm.latency")
                .tag("operation", operation)
                .register(meterRegistry)
                .record(latencyMs, TimeUnit.MILLISECONDS);

        log.info("LLM call operation={} model={} inputTokens={} outputTokens={} costUsd={} latencyMs={}",
                operation, result.model(), result.inputTokens(), result.outputTokens(), cost, latencyMs);

    }
}
