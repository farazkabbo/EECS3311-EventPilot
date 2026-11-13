package com.eventpilot.domain.service;

import com.eventpilot.domain.AgendaItem;
import com.eventpilot.domain.Conflict;
import com.eventpilot.domain.Conflict.Severity;
import com.eventpilot.domain.Conflict.Type;
import com.eventpilot.domain.Event;
import com.eventpilot.domain.Room;
import com.eventpilot.domain.Speaker;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Deterministic rule engine (F06). Finds room overlaps, speaker double-bookings,
 * speakers scheduled outside their availability, rooms that are too small, and
 * items outside the event hours. No AI is involved.
 */
public class ConflictDetector {

    public List<Conflict> detect(Event event) {
        List<Conflict> conflicts = new ArrayList<>();
        List<AgendaItem> items = event.agenda().items();

        for (AgendaItem item : items) {
            if (!event.window().contains(item.window())) {
                conflicts.add(new Conflict(Type.OUTSIDE_EVENT_HOURS, Severity.HIGH,
                        "\"" + item.title() + "\" (" + item.window() + ") is outside the event hours " + event.window(),
                        List.of(item.id())));
            }
            checkRoomCapacity(event, item).ifPresent(conflicts::add);
            checkSpeakerAvailability(event, item).ifPresent(conflicts::add);
        }

        for (int i = 0; i < items.size(); i++) {
            for (int j = i + 1; j < items.size(); j++) {
                AgendaItem a = items.get(i);
                AgendaItem b = items.get(j);
                if (!a.window().overlaps(b.window())) {
                    continue;
                }
                if (a.room() != null && a.room().equalsIgnoreCase(b.room() == null ? "" : b.room())
                        && !(a.type().isMealOrBreak() && b.type().isMealOrBreak())) {
                    conflicts.add(new Conflict(Type.ROOM_OVERLAP, Severity.HIGH,
                            "\"" + a.title() + "\" and \"" + b.title() + "\" overlap in " + a.room(),
                            List.of(a.id(), b.id())));
                }
                if (a.speakerId() != null && a.speakerId().equals(b.speakerId())) {
                    String speaker = event.findSpeaker(a.speakerId()).map(Speaker::name).orElse(a.speakerId());
                    conflicts.add(new Conflict(Type.SPEAKER_DOUBLE_BOOKED, Severity.HIGH,
                            speaker + " is booked for both \"" + a.title() + "\" and \"" + b.title() + "\"",
                            List.of(a.id(), b.id())));
                }
            }
        }
        return conflicts;
    }

    public boolean hasBlocking(Event event) {
        return detect(event).stream().anyMatch(Conflict::isBlocking);
    }

    private Optional<Conflict> checkRoomCapacity(Event event, AgendaItem item) {
        if (item.room() == null || item.expectedAudience() <= 0) {
            return Optional.empty();
        }
        Optional<Room> room = event.room(item.room());
        if (room.isEmpty()) {
            return Optional.of(new Conflict(Type.ROOM_TOO_SMALL, Severity.MEDIUM,
                    "\"" + item.title() + "\" uses unknown room " + item.room(), List.of(item.id())));
        }
        if (room.get().capacity() < item.expectedAudience()) {
            Severity severity = item.expectedAudience() > room.get().capacity() * 1.2 ? Severity.HIGH : Severity.MEDIUM;
            return Optional.of(new Conflict(Type.ROOM_TOO_SMALL, severity,
                    "\"" + item.title() + "\" expects " + item.expectedAudience() + " people but " + item.room()
                            + " seats " + room.get().capacity(), List.of(item.id())));
        }
        return Optional.empty();
    }

    private Optional<Conflict> checkSpeakerAvailability(Event event, AgendaItem item) {
        if (item.speakerId() == null) {
            return Optional.empty();
        }
        Optional<Speaker> speaker = event.findSpeaker(item.speakerId());
        if (speaker.isEmpty()) {
            return Optional.of(new Conflict(Type.SPEAKER_UNAVAILABLE, Severity.HIGH,
                    "\"" + item.title() + "\" refers to unknown speaker " + item.speakerId(), List.of(item.id())));
        }
        if (!speaker.get().isAvailable(item.window())) {
            return Optional.of(new Conflict(Type.SPEAKER_UNAVAILABLE, Severity.HIGH,
                    speaker.get().name() + " is not available for \"" + item.title() + "\" at " + item.window(),
                    List.of(item.id())));
        }
        return Optional.empty();
    }
}
