package com.eventpilot.application.command;

import com.eventpilot.domain.AgendaItem;
import com.eventpilot.domain.Event;

/** Adds one agenda item (F04, F12). */
public class AddAgendaItemCommand implements EventCommand {

    private AgendaItem item;
    private String assignedId;

    private AddAgendaItemCommand() {
    }

    public AddAgendaItemCommand(AgendaItem item) {
        this.item = item.copy();
    }

    @Override
    public void execute(Event event) {
        AgendaItem copy = item.copy();
        if (assignedId != null && copy.id() == null) {
            copy = new AgendaItem(assignedId, copy.title(), copy.type(), copy.start(), copy.end(), copy.room(),
                    copy.speakerId(), copy.expectedAudience());
        }
        assignedId = event.agenda().add(copy).id();
    }

    @Override
    public void undo(Event event) {
        event.agenda().remove(assignedId);
    }

    public AgendaItem item() {
        return item.copy();
    }

    @Override
    public String describe() {
        return "Add " + item.type().name().toLowerCase() + " \"" + item.title() + "\" " + item.window()
                + (item.room() == null ? "" : " in " + item.room());
    }
}
