package com.eventpilot.agent.workflow;

import com.eventpilot.agent.AgentResult;
import com.eventpilot.agent.AgentTask;
import com.eventpilot.agent.llm.LLMRequest;
import com.eventpilot.application.command.RemoveAgendaItemCommand;
import com.eventpilot.application.proposal.PlanProposal;
import com.eventpilot.application.proposal.Warning;
import com.eventpilot.domain.AgendaItem;
import com.eventpilot.domain.Event;
import java.util.ArrayList;
import java.util.List;

/** F04 - the agent designs a full agenda inside the event hours and checks it with conflict_check. */
public class AgendaGenerationWorkflow extends PlanningWorkflow<PlanProposal> {

    private final AgendaPreferences preferences;

    public AgendaGenerationWorkflow(AgentServices services, Event event, AgendaPreferences preferences) {
        super(services, event);
        this.preferences = preferences;
    }

    @Override
    protected String gatherContext() {
        PromptContext ctx = PromptContext.of("agenda_generation", event).withSpeakers(event).put("preferences", preferences);
        if (!preferences.replaceExisting()) {
            ctx.withAgenda(event);
        }
        return ctx.render() + "\n" + super.gatherContext();
    }

    @Override
    protected AgentTask buildTask(String context) {
        String system = Prompts.system("""
                Task: design the event agenda (sessions, keynote, breaks, meals) within the event start and end.
                - Respect the preferences: number of parallel tracks (use different rooms), session length,
                  break length, lunch time and topics. Open with a welcome/keynote; include coffee breaks and lunch
                  for events longer than 4 hours.
                - Assign speakers only when their availability allows (see speakers in the context).
                - Before answering, call conflict_check with your draft "changes" and fix every HIGH conflict.
                - When extending an existing agenda, only add items that fit around the existing ones.
                Reply format (times as HH:MM on the event day):
                {"summary": "...", "changes": [
                  {"op": "add", "title": "Opening keynote", "type": "keynote|session|workshop|panel|break|meal|networking",
                   "start": "09:00", "end": "09:45", "room": "Main Hall", "speaker_id": "s1", "expected_audience": 120,
                   "reason": "..."}]}
                """);
        return new AgentTask("agenda_generation", system,
                context + "\n" + Prompts.request("Generate the agenda."),
                List.of("conflict_check", "speaker_availability"), LLMRequest.Tier.MAIN, 6000);
    }

    @Override
    protected ResponseParser<PlanProposal> createParser() {
        Event base = baseEvent();
        ProposalParser inner = new ProposalParser(services.mapper(), base, "Generate agenda");
        return text -> {
            PlanProposal parsed = inner.parse(text);
            if (parsed.isQuestion() || !preferences.replaceExisting() || event.agenda().isEmpty()) {
                return parsed;
            }
            List<PlanProposal.Change> changes = new ArrayList<>();
            for (AgendaItem old : event.agenda().items()) {
                changes.add(new PlanProposal.Change(new RemoveAgendaItemCommand(old.id(), old.title()),
                        "replaced by the new agenda"));
            }
            changes.addAll(parsed.changes());
            return new PlanProposal(event.id(), "Generate agenda (replace)", parsed.summary(), changes, false);
        };
    }

    @Override
    protected Event toolEvent() {
        return baseEvent();
    }

    /** When replacing, new items are mapped against an empty agenda so their checks ignore the old items. */
    private Event baseEvent() {
        if (!preferences.replaceExisting()) {
            return event;
        }
        Event copy = event.deepCopy();
        copy.agenda().clear();
        return copy;
    }

    @Override
    protected List<Warning> validate(PlanProposal proposal, AgentResult run) {
        return services.guardrails().validateProposal(event, proposal);
    }
}
