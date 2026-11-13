package com.eventpilot.domain;

import com.eventpilot.domain.state.EventState;
import com.eventpilot.domain.state.EventStatus;
import com.eventpilot.domain.state.LifecycleAction;
import com.eventpilot.infra.Json;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The aggregate root: an event with its agenda, people, budget, catering, tasks
 * and invitations. It is the "Context" of the State pattern - lifecycle questions
 * are forwarded to its current {@link EventState} - and the "Receiver" of commands.
 */
public class Event {

    private String id;
    private String name;
    private String type;
    private LocalDateTime start;
    private LocalDateTime end;
    private int expectedAttendance;
    private String city;
    private EventStatus status = EventStatus.DRAFT;
    @JsonIgnore
    private transient EventState state;
    private Venue venue;
    private final List<Room> rooms = new ArrayList<>();
    private final Agenda agenda = new Agenda();
    private final List<Attendee> attendees = new ArrayList<>();
    private final List<Speaker> speakers = new ArrayList<>();
    private final Budget budget = new Budget();
    private CateringPlan cateringPlan;
    private final List<EventTask> tasks = new ArrayList<>();
    private final List<Invitation> invitations = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();

    private Event() {
    }

    public Event(String name, String type, LocalDateTime start, LocalDateTime end, int expectedAttendance,
                 double budgetLimit, String city) {
        if (name == null || name.isBlank()) {
            throw new DomainException("Event name is required.");
        }
        if (start == null || end == null || !end.isAfter(start)) {
            throw new DomainException("The event must end after it starts.");
        }
        if (expectedAttendance <= 0) {
            throw new DomainException("Expected attendance must be greater than 0.");
        }
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.name = name.trim();
        this.type = type == null || type.isBlank() ? "event" : type.trim();
        this.start = start;
        this.end = end;
        this.expectedAttendance = expectedAttendance;
        this.city = city == null ? "" : city.trim();
        this.budget.setLimit(budgetLimit);
        this.rooms.add(new Room("Main Hall", expectedAttendance));
        this.state = EventState.of(status);
    }

    // ---- State pattern: forward lifecycle questions to the current state ----

    public EventState state() {
        if (state == null) {
            state = EventState.of(status);
        }
        return state;
    }

    public void setState(EventState newState) {
        this.state = newState;
        this.status = newState.status();
    }

    public void transition(LifecycleAction action) {
        state().onAction(this, action);
    }

    public void ensureEditable() {
        state().ensureEditable(this);
    }

    public void ensureCanInvite() {
        state().ensureCanInvite(this);
    }

    public EventStatus status() {
        return status;
    }

    // ---- Copying (used to test proposed changes safely) ----

    public Event deepCopy() {
        Event copy = Json.copy(this, Event.class);
        copy.state = EventState.of(copy.status);
        return copy;
    }

    // ---- Venue and rooms ----

    public Venue venue() {
        return venue;
    }

    /** Sets the venue; the venue's rooms replace the event rooms when it has any. */
    public void setVenue(Venue newVenue) {
        this.venue = newVenue;
        if (newVenue != null && !newVenue.rooms().isEmpty()) {
            rooms.clear();
            rooms.addAll(newVenue.rooms());
        }
    }

    public List<Room> rooms() {
        return List.copyOf(rooms);
    }

    public void setRooms(List<Room> newRooms) {
        rooms.clear();
        rooms.addAll(newRooms);
    }

    public Optional<Room> room(String roomName) {
        return rooms.stream().filter(r -> r.name().equalsIgnoreCase(roomName)).findFirst();
    }

    // ---- People ----

    public Attendee addAttendee(Attendee attendee) {
        if (findAttendeeByEmail(attendee.email()).isPresent()) {
            throw new DomainException("An attendee with e-mail " + attendee.email() + " already exists.");
        }
        if (attendee.id() == null) {
            attendee.setId("p" + (maxNumericId(attendees.stream().map(Attendee::id).toList(), "p") + 1));
        }
        attendees.add(attendee);
        return attendee;
    }

    public boolean removeAttendee(String attendeeId) {
        return attendees.removeIf(a -> a.id().equals(attendeeId));
    }

