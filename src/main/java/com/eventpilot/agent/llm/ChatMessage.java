package com.eventpilot.agent.llm;

import java.util.List;

/** One message of the provider-neutral conversation the agent keeps. */
public record ChatMessage(Role role, String text, List<ToolCall> toolCalls, List<ToolResult> toolResults) {

    public enum Role { USER, ASSISTANT, TOOL_RESULTS }

    public ChatMessage {
        text = text == null ? "" : text;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        toolResults = toolResults == null ? List.of() : List.copyOf(toolResults);
    }

    public static ChatMessage user(String text) {
        return new ChatMessage(Role.USER, text, null, null);
    }

    public static ChatMessage assistant(String text, List<ToolCall> calls) {
        return new ChatMessage(Role.ASSISTANT, text, calls, null);
    }

    public static ChatMessage toolResults(List<ToolResult> results) {
        return new ChatMessage(Role.TOOL_RESULTS, null, null, results);
    }

    /** A tool call requested by the model; arguments are a JSON object string. */
    public record ToolCall(String id, String name, String argumentsJson) {
    }

    /** The result of running a tool, sent back to the model. */
    public record ToolResult(String toolCallId, String name, String content, boolean isError) {
    }
}
