package com.eventpilot.agent.workflow;

import com.eventpilot.agent.AgentResult;
import com.eventpilot.agent.AgentTask;
import com.eventpilot.agent.llm.LLMRequest;
import com.eventpilot.application.command.SetBudgetLineCommand;
import com.eventpilot.application.proposal.PlanProposal;
import com.eventpilot.application.proposal.Warning;
import com.eventpilot.domain.Budget;
import com.eventpilot.domain.DomainException;
import com.eventpilot.domain.Event;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * F07 - the agent chooses how to split the budget (percentages and reasons);
 * {@code BudgetTracker.allocate} computes every amount.
 */
public class BudgetPlanningWorkflow extends PlanningWorkflow<PlanProposal> {

    public BudgetPlanningWorkflow(AgentServices services, Event event) {
        super(services, event);
        if (event.budget().limit() <= 0) {
            throw new WorkflowException("Set a budget limit first (e.g. `budget limit 10000`).");
        }
    }

    @Override
    protected String gatherContext() {
        return PromptContext.of("budget_planning", event).withBudget(event).withAttendeeSummary(event)
                .put("standard_categories", Budget.STANDARD_CATEGORIES).render() + "\n" + super.gatherContext();
    }

    @Override
    protected AgentTask buildTask(String context) {
        String system = Prompts.system("""
                Task: split the total budget limit across categories (use the standard categories; add others only
                if the event clearly needs them) and explain each choice briefly.
                - Consider the event type, size and duration and the chosen venue's price if there is one.
                - Percentages must add up to at most 100. Keep 5-10% as contingency.
                - Call budget_calculator with operation "allocate" to see the exact amounts before answering.
                Reply format:
                {"summary": "...", "allocation": [{"category": "venue", "percent": 35, "rationale": "..."}]}
                """);
        return new AgentTask("budget_planning", system, context + "\n" + Prompts.request("Plan the budget."),
                List.of("budget_calculator"), LLMRequest.Tier.MAIN, 3000);
    }

    @Override
    protected ResponseParser<PlanProposal> createParser() {
        return text -> {
            JsonNode root = JsonAnswer.parse(text);
            Map<String, Double> percentages = new LinkedHashMap<>();
            Map<String, String> rationale = new LinkedHashMap<>();
            for (JsonNode a : root.path("allocation")) {
                String category = a.path("category").asText("").trim();
                if (category.isEmpty() || !a.path("percent").isNumber()) {
                    throw new ResponseParser.ParseException("every allocation needs a category and a numeric percent");
                }
                percentages.merge(category.toLowerCase(), a.get("percent").asDouble(), Double::sum);
                rationale.put(category.toLowerCase(), a.path("rationale").asText(""));
            }
            if (percentages.isEmpty()) {
                throw new ResponseParser.ParseException("\"allocation\" is empty");
            }
            double sum = percentages.values().stream().mapToDouble(Double::doubleValue).sum();
            if (sum > 100.5) {
                throw new ResponseParser.ParseException("percentages add up to " + sum + "%, more than 100%");
            }
            List<Budget.Line> lines;
            try {
                lines = services.budget().allocate(event.budget().limit(), percentages, rationale);
            } catch (DomainException e) {
                throw new ResponseParser.ParseException(e.getMessage());
            }
            List<PlanProposal.Change> changes = new ArrayList<>();
            for (Budget.Line line : lines) {
                changes.add(new PlanProposal.Change(new SetBudgetLineCommand(line),
                        String.format("%.0f%% - %s", percentages.get(line.category()), line.rationale())));
            }
            return new PlanProposal(event.id(), "Budget plan", root.path("summary").asText(""), changes, false);
        };
    }

    @Override
    protected List<Warning> validate(PlanProposal proposal, AgentResult run) {
        return services.guardrails().validateProposal(event, proposal);
    }
}