    public List<Attendee> attendees() {
        return List.copyOf(attendees);
    }

    public Optional<Attendee> findAttendee(String idOrEmail) {
        return attendees.stream()
                .filter(a -> a.id().equalsIgnoreCase(idOrEmail) || a.email().equalsIgnoreCase(idOrEmail))
                .findFirst();
    }

    public Optional<Attendee> findAttendeeByEmail(String email) {
        return attendees.stream().filter(a -> a.email().equalsIgnoreCase(email)).findFirst();
    }

    public Speaker addSpeaker(Speaker speaker) {
        if (speaker.id() == null) {
            speaker.setId("s" + (maxNumericId(speakers.stream().map(Speaker::id).toList(), "s") + 1));
        }
        speakers.add(speaker);
        return speaker;
    }

    public List<Speaker> speakers() {
        return List.copyOf(speakers);
    }

    public Optional<Speaker> findSpeaker(String idOrName) {
        return speakers.stream()
                .filter(s -> s.id().equalsIgnoreCase(idOrName) || s.name().equalsIgnoreCase(idOrName))
                .findFirst();
    }

    // ---- Tasks ----

    public EventTask addTask(EventTask task) {
        if (task.id() == null) {
            task.setId(nextTaskId());
        } else if (findTask(task.id()).isPresent()) {
            throw new DomainException("Task id already exists: " + task.id());
        }
        tasks.add(task);
        return task;
    }

    public String nextTaskId() {
        return "t" + (maxNumericId(tasks.stream().map(EventTask::id).toList(), "t") + 1);
    }

    public boolean removeTask(String taskId) {
        return tasks.removeIf(t -> t.id().equals(taskId));
    }

    public List<EventTask> tasks() {
        return List.copyOf(tasks);
    }

    public Optional<EventTask> findTask(String taskId) {
        return tasks.stream().filter(t -> t.id().equalsIgnoreCase(taskId)).findFirst();
    }

    // ---- Invitations ----

    public void replaceInvitations(List<Invitation> drafts) {
        invitations.removeIf(i -> i.status() != Invitation.Status.SENT);
        invitations.addAll(drafts);
    }

    public List<Invitation> invitations() {
        return List.copyOf(invitations);
    }

    // ---- Notes (organizer information used by the FAQ agent) ----

    public void addNote(String note) {
        if (note == null || note.isBlank()) {
            throw new DomainException("Note text is required.");
        }
        notes.add(note.trim());
    }

    public List<String> notes() {
        return List.copyOf(notes);
    }

    // ---- Simple properties ----

    public void reschedule(LocalDateTime newStart, LocalDateTime newEnd) {
        if (newStart == null || newEnd == null || !newEnd.isAfter(newStart)) {
            throw new DomainException("The event must end after it starts.");
        }
        this.start = newStart;
        this.end = newEnd;
    }

    public TimeWindow window() {
        return new TimeWindow(start, end);
    }

    public String id() { return id; }
    public String name() { return name; }
    public String type() { return type; }
    public LocalDateTime start() { return start; }
    public LocalDateTime end() { return end; }
    public int expectedAttendance() { return expectedAttendance; }
    public String city() { return city; }
    public Agenda agenda() { return agenda; }
    public Budget budget() { return budget; }
    public CateringPlan cateringPlan() { return cateringPlan; }

    public void setCateringPlan(CateringPlan plan) {
        this.cateringPlan = plan;
    }

    public void setExpectedAttendance(int attendance) {
        if (attendance <= 0) {
            throw new DomainException("Expected attendance must be greater than 0.");
        }
        this.expectedAttendance = attendance;
    }

    private static int maxNumericId(List<String> ids, String prefix) {
        int max = 0;
        for (String id : ids) {
            if (id != null && id.startsWith(prefix)) {
                try {
                    max = Math.max(max, Integer.parseInt(id.substring(prefix.length())));
                } catch (NumberFormatException ignored) {
                    // ignore custom ids
                }
            }
        }
        return max;
    }

    @Override
    public String toString() {
        return id + " \"" + name + "\" (" + type + ") " + window() + " " + status
                + (city.isBlank() ? "" : " in " + city);
    }
}
