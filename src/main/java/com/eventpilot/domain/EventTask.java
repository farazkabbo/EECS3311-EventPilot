package com.eventpilot.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** A planning task with an owner, deadline and dependencies. */
public class EventTask {

    public enum Status { TODO, IN_PROGRESS, DONE }

    private String id;
    private String title;
    private String owner;
    private LocalDate deadline;
    private List<String> dependsOn = new ArrayList<>();
    private Status status;

    private EventTask() {
    }

    public EventTask(String id, String title, String owner, LocalDate deadline, List<String> dependsOn, Status status) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("task title is required");
        }
        if (deadline == null) {
            throw new IllegalArgumentException("task deadline is required");
        }
        this.id = id;
        this.title = title;
        this.owner = owner == null || owner.isBlank() ? "organizer" : owner;
        this.deadline = deadline;
        this.dependsOn = new ArrayList<>(dependsOn == null ? List.of() : dependsOn);
        this.status = status == null ? Status.TODO : status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    void setId(String id) {
        this.id = id;
    }

    public boolean isOverdue(LocalDate today) {
        return status != Status.DONE && deadline.isBefore(today);
    }

    public String id() { return id; }
    public String title() { return title; }
    public String owner() { return owner; }
    public LocalDate deadline() { return deadline; }
    public List<String> dependsOn() { return List.copyOf(dependsOn); }
    public Status status() { return status; }

    @Override
    public String toString() {
        return id + " [" + status + "] " + title + " (" + owner + ", due " + deadline + ")"
                + (dependsOn.isEmpty() ? "" : " after " + dependsOn);
    }
}
