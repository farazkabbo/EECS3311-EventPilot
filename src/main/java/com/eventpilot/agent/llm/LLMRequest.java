package com.eventpilot.agent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** A provider-neutral chat request. */
public record LLMRequest(String system, List<ChatMessage> messages, List<ToolSpec> tools, Tier tier, int maxTokens) {

    /** MAIN = planning model with tools; LIGHT = cheaper model for FAQ answers and invitation text. */
    public enum Tier { MAIN, LIGHT }

    public LLMRequest {
        messages = List.copyOf(messages);
        tools = tools == null ? List.of() : List.copyOf(tools);
    }

    /** A tool the model may call: name, description and JSON schema of its arguments. */
    public record ToolSpec(String name, String description, JsonNode inputSchema) {
    }
}
