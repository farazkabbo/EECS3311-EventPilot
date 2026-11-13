package com.eventpilot.agent;

import com.eventpilot.application.VenueCatalog;
import com.eventpilot.application.command.AddTaskCommand;
import com.eventpilot.application.command.EventCommand;
import com.eventpilot.application.command.SetBudgetLineCommand;
import com.eventpilot.application.command.SetCateringPlanCommand;
import com.eventpilot.application.command.SetVenueCommand;
import com.eventpilot.application.proposal.PlanProposal;
import com.eventpilot.application.proposal.Warning;
import com.eventpilot.domain.Budget;
import com.eventpilot.domain.CateringPlan;
import com.eventpilot.domain.Conflict;
import com.eventpilot.domain.Event;
import com.eventpilot.domain.EventDraft;
import com.eventpilot.domain.EventTask;
import com.eventpilot.domain.Venue;
import com.eventpilot.domain.service.CateringCalculator;
import com.eventpilot.domain.service.ConflictDetector;
import com.eventpilot.domain.service.TaskScheduler;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic checks on everything the agent produces. A proposal is applied
 * to a copy of the event and checked there, so problems are found before the
 * organizer is asked to approve anything.
 */
public class GuardrailValidator {

    private final ConflictDetector conflictDetector;
    private final TaskScheduler taskScheduler;
    private final CateringCalculator cateringCalculator;
    private final VenueCatalog venueCatalog;

    public GuardrailValidator(ConflictDetector conflictDetector, TaskScheduler taskScheduler,
                              CateringCalculator cateringCalculator, VenueCatalog venueCatalog) {
        this.conflictDetector = conflictDetector;
        this.taskScheduler = taskScheduler;
        this.cateringCalculator = cateringCalculator;
        this.venueCatalog = venueCatalog;
    }

    /** Questions to ask about an extracted event (missing fields and contradictions). */
    public List<String> validateDraft(EventDraft draft) {
        List<String> questions = new ArrayList<>(draft.contradictions());
        for (String field : draft.missingFields()) {
            questions.add(switch (field) {
                case "name" -> "What is the name of the event?";
                case "start date and time" -> "On what date and at what time does it start?";
                case "end time" -> "When does it end?";
                case "expected attendance" -> "How many people do you expect?";
                case "budget" -> "What is the total budget (CAD)?";
                default -> "Please provide the " + field + ".";
            });
        }
        return questions;
    }

    /** Checks a proposal against the current event. Alternatives are checked one by one. */
    public List<Warning> validateProposal(Event event, PlanProposal proposal) {
        List<Warning> warnings = new ArrayList<>();
        if (proposal.isQuestion()) {
            return warnings;
        }
        if (proposal.alternatives()) {
            for (int i = 0; i < proposal.changes().size(); i++) {
                for (Warning w : checkCommands(event, List.of(proposal.changes().get(i).command()))) {
                    warnings.add(new Warning(w.severity(), "Option " + (i + 1) + ": " + w.message()));
                }
            }
            return warnings;
        }
        return checkCommands(event, proposal.changes().stream().map(PlanProposal.Change::command).toList());
    }

    public List<Warning> checkCommands(Event event, List<EventCommand> commands) {
        List<Warning> warnings = new ArrayList<>();
        Event trial = event.deepCopy();
        Set<String> before = new HashSet<>();
        conflictDetector.detect(trial).forEach(c -> before.add(c.message()));
        for (EventCommand command : commands) {
            try {
                command.execute(trial);
            } catch (RuntimeException e) {
                warnings.add(Warning.high("\"" + command.describe() + "\" cannot be applied: " + e.getMessage()));
            }
        }
        for (Conflict c : conflictDetector.detect(trial)) {
            if (!before.contains(c.message())) {
                warnings.add(c.isBlocking() ? Warning.high("Creates a conflict: " + c.message())
                        : Warning.medium("Creates a problem: " + c.message()));
            }
        }
        for (EventCommand command : commands) {
            if (command instanceof SetVenueCommand v) {
                warnings.addAll(checkVenue(trial, v.venue()));
            } else if (command instanceof SetCateringPlanCommand c) {
                warnings.addAll(checkCatering(trial, c.plan()));
            }
        }
        if (commands.stream().anyMatch(c -> c instanceof SetBudgetLineCommand)) {
            warnings.addAll(checkBudget(trial));
        }
        if (commands.stream().anyMatch(c -> c instanceof AddTaskCommand)) {
            warnings.addAll(checkTasks(trial));
        }
        return warnings;
    }

