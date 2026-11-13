package com.eventpilot.application.command;

import com.eventpilot.domain.AgendaItem;
import com.eventpilot.domain.Event;

/** Removes an agenda item; remembers it so undo can restore it (F12). */
public class RemoveAgendaItemCommand implements EventCommand {

    private String itemId;
    private String title;
    private AgendaItem removed;

    private RemoveAgendaItemCommand() {
    }

    public RemoveAgendaItemCommand(String itemId, String title) {
        this.itemId = itemId;
        this.title = title;
    }

    @Override
    public void execute(Event event) {
        removed = event.agenda().require(itemId).copy();
        event.agenda().remove(itemId);
    }

    @Override
    public void undo(Event event) {
        if (removed != null) {
            event.agenda().add(removed.copy());
        }
    }

    public String itemId() {
        return itemId;
    }

    @Override
    public String describe() {
        return "Remove \"" + title + "\" (" + itemId + ")";
    }
}
