package com.eventpilot.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgendaTest {

    private static final LocalDateTime NINE = LocalDateTime.of(2026, 11, 20, 9, 0);

    @Test
    void addAssignsSequentialIdsAndSortsByStart() {
        Agenda agenda = new Agenda();
        agenda.add(new AgendaItem(null, "Late", AgendaItemType.SESSION, NINE.plusHours(2), NINE.plusHours(3), "A", null, 10));
        agenda.add(new AgendaItem(null, "Early", AgendaItemType.KEYNOTE, NINE, NINE.plusHours(1), "A", null, 10));
        assertEquals(List.of("a2", "a1"), agenda.items().stream().map(AgendaItem::id).toList());
        assertEquals("a3", agenda.nextId());
    }

    @Test
    void rejectsInvalidTimes() {
        assertThrows(IllegalArgumentException.class,
                () -> new AgendaItem("x", "Bad", AgendaItemType.SESSION, NINE, NINE, "A", null, 0));
    }

    @Test
    void timeWindowOverlapAndContains() {
        TimeWindow day = new TimeWindow(NINE, NINE.plusHours(8));
        TimeWindow a = new TimeWindow(NINE, NINE.plusHours(1));
        TimeWindow b = new TimeWindow(NINE.plusHours(1), NINE.plusHours(2));
        assertFalse(a.overlaps(b), "touching windows do not overlap");
        assertTrue(day.contains(a));
        assertTrue(day.overlaps(b));
    }

    @Test
    void speakerAvailability() {
        Speaker s = new Speaker("s1", "Ada", "AI", null, List.of(new TimeWindow(NINE, NINE.plusHours(3))));
        assertTrue(s.isAvailable(new TimeWindow(NINE.plusHours(1), NINE.plusHours(2))));
        assertFalse(s.isAvailable(new TimeWindow(NINE.plusHours(2), NINE.plusHours(4))));
    }

    @Test
    void attendeeValidatesEmailAndNormalizesDiets() {
        assertThrows(IllegalArgumentException.class,
                () -> new Attendee(null, "Bob", "not-an-email", null, null, null, null));
        Attendee a = new Attendee(null, "Bob", "BOB@Example.com", null, List.of("Vegan", "none", "vegan"), null, null);
        assertEquals("bob@example.com", a.email());
        assertEquals(List.of("vegan"), a.dietary());
    }
}
