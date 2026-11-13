package com.eventpilot.application.command;

import com.eventpilot.domain.Event;
import com.eventpilot.domain.EventTask;

/** Adds a planning task (F09). */
public class AddTaskCommand implements EventCommand {

    private EventTask task;

    private AddTaskCommand() {
    }

    public AddTaskCommand(EventTask task) {
        this.task = task;
    }

    @Override
    public void execute(Event event) {
        event.addTask(new EventTask(task.id(), task.title(), task.owner(), task.deadline(), task.dependsOn(), task.status()));
    }

    @Override
    public void undo(Event event) {
        event.removeTask(task.id());
    }

    public EventTask task() {
        return task;
    }

    @Override
    public String describe() {
        return "Add task " + task.id() + " \"" + task.title() + "\" for " + task.owner() + ", due " + task.deadline()
                + (task.dependsOn().isEmpty() ? "" : " (after " + String.join(", ", task.dependsOn()) + ")");
    }
}
