package com.eventpilot.agent.tools;

import com.eventpilot.domain.Budget;
import com.eventpilot.domain.service.BudgetTracker;
import com.eventpilot.infra.Json;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** budget_calculator: exact budget arithmetic so the LLM never computes money itself. */
public class BudgetCalculatorTool implements AgentTool {

    private static final JsonNode SCHEMA = Schemas.of("""
            {"type": "object",
             "properties": {
               "operation": {"type": "string", "enum": ["allocate", "remaining", "status"],
                             "description": "allocate = split the limit by percentages; remaining = money left for a category; status = planned vs spent"},
               "percentages": {"type": "object", "additionalProperties": {"type": "number", "minimum": 0},
                               "description": "For allocate: category -> percent of the total limit"},
               "category": {"type": "string", "description": "For remaining: the category, e.g. venue"}
             },
             "required": ["operation"],
             "additionalProperties": false}
            """);

    private final BudgetTracker tracker;

    public BudgetCalculatorTool(BudgetTracker tracker) {
        this.tracker = tracker;
    }

    @Override
    public String name() {
        return "budget_calculator";
    }

    @Override
    public String description() {
        return "Exact budget arithmetic for the current event: allocate the budget limit by percentages, "
                + "get the remaining money for a category, or get planned-vs-spent status.";
    }

    @Override
    public JsonNode inputSchema() {
        return SCHEMA;
    }

    @Override
    public String execute(JsonNode a, ToolContext context) {
        ObjectNode result = Json.object();
        double limit = context.event().budget().limit();
        result.put("limit", limit);
        switch (a.get("operation").asText()) {
            case "allocate" -> {
                Map<String, Double> pct = new LinkedHashMap<>();
                a.path("percentages").fields().forEachRemaining(e -> pct.put(e.getKey(), e.getValue().asDouble()));
                if (pct.isEmpty()) {
                    throw new IllegalArgumentException("percentages are required for allocate");
                }
                List<Budget.Line> lines = tracker.allocate(limit, pct, Map.of());
                ArrayNode arr = result.putArray("lines");
                lines.forEach(l -> arr.addObject().put("category", l.category()).put("amount", l.amount()));
                result.put("total", lines.stream().mapToDouble(Budget.Line::amount).sum());
            }
            case "remaining" -> {
                String category = a.path("category").asText("venue");
                result.put("category", category);
                result.put("remaining", tracker.remainingFor(context.event(), category));
            }
            default -> {
                BudgetTracker.BudgetStatus s = tracker.computeStatus(context.event());
                result.put("total_planned", s.totalPlanned()).put("total_spent", s.totalSpent());
                ArrayNode arr = result.putArray("categories");
                s.categories().forEach(c -> arr.addObject().put("category", c.category())
                        .put("planned", c.planned()).put("spent", c.spent()));
            }
        }
        return Json.write(result);
    }
}
