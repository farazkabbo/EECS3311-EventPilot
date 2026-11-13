package com.eventpilot.agent.workflow;

import com.eventpilot.agent.AgentResult;
import com.eventpilot.agent.AgentTask;
import com.eventpilot.agent.llm.LLMRequest;
import com.eventpilot.application.command.SetVenueCommand;
import com.eventpilot.application.proposal.PlanProposal;
import com.eventpilot.application.proposal.Warning;
import com.eventpilot.domain.Event;
import com.eventpilot.domain.Venue;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** F03 - the agent searches venues with tools, ranks the top 3 and explains trade-offs. */
public class VenueRecommendationWorkflow extends PlanningWorkflow<PlanProposal> {

    private final String extraNeeds;

    public VenueRecommendationWorkflow(AgentServices services, Event event, String extraNeeds) {
        super(services, event);
        this.extraNeeds = extraNeeds == null ? "" : extraNeeds;
    }

    @Override
    protected String gatherContext() {
        return PromptContext.of("venue_recommendation", event).withBudget(event)
                .put("extra_needs", extraNeeds).render() + "\n" + super.gatherContext();
    }

    @Override
    protected AgentTask buildTask(String context) {
        String system = Prompts.system("""
                Task: recommend up to 3 venues for this event.
                Steps: (1) call venue_search with the event city and expected attendance (add accessibility or
                features if the organizer needs them); (2) call budget_calculator (operation "remaining",
                category "venue") to know the venue budget; (3) rank the venues returned by the tool.
                If nothing matches you may relax ONLY max_price_per_day or district, and must say so in "relaxed".
                Only recommend venues the tool returned. Score each from 0 to 100.
                Reply format:
                {"summary": "...", "relaxed": "" ,
                 "recommendations": [{"venue_id": "TO-002", "score": 87, "reason": "...", "tradeoffs": "..."}]}
                """);
        return new AgentTask("venue_recommendation", system,
                context + "\n" + Prompts.request("Recommend venues." + (extraNeeds.isBlank() ? "" : " Extra needs: " + extraNeeds)),
                List.of("venue_search", "budget_calculator"), LLMRequest.Tier.MAIN, 3000);
    }

    @Override
    protected ResponseParser<PlanProposal> createParser() {
        return text -> {
            JsonNode root = JsonAnswer.parse(text);
            List<PlanProposal.Change> changes = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            for (JsonNode rec : root.path("recommendations")) {
                String id = rec.path("venue_id").asText();
                Venue venue = services.venues().findById(id).orElse(null);
                if (venue == null) {
                    errors.add("venue_id \"" + id + "\" does not exist in the catalog");
                    continue;
                }
                String reason = "score " + rec.path("score").asInt() + "/100 - " + rec.path("reason").asText("")
                        + (rec.path("tradeoffs").asText("").isBlank() ? "" : " Trade-offs: " + rec.get("tradeoffs").asText());
                changes.add(new PlanProposal.Change(new SetVenueCommand(venue, reason), reason));
                if (changes.size() == 3) {
                    break;
                }
            }
            if (!errors.isEmpty()) {
                throw new ResponseParser.ParseException(String.join("; ", errors) + ". Use only venue ids returned by venue_search.");
            }
            String summary = root.path("summary").asText("");
            String relaxed = root.path("relaxed").asText("");
            if (changes.isEmpty()) {
                throw new com.eventpilot.agent.workflow.WorkflowException(
                        "No suitable venue was found. " + summary + " Try a larger budget, another city or fewer requirements.");
            }
            return new PlanProposal(event.id(), "Choose a venue",
                    summary + (relaxed.isBlank() ? "" : " (Relaxed: " + relaxed + ")"), changes, true);
        };
    }

    @Override
    protected List<Warning> validate(PlanProposal proposal, AgentResult run) {
        List<Warning> warnings = new ArrayList<>(services.guardrails().validateProposal(event, proposal));
        Set<String> returned = run.toolContext().returnedVenueIds();
        for (PlanProposal.Change change : proposal.changes()) {
            Venue v = ((SetVenueCommand) change.command()).venue();
            if (!returned.contains(v.id())) {
                warnings.add(Warning.high(v.name() + " was not returned by venue_search (not verified by a tool search)."));
            }
        }
        return warnings;
    }
}
