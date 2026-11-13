package com.eventpilot.agent.tools;

import com.eventpilot.domain.service.CateringCalculator;
import com.eventpilot.infra.Json;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** catering_calculator: portions and cost for one meal, plus the dietary counts. */
public class CateringCalculatorTool implements AgentTool {

    private static final JsonNode SCHEMA = Schemas.of("""
            {"type": "object",
             "properties": {
               "meal_type": {"type": "string", "enum": ["breakfast", "coffee break", "lunch", "reception", "dinner"]},
               "headcount": {"type": "integer", "minimum": 1, "description": "Optional; default = confirmed headcount"}
             },
             "required": ["meal_type"],
             "additionalProperties": false}
            """);

    private final CateringCalculator calculator;

    public CateringCalculatorTool(CateringCalculator calculator) {
        this.calculator = calculator;
    }

    @Override
    public String name() {
        return "catering_calculator";
    }

    @Override
    public String description() {
        return "Compute portions (headcount + 5% buffer), price per portion and total cost for a meal, "
                + "and return the dietary counts of confirmed attendees.";
    }

    @Override
    public JsonNode inputSchema() {
        return SCHEMA;
    }

    @Override
    public String execute(JsonNode a, ToolContext context) {
        int headcount = a.hasNonNull("headcount") ? a.get("headcount").asInt()
                : calculator.computeHeadcount(context.event()).count();
        CateringCalculator.Portions p = calculator.computePortions(a.get("meal_type").asText(), headcount);
        ObjectNode result = Json.object();
        result.put("meal_type", p.mealType()).put("headcount", p.headcount()).put("portions", p.portions())
                .put("cost_per_portion", p.costPerPortion()).put("total_cost", p.totalCost());
        ObjectNode diets = result.putObject("dietary_counts");
        calculator.summarizeDiets(context.event()).forEach(diets::put);
        return Json.write(result);
    }
}
