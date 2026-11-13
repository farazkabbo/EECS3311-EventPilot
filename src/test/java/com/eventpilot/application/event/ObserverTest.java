package com.eventpilot.application.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eventpilot.domain.AgendaItem;
import com.eventpilot.domain.AgendaItemType;
import com.eventpilot.domain.Event;
import com.eventpilot.domain.EventTask;
import com.eventpilot.domain.service.BudgetTracker;
import com.eventpilot.domain.service.ConflictDetector;
import com.eventpilot.domain.service.TaskScheduler;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ObserverTest {

    static final LocalDateTime NINE = LocalDateTime.of(2026, 12, 5, 9, 0);

    @Test
    void busNotifiesAllObserversEvenIfOneFails() {
        EventBus bus = new EventBus();
        List<String> seen = new ArrayList<>();
        bus.subscribe(c -> { throw new IllegalStateException("boom"); });
        bus.subscribe(c -> seen.add(c.type().name()));
        bus.publish(DomainEvent.of(DomainEvent.Type.EVENT_CHANGED, null, ""));
        assertEquals(List.of("EVENT_CHANGED"), seen);
    }

    @Test
    void alertServiceReactsToChangesAndEscalations() {
        EventBus bus = new EventBus();
        Clock clock = Clock.fixed(LocalDate.of(2026, 11, 30).atStartOfDay(ZoneId.systemDefault()).toInstant(),
                ZoneId.systemDefault());
        AlertService alerts = new AlertService(bus, new ConflictDetector(), new BudgetTracker(), new TaskScheduler(), clock);
        bus.subscribe(alerts);
        List<DomainEvent> updates = new ArrayList<>();
        bus.subscribe(c -> {
            if (c.type() == DomainEvent.Type.ALERTS_UPDATED) {
                updates.add(c);
            }
        });

        Event event = new Event("Conf", "conference", NINE, NINE.plusHours(4), 50, 1000, "Toronto");
        event.agenda().add(new AgendaItem(null, "A", AgendaItemType.SESSION, NINE, NINE.plusHours(1), "Main Hall", null, 10));
        event.agenda().add(new AgendaItem(null, "B", AgendaItemType.SESSION, NINE, NINE.plusHours(1), "Main Hall", null, 10));
        event.addTask(new EventTask(null, "Book venue", "Sam", LocalDate.of(2026, 11, 1), List.of(), null));

        bus.publish(DomainEvent.of(DomainEvent.Type.AGENDA_CHANGED, event, ""));
        List<Alert> list = alerts.alertsFor(event.id());
        assertTrue(list.stream().anyMatch(a -> a.level() == Alert.Level.CRITICAL && a.category().equals("Conflict")));
        assertTrue(list.stream().anyMatch(a -> a.category().equals("Task")));
        assertEquals(1, updates.size());

        bus.publish(DomainEvent.of(DomainEvent.Type.FAQ_ESCALATED, event, "Is there parking?"));
        assertEquals(List.of("Is there parking?"), alerts.escalatedQuestions(event.id()));
        assertTrue(alerts.alertsFor(event.id()).stream().anyMatch(a -> a.category().equals("FAQ")));
    }
}
