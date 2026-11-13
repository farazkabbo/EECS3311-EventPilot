package com.eventpilot.agent.workflow;

import com.eventpilot.agent.AgentResult;
import com.eventpilot.agent.AgentTask;
import com.eventpilot.agent.llm.LLMRequest;
import com.eventpilot.application.proposal.PlanProposal;
import com.eventpilot.application.proposal.Warning;
import com.eventpilot.domain.Event;
import java.util.List;

/** F05 - assigns speakers to sessions they are available for, testing drafts with the tools. */
public class SpeakerSchedulingWorkflow extends PlanningWorkflow<PlanProposal> {

    public SpeakerSchedulingWorkflow(AgentServices services, Event event) {
        super(services, event);
        if (event.speakers().isEmpty() || event.agenda().sessions().isEmpty()) {
            throw new WorkflowException("Speaker scheduling needs at least one speaker and one session "
                    + "(found " + event.speakers().size() + " speakers, " + event.agenda().sessions().size() + " sessions).");
        }
    }

    @Override
    protected String gatherContext() {
        return PromptContext.of("speaker_scheduling", event).withAgenda(event).withSpeakers(event).render()
                + "\n" + super.gatherContext();
    }

    @Override
    protected AgentTask buildTask(String context) {
        String system = Prompts.system("""
                Task: assign speakers to sessions (keynote, session, workshop, panel items).
                - A speaker may only be scheduled inside their availability windows and never in two items at once.
                - Prefer matching a speaker's topic to the session title. You may move a session to another time
                  (op "move") if that is the only way to fit an available speaker, but keep moves minimal.
                - Call speaker_availability first, then test your full draft with conflict_check until there is no
                  HIGH conflict. If some speakers cannot be placed, say why in the summary.
                Reply format:
                {"summary": "...", "changes": [
                  {"op": "assign_speaker", "item_id": "a2", "speaker_id": "s1", "reason": "..."},
                  {"op": "move", "item_id": "a3", "start": "14:00", "end": "14:45", "reason": "..."}]}
                """);
        return new AgentTask("speaker_scheduling", system, context + "\n" + Prompts.request("Schedule the speakers."),
                List.of("speaker_availability", "conflict_check"), LLMRequest.Tier.MAIN, 4000);
    }

    @Override
    protected ResponseParser<PlanProposal> createParser() {
        return new ProposalParser(services.mapper(), event, "Schedule speakers");
    }

    @Override
    protected List<Warning> validate(PlanProposal proposal, AgentResult run) {
        return services.guardrails().validateProposal(event, proposal);
    }
}
