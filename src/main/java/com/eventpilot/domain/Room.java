package com.eventpilot.domain;

/** A room the agenda can use, with its seating capacity. */
public record Room(String name, int capacity) {

    public Room {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("room name is required");
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("room capacity must be positive");
        }
    }
}
