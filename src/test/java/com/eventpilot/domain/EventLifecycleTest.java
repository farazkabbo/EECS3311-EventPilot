package com.eventpilot.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eventpilot.domain.state.EventStatus;
import com.eventpilot.domain.state.LifecycleAction;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class EventLifecycleTest {

    static final LocalDateTime START = LocalDateTime.of(2026, 12, 5, 9, 0);

    static Event sampleEvent() {
        return new Event("AI Meetup", "meetup", START, START.plusHours(8), 120, 10000, "Toronto");
    }

    static Venue sampleVenue() {
        return new Venue("v1", "Hall", "Toronto", "Downtown", "1 King St", 200, 1500, true, 4.5,
                List.of("stage"), List.of(new Room("Hall A", 150), new Room("Room B", 60)));
    }

    @Test
    void draftCannotInviteButPlanningCan() {
        Event event = sampleEvent();
        assertEquals(EventStatus.DRAFT, event.status());
        DomainException ex = assertThrows(DomainException.class, event::ensureCanInvite);
        assertTrue(ex.getMessage().contains("Start planning"));
        event.transition(LifecycleAction.START_PLANNING);
        assertDoesNotThrow(event::ensureCanInvite);
    }

    @Test
    void confirmRequiresVenueAndAgenda() {
        Event event = sampleEvent();
        event.transition(LifecycleAction.START_PLANNING);
        DomainException ex = assertThrows(DomainException.class, () -> event.transition(LifecycleAction.CONFIRM));
        assertTrue(ex.getMessage().contains("venue") && ex.getMessage().contains("agenda"));

        event.setVenue(sampleVenue());
        event.agenda().add(new AgendaItem(null, "Welcome", AgendaItemType.KEYNOTE, START, START.plusHours(1),
                "Hall A", null, 100));
        event.transition(LifecycleAction.CONFIRM);
        assertEquals(EventStatus.CONFIRMED, event.status());
    }

    @Test
    void completedAndCancelledAreReadOnly() {
        Event event = sampleEvent();
        event.transition(LifecycleAction.CANCEL);
        assertThrows(DomainException.class, event::ensureEditable);
        assertThrows(DomainException.class, () -> event.transition(LifecycleAction.START_PLANNING));
    }

    @Test
    void invalidActionIsRefusedWithAllowedList() {
        Event event = sampleEvent();
        DomainException ex = assertThrows(DomainException.class, () -> event.transition(LifecycleAction.COMPLETE));
        assertTrue(ex.getMessage().contains("START_PLANNING"));
    }

    @Test
    void deepCopyIsIndependentAndKeepsState() {
        Event event = sampleEvent();
        event.transition(LifecycleAction.START_PLANNING);
        event.setVenue(sampleVenue());
        event.addAttendee(new Attendee(null, "Ann", "ann@x.com", Attendee.Role.VIP, List.of("vegan"), null, Attendee.Rsvp.ACCEPTED));
        event.agenda().add(new AgendaItem(null, "Talk", AgendaItemType.SESSION, START, START.plusHours(1), "Hall A", null, 50));

        Event copy = event.deepCopy();
        assertNotSame(event, copy);
        assertEquals(EventStatus.PLANNING, copy.state().status());
        assertEquals("Hall", copy.venue().name());
        assertEquals(2, copy.rooms().size());
        assertEquals("vegan", copy.attendees().get(0).dietary().get(0));

        copy.agenda().require("a1").reschedule(START.plusHours(2), START.plusHours(3));
        assertEquals(START, event.agenda().require("a1").start(), "original unchanged");
    }

    @Test
    void draftReportsMissingFieldsAndContradictions() {
        EventDraft draft = new EventDraft();
        draft.name = "Party";
        draft.start = START;
        draft.end = START.minusHours(1);
        assertTrue(draft.missingFields().contains("expected attendance"));
        assertEquals(1, draft.contradictions().size());
        assertThrows(DomainException.class, draft::toEvent);
    }

    @Test
    void duplicateAttendeeEmailRejected() {
        Event event = sampleEvent();
        event.addAttendee(new Attendee(null, "Ann", "ann@x.com", null, null, null, null));
        assertThrows(DomainException.class,
                () -> event.addAttendee(new Attendee(null, "Ann B", "ANN@x.com", null, null, null, null)));
    }
}
