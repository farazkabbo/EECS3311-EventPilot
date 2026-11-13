package com.eventpilot.domain.state;

import com.eventpilot.domain.DomainException;
import com.eventpilot.domain.Event;
import java.util.EnumSet;
import java.util.Set;

/** The event was cancelled. It is read-only. */
public class CancelledState extends AbstractEventState {

    @Override
    public EventStatus status() {
        return EventStatus.CANCELLED;
    }

    @Override
    public void ensureEditable(Event event) {
        throw new DomainException("The event is cancelled and can no longer be changed.");
    }

    @Override
    public Set<LifecycleAction> allowedActions() {
        return EnumSet.noneOf(LifecycleAction.class);
    }

    @Override
    protected EventState next(Event event, LifecycleAction action) {
        throw new IllegalStateException("unreachable");
    }
}
