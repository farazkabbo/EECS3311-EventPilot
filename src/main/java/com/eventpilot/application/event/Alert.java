package com.eventpilot.application.event;

/** A notice for the organizer shown on the Dashboard and by `eventpilot alerts`. */
public record Alert(Level level, String category, String message) {

    public enum Level { INFO, WARNING, CRITICAL }

    @Override
    public String toString() {
        return "[" + level + "] " + category + ": " + message;
    }
}
