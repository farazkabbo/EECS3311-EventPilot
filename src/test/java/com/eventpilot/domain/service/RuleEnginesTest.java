package com.eventpilot.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eventpilot.domain.Attendee;
import com.eventpilot.domain.Budget;
import com.eventpilot.domain.DomainException;
import com.eventpilot.domain.Event;
import com.eventpilot.domain.EventTask;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuleEnginesTest {

    static final LocalDateTime START = LocalDateTime.of(2026, 12, 5, 9, 0);

    private Event event() {
        return new Event("Meetup", "meetup", START, START.plusHours(6), 100, 10000, "Toronto");
    }

    // ---- BudgetTracker ----

    @Test
    void allocateSplitsWholeLimit() {
        Map<String, Double> pct = new LinkedHashMap<>();
        pct.put("venue", 40.0);
        pct.put("catering", 35.0);
        pct.put("contingency", 25.0);
        List<Budget.Line> lines = new BudgetTracker().allocate(10000, pct, Map.of());
        assertEquals(4000, lines.get(0).amount());
        assertEquals(10000, lines.stream().mapToDouble(Budget.Line::amount).sum(), 0.001);
    }

    @Test
    void allocateNormalisesOver100Percent() {
        List<Budget.Line> lines = new BudgetTracker().allocate(1000, Map.of("a", 100.0, "b", 100.0), Map.of());
        assertTrue(lines.stream().mapToDouble(Budget.Line::amount).sum() <= 1000);
    }

    @Test
    void allocateNeedsLimit() {
        assertThrows(DomainException.class, () -> new BudgetTracker().allocate(0, Map.of("a", 10.0), Map.of()));
    }

    @Test
    void statusRaisesOverPlanAndNinetyPercentAlerts() {
        Event e = event();
        BudgetTracker tracker = new BudgetTracker();
        e.budget().setLine(new Budget.Line("venue", 4000, ""));
        tracker.recordExpense(e, "venue", 4500, null, "deposit");
        tracker.recordExpense(e, "catering", 4600, null, "food");
        BudgetTracker.BudgetStatus status = tracker.computeStatus(e);
        assertEquals(9100, status.totalSpent(), 0.001);
        assertTrue(status.alerts().stream().anyMatch(a -> a.contains("'venue' is over plan")));
        assertTrue(status.alerts().stream().anyMatch(a -> a.contains("91%")));
        assertThrows(DomainException.class, () -> tracker.recordExpense(e, "venue", -5, null, ""));
    }

    // ---- CateringCalculator ----

    @Test
    void headcountUsesConfirmedRsvpsOrFallsBack() {
        Event e = event();
        CateringCalculator calc = new CateringCalculator();
        CateringCalculator.Headcount none = calc.computeHeadcount(e);
        assertFalse(none.fromRsvps());
        assertEquals(100, none.count());

        e.addAttendee(new Attendee(null, "A", "a@x.com", null, List.of("vegan"), null, Attendee.Rsvp.ACCEPTED));
        e.addAttendee(new Attendee(null, "B", "b@x.com", null, List.of("halal"), null, Attendee.Rsvp.DECLINED));
        assertEquals(1, calc.computeHeadcount(e).count());
        assertEquals(Map.of("vegan", 1), calc.summarizeDiets(e));
    }

    @Test
    void portionsIncludeBuffer() {
        CateringCalculator.Portions p = new CateringCalculator().computePortions("lunch", 100);
        assertEquals(105, p.portions());
        assertEquals(105 * 18.0, p.totalCost(), 0.001);
    }

    // ---- TaskScheduler ----

    private EventTask task(String id, int daysBefore, String... deps) {
        return new EventTask(id, "Task " + id, "me", LocalDate.of(2026, 12, 5).minusDays(daysBefore), List.of(deps), null);
    }

    @Test
    void detectsCycle() {
        List<EventTask> tasks = List.of(task("t1", 10, "t3"), task("t2", 9, "t1"), task("t3", 8, "t2"));
        assertTrue(new TaskScheduler().detectCycles(tasks).isPresent());
        assertThrows(IllegalArgumentException.class, () -> new TaskScheduler().orderByDependencies(tasks));
    }

    @Test
    void ordersDependenciesFirst() {
        List<EventTask> tasks = List.of(task("t1", 1, "t2"), task("t2", 5), task("t3", 3, "t1"));
        List<String> order = new TaskScheduler().orderByDependencies(tasks).stream().map(EventTask::id).toList();
        assertEquals(List.of("t2", "t1", "t3"), order);
    }

    @Test
    void findsOverdueAndOpenDependencies() {
        EventTask a = task("t1", 30);
        EventTask b = task("t2", 1, "t1");
        TaskScheduler s = new TaskScheduler();
        assertEquals(List.of(a), s.findOverdue(List.of(a, b), LocalDate.of(2026, 11, 20)));
        assertEquals(List.of(a), s.openDependencies(b, List.of(a, b)));
        a.setStatus(EventTask.Status.DONE);
        assertTrue(s.openDependencies(b, List.of(a, b)).isEmpty());
    }
}
