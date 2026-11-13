package com.eventpilot.agent.llm;

/**
 * Strategy pattern - the "Strategy" interface. One implementation per model
 * back-end (Claude, Ollama, scripted mock). Workflows never know which one runs.
 * The concrete providers are also Adapters from a vendor API to this interface.
 */
public interface LLMProvider {

    String name();

    /** The model id used for a tier (for display and usage tracking). */
    String modelFor(LLMRequest.Tier tier);

    LLMResponse chat(LLMRequest request) throws LLMException;
}
