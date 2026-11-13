package com.eventpilot.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Event details extracted from a plain-language description (F01). Fields may be
 * missing; {@link #missingFields()} tells the agent which questions to ask.
 */
public class EventDraft {

    public String name;
    public String type;
    public LocalDateTime start;
    public LocalDateTime end;
    public Integer expectedAttendance;
    public Double budget;
    public String city;
    public List<String> questions = new ArrayList<>();

    public List<String> missingFields() {
        List<String> missing = new ArrayList<>();
        if (name == null || name.isBlank()) {
            missing.add("name");
        }
        if (start == null) {
            missing.add("start date and time");
        }
        if (end == null) {
            missing.add("end time");
        }
        if (expectedAttendance == null || expectedAttendance <= 0) {
            missing.add("expected attendance");
        }
        if (budget == null) {
            missing.add("budget");
        }
        return missing;
    }

    /** Contradictions that must be asked about, never guessed. */
    public List<String> contradictions() {
        List<String> problems = new ArrayList<>();
        if (start != null && end != null && !end.isAfter(start)) {
            problems.add("The end time (" + end + ") is not after the start time (" + start + "). When does it end?");
        }
        if (budget != null && budget < 0) {
            problems.add("The budget cannot be negative. What is the budget?");
        }
        if (expectedAttendance != null && expectedAttendance <= 0) {
            problems.add("How many people do you expect?");
        }
        return problems;
    }

    public boolean isComplete() {
        return missingFields().isEmpty() && contradictions().isEmpty();
    }

    public Event toEvent() {
        if (!isComplete()) {
            throw new DomainException("The event description is incomplete: missing " + missingFields());
        }
        return new Event(name, type, start, end, expectedAttendance, budget, city);
    }
}
