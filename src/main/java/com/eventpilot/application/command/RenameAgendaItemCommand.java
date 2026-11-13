package com.eventpilot.application.command;

import com.eventpilot.domain.AgendaItem;
import com.eventpilot.domain.Event;

/** Renames an agenda item (added in Stage 2 for F12 "rename the panel to ..."). */
public class RenameAgendaItemCommand implements EventCommand {

    private String itemId;
    private String newTitle;
    private String oldTitle;

    private RenameAgendaItemCommand() {
    }

    public RenameAgendaItemCommand(String itemId, String newTitle) {
        if (newTitle == null || newTitle.isBlank()) {
            throw new IllegalArgumentException("new title is required");
        }
        this.itemId = itemId;
        this.newTitle = newTitle;
    }

    @Override
    public void execute(Event event) {
        AgendaItem item = event.agenda().require(itemId);
        oldTitle = item.title();
        item.rename(newTitle);
    }

    @Override
    public void undo(Event event) {
        event.agenda().require(itemId).rename(oldTitle);
    }

    @Override
    public String describe() {
        return "Rename " + itemId + (oldTitle == null ? "" : " \"" + oldTitle + "\"") + " to \"" + newTitle + "\"";
    }
}
