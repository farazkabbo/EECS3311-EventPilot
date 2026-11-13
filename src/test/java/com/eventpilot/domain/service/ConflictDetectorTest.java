package com.eventpilot.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eventpilot.domain.AgendaItem;
import com.eventpilot.domain.AgendaItemType;
import com.eventpilot.domain.Conflict;
import com.eventpilot.domain.Event;
import com.eventpilot.domain.Room;
import com.eventpilot.domain.Speaker;
import com.eventpilot.domain.TimeWindow;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConflictDetectorTest {

    static final LocalDateTime NINE = LocalDateTime.of(2026, 12, 5, 9, 0);
    private Event event;
    private final ConflictDetector detector = new ConflictDetector();

    @BeforeEach
    void setUp() {
        event = new Event("Conf", "conference", NINE, NINE.plusHours(8), 100, 5000, "Toronto");
        event.setRooms(List.of(new Room("A", 100), new Room("B", 30)));
        event.addSpeaker(new Speaker(null, "Ada", "AI", null, List.of(new TimeWindow(NINE, NINE.plusHours(2)))));
    }

    private AgendaItem item(String title, int startHour, int endHour, String room, String speaker, int audience) {
        return event.agenda().add(new AgendaItem(null, title, AgendaItemType.SESSION,
                NINE.plusHours(startHour), NINE.plusHours(endHour), room, speaker, audience));
    }

    @Test
    void cleanAgendaHasNoConflicts() {
        item("One", 0, 1, "A", "s1", 80);
        item("Two", 1, 2, "A", null, 80);
        assertTrue(detector.detect(event).isEmpty());
    }

    @Test
    void detectsRoomOverlapAndSpeakerDoubleBooking() {
        item("One", 0, 1, "A", "s1", 50);
        item("Two", 0, 1, "A", "s1", 50);
        List<Conflict> conflicts = detector.detect(event);
        assertTrue(conflicts.stream().anyMatch(c -> c.type() == Conflict.Type.ROOM_OVERLAP));
        assertTrue(conflicts.stream().anyMatch(c -> c.type() == Conflict.Type.SPEAKER_DOUBLE_BOOKED));
    }

    @Test
    void detectsUnavailableSpeakerOutsideHoursAndSmallRoom() {
        item("Late talk", 3, 4, "B", "s1", 60);
        item("After hours", 8, 9, "A", null, 10);
        List<Conflict> conflicts = detector.detect(event);
        assertTrue(conflicts.stream().anyMatch(c -> c.type() == Conflict.Type.SPEAKER_UNAVAILABLE));
        assertTrue(conflicts.stream().anyMatch(c -> c.type() == Conflict.Type.OUTSIDE_EVENT_HOURS));
        Conflict small = conflicts.stream().filter(c -> c.type() == Conflict.Type.ROOM_TOO_SMALL).findFirst().orElseThrow();
        assertEquals(Conflict.Severity.HIGH, small.severity());
    }

    @Test
    void parallelBreaksInSameRoomAreAllowed() {
        event.agenda().add(new AgendaItem(null, "Coffee", AgendaItemType.BREAK, NINE.plusHours(2),
                NINE.plusHours(3), "A", null, 0));
        event.agenda().add(new AgendaItem(null, "Snacks", AgendaItemType.BREAK, NINE.plusHours(2),
                NINE.plusHours(3), "A", null, 0));
        assertTrue(detector.detect(event).isEmpty());
    }
}
