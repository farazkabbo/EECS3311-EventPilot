package com.eventpilot.application.command;

import com.eventpilot.domain.CateringPlan;
import com.eventpilot.domain.Event;

/** Replaces the catering plan (F08). */
public class SetCateringPlanCommand implements EventCommand {

    private CateringPlan plan;
    private CateringPlan previous;

    private SetCateringPlanCommand() {
    }

    public SetCateringPlanCommand(CateringPlan plan) {
        this.plan = plan;
    }

    @Override
    public void execute(Event event) {
        previous = event.cateringPlan();
        event.setCateringPlan(plan);
    }

    @Override
    public void undo(Event event) {
        event.setCateringPlan(previous);
    }

    public CateringPlan plan() {
        return plan;
    }

    @Override
    public String describe() {
        return String.format("Set catering plan: %d meal(s) for %d people, estimated $%.2f",
                plan.meals().size(), plan.headcount(), plan.estimatedCost());
    }
}
