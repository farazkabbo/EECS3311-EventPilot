package com.eventpilot.agent.llm;

/** The AI could not be reached (after retries) or refused; the user gets a manual alternative. */
public class LLMUnavailableException extends RuntimeException {

    public LLMUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
