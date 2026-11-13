package com.eventpilot.domain;

import java.time.LocalDateTime;
import java.util.Objects;

/** One entry in the agenda: a session, keynote, break, meal, etc. */
public class AgendaItem {

    private String id;
    private String title;
    private AgendaItemType type;
    private LocalDateTime start;
    private LocalDateTime end;
    private String room;
    private String speakerId;
    private int expectedAudience;

    private AgendaItem() {
    }

    public AgendaItem(String id, String title, AgendaItemType type, LocalDateTime start, LocalDateTime end,
                      String room, String speakerId, int expectedAudience) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("agenda item title is required");
        }
        new TimeWindow(start, end); // validates the times
        this.id = id;
        this.title = title;
        this.type = Objects.requireNonNull(type);
        this.start = start;
        this.end = end;
        this.room = room;
        this.speakerId = speakerId;
        this.expectedAudience = Math.max(0, expectedAudience);
    }

    public AgendaItem copy() {
        return new AgendaItem(id, title, type, start, end, room, speakerId, expectedAudience);
    }

    public TimeWindow window() {
        return new TimeWindow(start, end);
    }

    public void reschedule(LocalDateTime newStart, LocalDateTime newEnd) {
        new TimeWindow(newStart, newEnd);
        this.start = newStart;
        this.end = newEnd;
    }

    public void assignSpeaker(String newSpeakerId) {
        this.speakerId = newSpeakerId;
    }

    public void moveToRoom(String newRoom) {
        this.room = newRoom;
    }

    public void rename(String newTitle) {
        if (newTitle == null || newTitle.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
        this.title = newTitle;
    }

    void setId(String id) {
        this.id = id;
    }

    public String id() { return id; }
    public String title() { return title; }
    public AgendaItemType type() { return type; }
    public LocalDateTime start() { return start; }
    public LocalDateTime end() { return end; }
    public String room() { return room; }
    public String speakerId() { return speakerId; }
    public int expectedAudience() { return expectedAudience; }

    @Override
    public String toString() {
        return id + " " + window() + " " + type + " \"" + title + "\"" + (room == null ? "" : " @" + room)
                + (speakerId == null ? "" : " [speaker " + speakerId + "]");
    }
}
