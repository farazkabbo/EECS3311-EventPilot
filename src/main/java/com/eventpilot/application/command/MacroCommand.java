package com.eventpilot.application.command;

import com.eventpilot.domain.Event;
import java.util.ArrayList;
import java.util.List;

/**
 * A group of commands applied as one undoable unit (one approved proposal).
 * Execution is atomic: if a step fails, the steps already done are undone.
 */
public class MacroCommand implements EventCommand {

    private String description;
    private List<EventCommand> steps = new ArrayList<>();

    private MacroCommand() {
    }

    public MacroCommand(String description, List<EventCommand> steps) {
        if (steps == null || steps.isEmpty()) {
            throw new IllegalArgumentException("a macro command needs at least one step");
        }
        this.description = description;
        this.steps = new ArrayList<>(steps);
    }

    @Override
    public void execute(Event event) {
        List<EventCommand> done = new ArrayList<>();
        try {
            for (EventCommand step : steps) {
                step.execute(event);
                done.add(step);
            }
        } catch (RuntimeException e) {
            for (int i = done.size() - 1; i >= 0; i--) {
                done.get(i).undo(event);
            }
            throw e;
        }
    }

    @Override
    public void undo(Event event) {
        for (int i = steps.size() - 1; i >= 0; i--) {
            steps.get(i).undo(event);
        }
    }

    public List<EventCommand> steps() {
        return List.copyOf(steps);
    }

    @Override
    public String describe() {
        return description + " (" + steps.size() + " change" + (steps.size() == 1 ? "" : "s") + ")";
    }
}
