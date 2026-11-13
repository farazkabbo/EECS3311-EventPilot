package com.eventpilot.agent.llm;

import com.eventpilot.infra.AppConfig;
import java.util.function.Function;

/** Chooses the concrete strategy from configuration, so switching models needs no code change. */
public final class LLMProviderFactory {

    private LLMProviderFactory() {
    }

    /**
     * @param offlineBehaviour the answering logic the mock uses when it has no script
     *                         (the offline planner in the real application)
     */
    public static LLMProvider create(AppConfig config, Function<LLMRequest, LLMResponse> offlineBehaviour) {
        return switch (config.llmProvider()) {
            case "claude" -> new ClaudeProvider(config.anthropicApiKey().orElseThrow(), config.mainModel(),
                    config.lightModel());
            case "ollama" -> new OllamaProvider(config.ollamaUrl(), config.ollamaModel());
            case "mock" -> new MockLLMProvider(offlineBehaviour);
            default -> throw new IllegalArgumentException("Unknown LLM_PROVIDER '" + config.llmProvider()
                    + "' (use claude, ollama or mock)");
        };
    }
}
