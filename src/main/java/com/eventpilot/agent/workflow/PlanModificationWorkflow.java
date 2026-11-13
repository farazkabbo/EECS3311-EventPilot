package com.eventpilot.agent.workflow;

import com.eventpilot.agent.AgentResult;
import com.eventpilot.agent.AgentTask;
import com.eventpilot.agent.llm.LLMRequest;
import com.eventpilot.application.proposal.PlanProposal;
import com.eventpilot.application.proposal.Warning;
import com.eventpilot.domain.Event;
import java.util.List;

/**
 * F12 - turns a plain-language instruction ("move the keynote to 2 pm and add a
 * 15-minute break after it") into operations on real agenda items. Ambiguous
 * requests come back as a question; conversation memory resolves "it".
 */
public class PlanModificationWorkflow extends PlanningWorkflow<PlanProposal> {

    private final String instruction;

    public PlanModificationWorkflow(AgentServices services, Event event, String instruction) {
        super(services, event);
        if (instruction == null || instruction.isBlank()) {
            throw new WorkflowException("Please describe the change.");
        }
        this.instruction = instruction.trim();
    }

    @Override
    protected String gatherContext() {
        return PromptContext.of("plan_modification", event).withAgenda(event).withSpeakers(event).render()
                + "\n" + super.gatherContext();
    }

    @Override
    protected AgentTask buildTask(String context) {
        String system = Prompts.system("""
                Task: turn the organizer's change request into agenda operations on the existing items.
                - Refer to existing items by their "id". Allowed operations: add, move, remove, rename, assign_speaker.
                - "after X" means starting when X ends; keep durations unless told otherwise; times as HH:MM.
                - If the request is ambiguous (e.g. two items match "the talk") or impossible, do not guess:
                  reply {"question": "..."} asking which one / what to do.
                - Use the recent conversation to resolve words like "it" or "that session".
                - Test your operations with conflict_check; mention any unavoidable conflict in the summary.
                Reply format:
                {"summary": "...", "changes": [
                  {"op": "move", "item_id": "a1", "start": "14:00", "reason": "..."},
                  {"op": "add", "title": "Break", "type": "break", "start": "14:45", "end": "15:00", "room": "Main Hall"},
                  {"op": "remove", "item_id": "a7"}, {"op": "rename", "item_id": "a2", "title": "..."},
                  {"op": "assign_speaker", "item_id": "a3", "speaker_id": "s2"}]}
                or {"question": "..."}
                """);
        return new AgentTask("plan_modification", system, context + "\n" + Prompts.request(instruction),
                List.of("conflict_check", "speaker_availability"), LLMRequest.Tier.MAIN, 3000);
    }

    @Override
    protected ResponseParser<PlanProposal> createParser() {
        return new ProposalParser(services.mapper(), event, "Change: " + shorten(instruction));
    }

    @Override
    protected List<Warning> validate(PlanProposal proposal, AgentResult run) {
        return services.guardrails().validateProposal(event, proposal);
    }

    private static String shorten(String s) {
        return s.length() > 60 ? s.substring(0, 57) + "..." : s;
    }
}
