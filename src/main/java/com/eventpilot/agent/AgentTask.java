package com.eventpilot.agent;

import com.eventpilot.agent.llm.LLMRequest;
import java.util.List;

/** What one workflow asks the agent to do: instructions, the task prompt and the tools it may use. */
public record AgentTask(String workflowId, String systemPrompt, String userPrompt, List<String> allowedTools,
                        LLMRequest.Tier tier, int maxTokens) {

    public AgentTask {
        allowedTools = allowedTools == null ? List.of() : List.copyOf(allowedTools);
    }
}
