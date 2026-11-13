package com.eventpilot.agent;

import com.eventpilot.agent.llm.ChatMessage;
import com.eventpilot.agent.llm.LLMClient;
import com.eventpilot.agent.llm.LLMRequest;
import com.eventpilot.agent.llm.LLMResponse;
import com.eventpilot.agent.tools.ToolContext;
import com.eventpilot.agent.tools.ToolRegistry;
import java.util.ArrayList;
import java.util.List;

/**
 * The agent loop: send the conversation to the LLM; if it asks for tools, run
 * them through the {@link ToolRegistry} and send the results back; repeat until
 * the LLM gives a final answer or {@link #MAX_STEPS} is reached.
 */
public class AgentOrchestrator {

    public static final int MAX_STEPS = 8;

    private final LLMClient llm;
    private final ToolRegistry tools;

    public AgentOrchestrator(LLMClient llm, ToolRegistry tools) {
        this.llm = llm;
        this.tools = tools;
    }

    public AgentResult run(AgentTask task, ToolContext context) {
        List<ChatMessage> conversation = new ArrayList<>();
        conversation.add(ChatMessage.user(task.userPrompt()));
        return loop(task, context, conversation, new ArrayList<>());
    }

    /** Continues a previous run with a new user message (used to ask the LLM to fix its answer). */
    public AgentResult followUp(AgentResult previous, String message) {
        List<ChatMessage> conversation = new ArrayList<>(previous.conversation());
        conversation.add(ChatMessage.user(message));
        return loop(previous.task(), previous.toolContext(), conversation, new ArrayList<>(previous.trace()));
    }

    private AgentResult loop(AgentTask task, ToolContext context, List<ChatMessage> conversation,
                             List<AgentResult.TraceEntry> trace) {
        List<LLMRequest.ToolSpec> specs = tools.specs(task.allowedTools());
        for (int step = 1; step <= MAX_STEPS; step++) {
            // On the last step tools are withheld so the model must answer.
            List<LLMRequest.ToolSpec> offered = step == MAX_STEPS ? List.of() : specs;
            LLMResponse response = llm.complete(new LLMRequest(task.systemPrompt(), conversation, offered,
                    task.tier(), task.maxTokens()));
            conversation.add(ChatMessage.assistant(response.text(), response.toolCalls()));
            if (!response.wantsTools()) {
                return new AgentResult(response.text(), trace, conversation, false, task, context);
            }
            List<ChatMessage.ToolResult> results = new ArrayList<>();
            for (ChatMessage.ToolCall call : response.toolCalls()) {
                ChatMessage.ToolResult result = tools.execute(call, task.allowedTools(), context);
                results.add(result);
                trace.add(new AgentResult.TraceEntry(trace.size() + 1, call.name(), call.argumentsJson(),
                        result.content(), result.isError()));
            }
            conversation.add(ChatMessage.toolResults(results));
        }
        return new AgentResult("", trace, conversation, true, task, context);
    }
}
