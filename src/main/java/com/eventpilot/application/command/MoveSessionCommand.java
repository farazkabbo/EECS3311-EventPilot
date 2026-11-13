package com.eventpilot.application.command;

import com.eventpilot.domain.AgendaItem;
import com.eventpilot.domain.Event;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Moves an agenda item to a new time and optionally a new room (F05, F06, F12). */
public class MoveSessionCommand implements EventCommand {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private String itemId;
    private String title;
    private LocalDateTime newStart;
    private LocalDateTime newEnd;
    private String newRoom;
    private LocalDateTime oldStart;
    private LocalDateTime oldEnd;
    private String oldRoom;

    private MoveSessionCommand() {
    }

    public MoveSessionCommand(String itemId, String title, LocalDateTime newStart, LocalDateTime newEnd, String newRoom) {
        if (newStart == null || newEnd == null || !newEnd.isAfter(newStart)) {
            throw new IllegalArgumentException("new end must be after new start");
        }
        this.itemId = itemId;
        this.title = title;
        this.newStart = newStart;
        this.newEnd = newEnd;
        this.newRoom = newRoom;
    }

    @Override
    public void execute(Event event) {
        AgendaItem item = event.agenda().require(itemId);
        oldStart = item.start();
        oldEnd = item.end();
        oldRoom = item.room();
        item.reschedule(newStart, newEnd);
        if (newRoom != null) {
            item.moveToRoom(newRoom);
        }
    }

    @Override
    public void undo(Event event) {
        AgendaItem item = event.agenda().require(itemId);
        item.reschedule(oldStart, oldEnd);
        item.moveToRoom(oldRoom);
    }

    public String itemId() { return itemId; }
    public LocalDateTime newStart() { return newStart; }
    public LocalDateTime newEnd() { return newEnd; }
    public String newRoom() { return newRoom; }

    @Override
    public String describe() {
        return "Move \"" + title + "\" (" + itemId + ") to " + newStart.format(HM) + "-" + newEnd.format(HM)
                + (newRoom == null ? "" : " in " + newRoom);
    }
}
