package com.eventpilot.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** A closed-open time interval [start, end). */
public record TimeWindow(LocalDateTime start, LocalDateTime end) {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    public TimeWindow {
        if (start == null || end == null) {
            throw new IllegalArgumentException("start and end are required");
        }
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("end must be after start (" + start + " .. " + end + ")");
        }
    }

    public boolean overlaps(TimeWindow other) {
        return start.isBefore(other.end) && other.start.isBefore(end);
    }

    public boolean contains(TimeWindow other) {
        return !other.start.isBefore(start) && !other.end.isAfter(end);
    }

    public long minutes() {
        return Duration.between(start, end).toMinutes();
    }

    @Override
    public String toString() {
        return start.toLocalDate() + " " + start.format(HM) + "-" + end.format(HM);
    }
}