    private List<Warning> checkVenue(Event event, Venue venue) {
        List<Warning> w = new ArrayList<>();
        if (venueCatalog.findById(venue.id()).isEmpty()) {
            w.add(Warning.high("Venue " + venue.name() + " (" + venue.id() + ") is not in the venue catalog."));
        }
        if (venue.capacity() < event.expectedAttendance()) {
            w.add(Warning.high(venue.name() + " holds " + venue.capacity() + " but " + event.expectedAttendance()
                    + " people are expected."));
        }
        double venueBudget = event.budget().line("venue").map(Budget.Line::amount).orElse(event.budget().limit());
        if (venueBudget > 0 && venue.pricePerDay() > venueBudget) {
            w.add(Warning.medium(String.format("%s costs $%.0f/day, above the venue budget of $%.0f.",
                    venue.name(), venue.pricePerDay(), venueBudget)));
        }
        if (!event.city().isBlank() && !venue.city().equalsIgnoreCase(event.city())) {
            w.add(Warning.medium(venue.name() + " is in " + venue.city() + ", not " + event.city() + "."));
        }
        return w;
    }

    private List<Warning> checkBudget(Event event) {
        double total = event.budget().lines().stream().mapToDouble(Budget.Line::amount).sum();
        if (total > event.budget().limit() + 0.01) {
            return List.of(Warning.high(String.format("The planned total $%.2f exceeds the budget limit $%.2f.",
                    total, event.budget().limit())));
        }
        return List.of();
    }

    private List<Warning> checkCatering(Event event, CateringPlan plan) {
        List<Warning> w = new ArrayList<>();
        if (plan.meals().isEmpty()) {
            w.add(Warning.high("The catering plan has no meals."));
            return w;
        }
        Map<String, Integer> diets = cateringCalculator.summarizeDiets(event);
        Set<String> covered = plan.coveredDiets();
        for (String diet : diets.keySet()) {
            if (!covered.contains(diet)) {
                w.add(Warning.high("Not every meal has an option for \"" + diet + "\" (" + diets.get(diet) + " people)."));
            }
        }
        int headcount = cateringCalculator.computeHeadcount(event).count();
        for (CateringPlan.Meal meal : plan.meals()) {
            if (meal.portions() < headcount) {
                w.add(Warning.high(meal.name() + " has " + meal.portions() + " portions for " + headcount + " people."));
            }
            if (!event.window().contains(new com.eventpilot.domain.TimeWindow(meal.time(), meal.time().plusMinutes(1)))) {
                w.add(Warning.medium(meal.name() + " is served outside the event hours."));
            }
        }
        double cateringBudget = event.budget().line("catering").map(Budget.Line::amount).orElse(0.0);
        if (cateringBudget > 0 && plan.estimatedCost() > cateringBudget) {
            w.add(Warning.medium(String.format("Catering costs $%.2f, above the catering budget of $%.2f.",
                    plan.estimatedCost(), cateringBudget)));
        }
        return w;
    }

    private List<Warning> checkTasks(Event event) {
        List<Warning> w = new ArrayList<>();
        List<EventTask> tasks = event.tasks();
        taskScheduler.detectCycles(tasks).ifPresent(c ->
                w.add(Warning.high("Circular task dependency: " + String.join(" -> ", c))));
        taskScheduler.unknownDependencies(tasks).forEach(u -> w.add(Warning.high("Unknown dependency " + u)));
        for (EventTask t : tasks) {
            if (t.deadline().isAfter(event.start().toLocalDate())) {
                w.add(Warning.high("Task \"" + t.title() + "\" is due " + t.deadline() + ", after the event."));
            }
        }
        for (EventTask t : tasks) {
            for (String dep : t.dependsOn()) {
                event.findTask(dep).filter(d -> d.deadline().isAfter(t.deadline())).ifPresent(d ->
                        w.add(Warning.medium("\"" + t.title() + "\" is due before its dependency \"" + d.title() + "\".")));
            }
        }
        return w;
    }
}
