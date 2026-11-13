package com.eventpilot.agent.tools;

import com.eventpilot.domain.Event;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * What tools may look at during one agent run: a private copy of the event, and
 * the ids of venues the tools returned (used later to reject invented venues).
 */
public class ToolContext {

    private final Event eventCopy;
    private final Set<String> returnedVenueIds = new LinkedHashSet<>();

    public ToolContext(Event event) {
        this.eventCopy = event == null ? null : event.deepCopy();
    }

    public Event event() {
        if (eventCopy == null) {
            throw new IllegalStateException("This tool needs an event");
        }
        return eventCopy;
    }

    public void recordVenues(Set<String> ids) {
        returnedVenueIds.addAll(ids);
    }

    public Set<String> returnedVenueIds() {
        return Set.copyOf(returnedVenueIds);
    }
}
