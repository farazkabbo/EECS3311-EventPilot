package com.eventpilot.agent.workflow;

import com.eventpilot.agent.AgentResult;
import com.eventpilot.agent.AgentTask;
import com.eventpilot.agent.llm.LLMRequest;
import com.eventpilot.application.command.AddTaskCommand;
import com.eventpilot.application.proposal.PlanProposal;
import com.eventpilot.application.proposal.Warning;
import com.eventpilot.domain.Event;
import com.eventpilot.domain.EventTask;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * F09 - the agent breaks the event into tasks planned backwards from the event
 * date; TaskScheduler checks cycles and orders them.
 */
public class TaskDecompositionWorkflow extends PlanningWorkflow<PlanProposal> {

    private final List<String> team;

    public TaskDecompositionWorkflow(AgentServices services, Event event, List<String> team) {
        super(services, event);
        this.team = team == null || team.isEmpty() ? List.of("organizer") : List.copyOf(team);
    }

    @Override
    protected String gatherContext() {
        return PromptContext.of("task_decomposition", event).withTasks(event)
                .put("today", LocalDate.now(services.clock()).toString())
                .put("team", team)
                .put("next_task_id", event.nextTaskId())
                .put("has_venue", event.venue() != null)
                .put("has_agenda", !event.agenda().isEmpty())
                .put("has_catering_plan", event.cateringPlan() != null)
                .render() + "\n" + super.gatherContext();
    }

    @Override
    protected AgentTask buildTask(String context) {
        String system = Prompts.system("""
                Task: break the remaining preparation work into 8-15 concrete tasks.
                - Plan backwards from the event date: every deadline must be on or before the event date and,
                  where possible, on or after today. Skip work that is already done (see has_venue etc. and tasks).
                - Give each task an owner from the team and dependencies (ids of tasks that must finish first).
                  No circular dependencies. A task's deadline must not be before its dependencies' deadlines.
                - Number new ids from next_task_id upwards (t1, t2, ...); never reuse existing ids.
                Reply format:
                {"summary": "...", "tasks": [{"id": "t1", "title": "...", "owner": "Sam",
                  "deadline": "YYYY-MM-DD", "depends_on": ["t0"]}]}
                """);
        return new AgentTask("task_decomposition", system, context + "\n" + Prompts.request("Create the task plan."),
                List.of(), LLMRequest.Tier.MAIN, 4000);
    }

    @Override
    protected ResponseParser<PlanProposal> createParser() {
        return text -> {
            JsonNode root = JsonAnswer.parse(text);
            Set<String> existing = new HashSet<>();
            event.tasks().forEach(t -> existing.add(t.id()));
            List<EventTask> tasks = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            for (JsonNode n : root.path("tasks")) {
                String id = n.path("id").asText("").trim();
                if (id.isEmpty() || existing.contains(id)) {
                    errors.add("task id \"" + id + "\" is missing or already used");
                    continue;
                }
                existing.add(id);
                List<String> deps = new ArrayList<>();
                n.path("depends_on").forEach(d -> deps.add(d.asText()));
                try {
                    tasks.add(new EventTask(id, n.path("title").asText(), n.path("owner").asText(team.get(0)),
                            LocalDate.parse(n.path("deadline").asText()), deps, EventTask.Status.TODO));
                } catch (DateTimeParseException | IllegalArgumentException e) {
                    errors.add("task " + id + ": invalid deadline or title (" + e.getMessage() + ")");
                }
            }
            if (!errors.isEmpty()) {
                throw new ResponseParser.ParseException(String.join("; ", errors));
            }
            if (tasks.isEmpty()) {
                throw new ResponseParser.ParseException("\"tasks\" is empty");
            }
            List<EventTask> all = new ArrayList<>(event.tasks());
            all.addAll(tasks);
            List<EventTask> ordered;
            try {
                ordered = services.tasks().orderByDependencies(all);
            } catch (IllegalArgumentException e) {
                throw new ResponseParser.ParseException(e.getMessage() + ". Remove the cycle.");
            }
            Map<String, EventTask> newOnes = new HashMap<>();
            tasks.forEach(t -> newOnes.put(t.id(), t));
            List<PlanProposal.Change> changes = new ArrayList<>();
            for (EventTask t : ordered) {
                if (newOnes.containsKey(t.id())) {
                    changes.add(new PlanProposal.Change(new AddTaskCommand(t), ""));
                }
            }
            return new PlanProposal(event.id(), "Task plan", root.path("summary").asText(""), changes, false);
        };
    }

    @Override
    protected List<Warning> validate(PlanProposal proposal, AgentResult run) {
        return services.guardrails().validateProposal(event, proposal);
    }
}
