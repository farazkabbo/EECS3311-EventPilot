package com.eventpilot.application.command;

import com.eventpilot.domain.AgendaItem;
import com.eventpilot.domain.DomainException;
import com.eventpilot.domain.Event;

/** Assigns a speaker to a session (F05). */
public class AssignSpeakerCommand implements EventCommand {

    private String itemId;
    private String speakerId;
    private String speakerName;
    private String itemTitle;
    private String previousSpeakerId;

    private AssignSpeakerCommand() {
    }

    public AssignSpeakerCommand(String itemId, String itemTitle, String speakerId, String speakerName) {
        this.itemId = itemId;
        this.itemTitle = itemTitle;
        this.speakerId = speakerId;
        this.speakerName = speakerName;
    }

    @Override
    public void execute(Event event) {
        if (event.findSpeaker(speakerId).isEmpty()) {
            throw new DomainException("Unknown speaker " + speakerId);
        }
        AgendaItem item = event.agenda().require(itemId);
        previousSpeakerId = item.speakerId();
        item.assignSpeaker(speakerId);
    }

    @Override
    public void undo(Event event) {
        event.agenda().require(itemId).assignSpeaker(previousSpeakerId);
    }

    public String itemId() { return itemId; }
    public String speakerId() { return speakerId; }

    @Override
    public String describe() {
        return "Assign " + speakerName + " (" + speakerId + ") to \"" + itemTitle + "\" (" + itemId + ")";
    }
}
