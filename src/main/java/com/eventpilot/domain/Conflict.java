package com.eventpilot.domain;

import java.util.List;

/** A scheduling problem found by the ConflictDetector. */
public record Conflict(Type type, Severity severity, String message, List<String> itemIds) {

    public enum Type { ROOM_OVERLAP, SPEAKER_DOUBLE_BOOKED, SPEAKER_UNAVAILABLE, ROOM_TOO_SMALL, OUTSIDE_EVENT_HOURS }

    public enum Severity { LOW, MEDIUM, HIGH }

    public Conflict {
        itemIds = List.copyOf(itemIds);
    }

    public boolean isBlocking() {
        return severity == Severity.HIGH;
    }

    @Override
    public String toString() {
        return "[" + severity + "] " + type + ": " + message;
    }
}
