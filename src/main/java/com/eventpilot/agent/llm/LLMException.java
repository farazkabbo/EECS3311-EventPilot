package com.eventpilot.agent.llm;

/** A failed model call. {@code retryable} is true for network errors, rate limits and server errors. */
public class LLMException extends Exception {

    private final boolean retryable;

    public LLMException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public LLMException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
