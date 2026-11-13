package com.eventpilot.agent.llm;

import java.util.Objects;

/**
 * Strategy pattern - the "Context". Holds the current {@link LLMProvider} (which
 * can be replaced at run time) and adds what every call needs: retries with
 * back-off for temporary failures and token-usage tracking.
 */
public class LLMClient {

    public static final int MAX_ATTEMPTS = 3;

    private volatile LLMProvider provider;
    private final UsageTracker usage = new UsageTracker();
    private final long backoffMillis;

    public LLMClient(LLMProvider provider) {
        this(provider, 800);
    }

    public LLMClient(LLMProvider provider, long backoffMillis) {
        this.provider = Objects.requireNonNull(provider);
        this.backoffMillis = backoffMillis;
    }

    public LLMResponse complete(LLMRequest request) {
        LLMException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                LLMResponse response = provider.chat(request);
                usage.record(response);
                return response;
            } catch (LLMException e) {
                last = e;
                if (!e.isRetryable()) {
                    break;
                }
                sleep(backoffMillis * attempt);
            }
        }
        throw new LLMUnavailableException("The AI service (" + provider.name() + ") is unavailable: "
                + (last == null ? "unknown error" : last.getMessage()), last);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public LLMProvider provider() {
        return provider;
    }

    public void setProvider(LLMProvider provider) {
        this.provider = Objects.requireNonNull(provider);
    }

    public UsageTracker usage() {
        return usage;
    }
}
