package com.eventpilot.agent.llm;

import java.util.List;

/** A provider-neutral reply: final text and/or requested tool calls, plus token usage. */
public record LLMResponse(String text, List<ChatMessage.ToolCall> toolCalls, String stopReason,
                          long inputTokens, long outputTokens, String model) {

    public LLMResponse {
        text = text == null ? "" : text;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public static LLMResponse text(String text) {
        return new LLMResponse(text, List.of(), "end_turn", 0, 0, "mock");
    }

    public static LLMResponse tools(List<ChatMessage.ToolCall> calls) {
        return new LLMResponse("", calls, "tool_use", 0, 0, "mock");
    }

    public boolean wantsTools() {
        return !toolCalls.isEmpty();
    }
}
