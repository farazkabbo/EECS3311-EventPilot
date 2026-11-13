package com.eventpilot.agent.workflow;

import com.eventpilot.agent.AgentResult;
import com.eventpilot.application.proposal.Warning;
import java.util.List;

/** The validated output of a workflow, its guardrail warnings and the agent's tool trace. */
public record WorkflowResult<T>(T value, List<Warning> warnings, List<AgentResult.TraceEntry> trace) {

    public WorkflowResult {
        warnings = List.copyOf(warnings);
        trace = List.copyOf(trace);
    }

    public boolean hasBlocking() {
        return warnings.stream().anyMatch(Warning::isBlocking);
    }

    public List<String> traceLines() {
        return trace.stream().map(AgentResult.TraceEntry::toString).toList();
    }
}
