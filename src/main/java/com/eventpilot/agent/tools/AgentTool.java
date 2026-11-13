package com.eventpilot.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A tool the LLM can call. Tools are read-only: they wrap deterministic domain
 * services and work on a copy of the event, so the agent can gather facts but
 * never change the event directly.
 */
public interface AgentTool {

    String name();

    String description();

    /** JSON schema of the arguments (type object, properties, required). */
    JsonNode inputSchema();

    /** Runs the tool with already-validated arguments and returns a JSON result. */
    String execute(JsonNode arguments, ToolContext context);
}
