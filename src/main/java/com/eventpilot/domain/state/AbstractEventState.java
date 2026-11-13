package com.eventpilot.domain.state;

import com.eventpilot.domain.DomainException;
import com.eventpilot.domain.Event;

/** Shared defaults for the concrete states: editable, no invitations, every action refused. */
abstract class AbstractEventState implements EventState {

    @Override
    public void ensureEditable(Event event) {
        // editable by default
    }

    @Override
    public void ensureCanInvite(Event event) {
        throw new DomainException("Invitations can only be drafted or sent while the event is in PLANNING or "
                + "CONFIRMED (it is " + status() + ")."
                + (status() == EventStatus.DRAFT ? " Start planning first." : ""));
    }

    @Override
    public void onAction(Event event, LifecycleAction action) {
        if (!allowedActions().contains(action)) {
            throw new DomainException("Cannot " + action.name().toLowerCase().replace('_', ' ')
                    + " an event that is " + status() + ". Allowed: " + allowedActions());
        }
        event.setState(next(event, action));
    }

    /** Returns the next state for an allowed action (may still refuse by throwing). */
    protected abstract EventState next(Event event, LifecycleAction action);

    @Override
    public String toString() {
        return status().name();
    }
}
