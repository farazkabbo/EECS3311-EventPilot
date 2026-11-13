package com.eventpilot.agent.workflow;

import com.eventpilot.agent.AgentResult;
import com.eventpilot.agent.AgentTask;
import com.eventpilot.agent.llm.LLMRequest;
import com.eventpilot.application.proposal.PlanProposal;
import com.eventpilot.application.proposal.Warning;
import com.eventpilot.domain.Conflict;
import com.eventpilot.domain.Event;
import java.util.ArrayList;
import java.util.List;

/**
 * F06 - detection is deterministic; this workflow asks the agent for a minimal
 * fix and overrides {@link #validate} to test the fix on a copy of the event.
 */
public class ConflictResolutionWorkflow extends PlanningWorkflow<PlanProposal> {

    private final List<Conflict> conflicts;

    public ConflictResolutionWorkflow(AgentServices services, Event event) {
        super(services, event);
        this.conflicts = services.conflicts().detect(event);
        if (conflicts.isEmpty()) {
            throw new WorkflowException("No conflicts found - nothing to resolve.");
        }
    }

    @Override
    protected String gatherContext() {
        return PromptContext.of("conflict_resolution", event).withAgenda(event).withSpeakers(event)
                .put("conflicts", conflicts).render() + "\n" + super.gatherContext();
    }

    @Override
    protected AgentTask buildTask(String context) {
        String system = Prompts.system("""
                Task: resolve the listed scheduling conflicts with as few changes as possible.
                - Prefer moving an item to a free time or room over removing it. Keep items inside event hours.
                - Use conflict_check to test your complete set of changes; it must report no HIGH conflicts.
                Reply format:
                {"summary": "...", "changes": [
                  {"op": "move", "item_id": "a4", "start": "11:00", "end": "11:45", "room": "Room B", "reason": "..."},
                  {"op": "assign_speaker", "item_id": "a5", "speaker_id": "s2", "reason": "..."}]}
                """);
        return new AgentTask("conflict_resolution", system, context + "\n" + Prompts.request("Fix the conflicts."),
                List.of("conflict_check", "speaker_availability"), LLMRequest.Tier.MAIN, 4000);
    }

    @Override
    protected ResponseParser<PlanProposal> createParser() {
        return new ProposalParser(services.mapper(), event, "Resolve conflicts");
    }

    /** Applies the fix to a deep copy and re-runs detection: remaining serious conflicts block approval. */
    @Override
    protected List<Warning> validate(PlanProposal proposal, AgentResult run) {
        List<Warning> warnings = new ArrayList<>(services.guardrails().validateProposal(event, proposal));
        Event trial = event.deepCopy();
        try {
            proposal.selectAll().execute(trial);
        } catch (RuntimeException e) {
            return warnings; // already reported by the guardrails
        }
        for (Conflict c : services.conflicts().detect(trial)) {
            if (c.isBlocking()) {
                warnings.add(Warning.high("Still unresolved: " + c.message()));
            }
        }
        return warnings;
    }
}
