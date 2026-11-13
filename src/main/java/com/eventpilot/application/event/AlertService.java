package com.eventpilot.application.event;

import com.eventpilot.domain.Conflict;
import com.eventpilot.domain.Event;
import com.eventpilot.domain.EventTask;
import com.eventpilot.domain.service.BudgetTracker;
import com.eventpilot.domain.service.ConflictDetector;
import com.eventpilot.domain.service.TaskScheduler;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Concrete observer. After every change it re-checks the event - serious
 * conflicts, budget alerts, overdue tasks - and keeps the current alert list.
 * Escalated FAQ questions are kept until the organizer clears them.
 */
public class AlertService implements EventObserver {

    private final EventBus bus;
    private final ConflictDetector conflictDetector;
    private final BudgetTracker budgetTracker;
    private final TaskScheduler taskScheduler;
    private final Clock clock;
    private final Map<String, List<Alert>> current = new ConcurrentHashMap<>();
    private final Map<String, List<Alert>> escalations = new ConcurrentHashMap<>();

    public AlertService(EventBus bus, ConflictDetector conflictDetector, BudgetTracker budgetTracker,
                        TaskScheduler taskScheduler, Clock clock) {
        this.bus = bus;
        this.conflictDetector = conflictDetector;
        this.budgetTracker = budgetTracker;
        this.taskScheduler = taskScheduler;
        this.clock = clock;
    }

    @Override
    public void onEventChanged(DomainEvent change) {
        switch (change.type()) {
            case ALERTS_UPDATED -> {
                // our own notification - ignore to avoid loops
            }
            case EVENT_DELETED -> {
                current.remove(change.eventId());
                escalations.remove(change.eventId());
            }
            case FAQ_ESCALATED -> {
                escalations.computeIfAbsent(change.eventId(), k -> new ArrayList<>())
                        .add(new Alert(Alert.Level.WARNING, "FAQ", "Unanswered attendee question: " + change.detail()));
                refresh(change.event());
            }
            default -> refresh(change.event());
        }
    }

    /** Recomputes the alerts of an event (also called at start-up for the overdue-task check). */
    public List<Alert> refresh(Event event) {
        if (event == null) {
            return List.of();
        }
        List<Alert> alerts = checkAll(event);
        alerts.addAll(escalations.getOrDefault(event.id(), List.of()));
        current.put(event.id(), alerts);
        bus.publish(DomainEvent.of(DomainEvent.Type.ALERTS_UPDATED, event, alerts.size() + " alert(s)"));
        return alerts;
    }

    /** Current alerts without storing or publishing anything (safe to call from views). */
    public List<Alert> compute(Event event) {
        List<Alert> alerts = checkAll(event);
        alerts.addAll(escalations.getOrDefault(event.id(), List.of()));
        return alerts;
    }

    public List<Alert> checkAll(Event event) {
        List<Alert> alerts = new ArrayList<>();
        for (Conflict c : conflictDetector.detect(event)) {
            alerts.add(new Alert(c.isBlocking() ? Alert.Level.CRITICAL : Alert.Level.WARNING, "Conflict", c.message()));
        }
        for (String msg : budgetTracker.computeStatus(event).alerts()) {
            alerts.add(new Alert(Alert.Level.WARNING, "Budget", msg));
        }
        alerts.addAll(checkOverdueTasks(event));
        return alerts;
    }

    public List<Alert> checkOverdueTasks(Event event) {
        LocalDate today = LocalDate.now(clock);
        List<Alert> alerts = new ArrayList<>();
        for (EventTask t : taskScheduler.findOverdue(event.tasks(), today)) {
            alerts.add(new Alert(Alert.Level.WARNING, "Task",
                    "Overdue: " + t.title() + " (" + t.owner() + ", was due " + t.deadline() + ")"));
        }
        return alerts;
    }

    public List<Alert> alertsFor(String eventId) {
        return List.copyOf(current.getOrDefault(eventId, List.of()));
    }

    public void clearEscalations(String eventId) {
        escalations.remove(eventId);
    }

    /** Restores persisted escalations (CLI runs are separate processes). */
    public void restoreEscalations(String eventId, List<String> questions) {
        List<Alert> list = new ArrayList<>();
        questions.forEach(q -> list.add(new Alert(Alert.Level.WARNING, "FAQ", "Unanswered attendee question: " + q)));
        escalations.put(eventId, list);
    }

    public List<String> escalatedQuestions(String eventId) {
        String prefix = "Unanswered attendee question: ";
        return escalations.getOrDefault(eventId, List.of()).stream()
                .map(a -> a.message().startsWith(prefix) ? a.message().substring(prefix.length()) : a.message())
                .toList();
    }
}
