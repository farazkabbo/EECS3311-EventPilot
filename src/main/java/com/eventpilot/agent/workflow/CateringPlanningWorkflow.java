package com.eventpilot.agent.workflow;

import com.eventpilot.agent.AgentResult;
import com.eventpilot.agent.AgentTask;
import com.eventpilot.agent.TimeParser;
import com.eventpilot.agent.llm.LLMRequest;
import com.eventpilot.application.command.SetCateringPlanCommand;
import com.eventpilot.application.proposal.PlanProposal;
import com.eventpilot.application.proposal.Warning;
import com.eventpilot.domain.AgendaItem;
import com.eventpilot.domain.CateringPlan;
import com.eventpilot.domain.Event;
import com.eventpilot.domain.service.CateringCalculator;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * F08 - code computes headcount and dietary counts (given to the agent as facts);
 * the agent designs meals and menus; code computes portions and cost and checks
 * that every dietary need is covered.
 */
public class CateringPlanningWorkflow extends PlanningWorkflow<PlanProposal> {

    private CateringCalculator.Headcount headcount;

    public CateringPlanningWorkflow(AgentServices services, Event event) {
        super(services, event);
    }

    @Override
    protected String gatherContext() {
        headcount = services.catering().computeHeadcount(event);
        Map<String, Integer> diets = services.catering().summarizeDiets(event);
        List<Map<String, String>> slots = new ArrayList<>();
        for (AgendaItem i : event.agenda().items()) {
            if (i.type().isMealOrBreak() || i.type().name().equals("NETWORKING")) {
                slots.add(Map.of("title", i.title(), "start", i.start().toLocalTime().toString(),
                        "end", i.end().toLocalTime().toString()));
            }
        }
        return PromptContext.of("catering_planning", event).withBudget(event)
                .put("headcount", headcount.count())
                .put("headcount_from_rsvps", headcount.fromRsvps())
                .put("dietary_counts", diets)
                .put("meal_slots_in_agenda", slots)
                .render() + "\n" + super.gatherContext();
    }

    @Override
    protected AgentTask buildTask(String context) {
        String system = Prompts.system("""
                Task: design the catering plan.
                - Plan one meal per meal/break slot in the agenda (if there are none, plan meals that fit the event hours).
                - EVERY meal must include at least one menu item suitable for EACH dietary need in dietary_counts
                  (tag menu items with the needs they satisfy, e.g. ["vegan", "gluten-free"]).
                - Use catering_calculator for portions and cost; stay within the catering budget line if there is one.
                Reply format:
                {"summary": "...", "notes": "allergen notes for the caterer",
                 "meals": [{"name": "Lunch", "meal_type": "breakfast|coffee break|lunch|reception|dinner",
                            "time": "12:00",
                            "menu": [{"name": "Chickpea curry", "suitable_for": ["vegan", "halal", "gluten-free"]}]}]}
                """);
        return new AgentTask("catering_planning", system, context + "\n" + Prompts.request("Plan the catering."),
                List.of("catering_calculator", "attendee_query"), LLMRequest.Tier.MAIN, 4000);
    }

    @Override
    protected ResponseParser<PlanProposal> createParser() {
        return text -> {
            JsonNode root = JsonAnswer.parse(text);
            List<CateringPlan.Meal> meals = new ArrayList<>();
            for (JsonNode m : root.path("meals")) {
                String name = m.path("name").asText("Meal");
                String type = m.path("meal_type").asText(name);
                LocalDateTime time;
                try {
                    time = TimeParser.parse(m.path("time").asText(""), event.start().toLocalDate());
                } catch (IllegalArgumentException e) {
                    throw new ResponseParser.ParseException("meal \"" + name + "\": " + e.getMessage());
                }
                List<CateringPlan.MenuItem> menu = new ArrayList<>();
                for (JsonNode item : m.path("menu")) {
                    List<String> tags = new ArrayList<>();
                    item.path("suitable_for").forEach(t -> tags.add(t.asText()));
                    menu.add(new CateringPlan.MenuItem(item.path("name").asText(), tags));
                }
                if (menu.isEmpty()) {
                    throw new ResponseParser.ParseException("meal \"" + name + "\" has no menu items");
                }
                CateringCalculator.Portions p = services.catering().computePortions(type, headcount.count());
                meals.add(new CateringPlan.Meal(name, time, menu, p.portions(), p.costPerPortion()));
            }
            if (meals.isEmpty()) {
                throw new ResponseParser.ParseException("\"meals\" is empty");
            }
            CateringPlan plan = new CateringPlan(headcount.count(), meals, root.path("notes").asText(""));
            return new PlanProposal(event.id(), "Catering plan", root.path("summary").asText(""),
                    List.of(new PlanProposal.Change(new SetCateringPlanCommand(plan),
                            String.format("%d meals, $%.2f estimated", meals.size(), plan.estimatedCost()))), false);
        };
    }

    @Override
    protected List<Warning> validate(PlanProposal proposal, AgentResult run) {
        List<Warning> warnings = new ArrayList<>(services.guardrails().validateProposal(event, proposal));
        if (headcount != null && headcount.warning() != null) {
            warnings.add(Warning.low(headcount.warning()));
        }
        return warnings;
    }
}
