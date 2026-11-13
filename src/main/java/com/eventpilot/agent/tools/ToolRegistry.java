package com.eventpilot.agent.tools;

import com.eventpilot.agent.llm.ChatMessage;
import com.eventpilot.agent.llm.LLMRequest;
import com.eventpilot.infra.Json;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds the agent's tools. Before running a tool it checks the arguments
 * against the tool's JSON schema; invalid calls are not run - the error goes
 * back to the LLM so it can correct itself.
 */
public class ToolRegistry {

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    public ToolRegistry register(AgentTool tool) {
        tools.put(tool.name(), tool);
        return this;
    }

    public List<LLMRequest.ToolSpec> specs(Collection<String> allowed) {
        List<LLMRequest.ToolSpec> specs = new ArrayList<>();
        for (String name : allowed) {
            AgentTool tool = tools.get(name);
            if (tool == null) {
                throw new IllegalArgumentException("No tool named " + name);
            }
            specs.add(new LLMRequest.ToolSpec(tool.name(), tool.description(), tool.inputSchema()));
        }
        return specs;
    }

    public List<String> names() {
        return List.copyOf(tools.keySet());
    }

    public ChatMessage.ToolResult execute(ChatMessage.ToolCall call, Collection<String> allowed, ToolContext context) {
        AgentTool tool = tools.get(call.name());
        if (tool == null || !allowed.contains(call.name())) {
            return error(call, "Tool \"" + call.name() + "\" is not available. Available tools: " + allowed);
        }
        JsonNode args;
        try {
            args = Json.tree(call.argumentsJson() == null || call.argumentsJson().isBlank() ? "{}" : call.argumentsJson());
        } catch (IllegalArgumentException e) {
            return error(call, "Arguments are not valid JSON: " + e.getMessage());
        }
        List<String> problems = SchemaValidator.validate(args, tool.inputSchema());
        if (!problems.isEmpty()) {
            return error(call, "Invalid arguments - " + String.join("; ", problems)
                    + ". Expected schema: " + tool.inputSchema());
        }
        try {
            return new ChatMessage.ToolResult(call.id(), call.name(), tool.execute(args, context), false);
        } catch (RuntimeException e) {
            return error(call, "Tool failed: " + e.getMessage());
        }
    }

    private static ChatMessage.ToolResult error(ChatMessage.ToolCall call, String message) {
        return new ChatMessage.ToolResult(call.id(), call.name(), Json.write(Map.of("error", message)), true);
    }
}
