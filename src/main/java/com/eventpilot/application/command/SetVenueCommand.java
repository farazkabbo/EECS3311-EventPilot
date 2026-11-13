package com.eventpilot.application.command;

import com.eventpilot.domain.Event;
import com.eventpilot.domain.Room;
import com.eventpilot.domain.Venue;
import java.util.ArrayList;
import java.util.List;

/** Selects a venue (F03). Restores the previous venue and rooms on undo. */
public class SetVenueCommand implements EventCommand {

    private Venue venue;
    private String reason;
    private Venue previousVenue;
    private List<Room> previousRooms = new ArrayList<>();

    private SetVenueCommand() {
    }

    public SetVenueCommand(Venue venue, String reason) {
        this.venue = venue;
        this.reason = reason == null ? "" : reason;
    }

    @Override
    public void execute(Event event) {
        previousVenue = event.venue();
        previousRooms = new ArrayList<>(event.rooms());
        event.setVenue(venue);
    }

    @Override
    public void undo(Event event) {
        event.setVenue(previousVenue);
        event.setRooms(previousRooms);
    }

    public Venue venue() {
        return venue;
    }

    public String reason() {
        return reason;
    }

    @Override
    public String describe() {
        return String.format("Choose venue %s (%s, capacity %d, $%.0f/day)", venue.name(), venue.district(),
                venue.capacity(), venue.pricePerDay());
    }
}
