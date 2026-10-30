package com.eventpilot.gui;

import com.eventpilot.domain.EventTask;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/** F09: AI task plan, manual tasks and status changes (refused while a dependency is open). */
public class TaskController extends BaseController {

    public TaskController(GuiContext ctx) {
        super(ctx);
    }

    public void onGenerateTasks(String team) {
        List<String> members = Arrays.stream(team.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        requireEvent().ifPresent(id -> runAsync("Agent is planning the tasks",
                () -> ctx.facade().generateTasks(id, members), this::reviewAndApply));
    }

    public void onUpdateTaskStatus(String taskId, EventTask.Status status) {
        requireEvent().ifPresent(id -> run("Task " + taskId + " -> " + status,
                () -> ctx.facade().updateTaskStatus(id, taskId, status)));
    }

    public void onAddTask(String title, String owner, LocalDate due) {
        requireEvent().ifPresent(id -> run("Task added", () -> ctx.facade().addTask(id, title, owner, due, List.of())));
    }
}
