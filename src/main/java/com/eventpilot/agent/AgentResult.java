package com.eventpilot.agent;

import com.eventpilot.agent.llm.ChatMessage;
import com.eventpilot.agent.tools.ToolContext;
import java.util.List;

/** The outcome of an agent run: the final answer, the tool trace and the conversation (for follow-ups). */
public record AgentResult(String finalText, List<TraceEntry> trace, List<ChatMessage> conversation,
                          boolean stepLimitReached, AgentTask task, ToolContext toolContext) {

    /** One tool call made during the run (shown to the user as the agent's "reasoning trail"). */
    public record TraceEntry(int step, String tool, String arguments, String result, boolean error) {
        @Override
        public String toString() {
            String r = result.length() > 160 ? result.substring(0, 157) + "..." : result;
            return "step " + step + ": " + tool + "(" + arguments + ")" + (error ? " -> ERROR " : " -> ") + r;
        }
    }
}
