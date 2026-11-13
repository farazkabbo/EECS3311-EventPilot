package com.eventpilot.application.command;

import com.eventpilot.domain.Budget;
import com.eventpilot.domain.Event;

/** Sets the planned amount for one budget category (F07). */
public class SetBudgetLineCommand implements EventCommand {

    private Budget.Line line;
    private Budget.Line previous;

    private SetBudgetLineCommand() {
    }

    public SetBudgetLineCommand(Budget.Line line) {
        this.line = line;
    }

    @Override
    public void execute(Event event) {
        previous = event.budget().setLine(line).orElse(null);
    }

    @Override
    public void undo(Event event) {
        if (previous != null) {
            event.budget().setLine(previous);
        } else {
            event.budget().removeLine(line.category());
        }
    }

    public Budget.Line line() {
        return line;
    }

    @Override
    public String describe() {
        return String.format("Plan $%.2f for %s%s", line.amount(), line.category(),
                line.rationale().isBlank() ? "" : " - " + line.rationale());
    }
}
