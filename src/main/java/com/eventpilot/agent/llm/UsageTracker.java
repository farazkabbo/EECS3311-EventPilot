package com.eventpilot.agent.llm;

import java.util.LinkedHashMap;
import java.util.Map;

/** Counts calls and tokens per model and estimates cost (USD per million tokens). */
public class UsageTracker {

    public record ModelUsage(String model, long calls, long inputTokens, long outputTokens, double estimatedCost) {
    }

    private static final Map<String, double[]> PRICES = Map.of(
            "claude-sonnet-5-5", new double[] {2.00, 10.00},
            "claude-haiku-5-5", new double[] {0.10, 0.50},
            "claude-haiku-4-5", new double[] {1.00, 5.00},
            "claude-opus-5-5", new double[] {4.00, 20.00});

    private final Map<String, long[]> totals = new LinkedHashMap<>();

    public synchronized void record(LLMResponse response) {
        long[] t = totals.computeIfAbsent(response.model() == null ? "unknown" : response.model(), k -> new long[3]);
        t[0]++;
        t[1] += response.inputTokens();
        t[2] += response.outputTokens();
    }

    public synchronized Map<String, ModelUsage> snapshot() {
        Map<String, ModelUsage> out = new LinkedHashMap<>();
        totals.forEach((model, t) -> {
            double[] p = PRICES.getOrDefault(model, new double[] {0, 0});
            double cost = t[1] / 1_000_000.0 * p[0] + t[2] / 1_000_000.0 * p[1];
            out.put(model, new ModelUsage(model, t[0], t[1], t[2], cost));
        });
        return out;
    }

    public synchronized double totalCost() {
        return snapshot().values().stream().mapToDouble(ModelUsage::estimatedCost).sum();
    }
}
