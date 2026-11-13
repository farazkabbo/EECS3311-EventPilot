package com.eventpilot.domain;

import java.util.ArrayList;
import java.util.List;

/** A speaker with the time windows in which they are available. */
public class Speaker {

    private String id;
    private String name;
    private String topic;
    private String email;
    private List<TimeWindow> availability = new ArrayList<>();

    private Speaker() {
    }

    public Speaker(String id, String name, String topic, String email, List<TimeWindow> availability) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("speaker name is required");
        }
        this.id = id;
        this.name = name;
        this.topic = topic == null ? "" : topic;
        this.email = email;
        this.availability = new ArrayList<>(availability == null ? List.of() : availability);
    }

    /** True when the window lies completely inside one availability window (or no limits were given). */
    public boolean isAvailable(TimeWindow window) {
        if (availability.isEmpty()) {
            return true;
        }
        return availability.stream().anyMatch(a -> a.contains(window));
    }

    public String id() { return id; }
    public String name() { return name; }
    public String topic() { return topic; }
    public String email() { return email; }
    public List<TimeWindow> availability() { return List.copyOf(availability); }

    void setId(String id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return id + " " + name + (topic.isBlank() ? "" : " (" + topic + ")") + " available " + availability;
    }
}
