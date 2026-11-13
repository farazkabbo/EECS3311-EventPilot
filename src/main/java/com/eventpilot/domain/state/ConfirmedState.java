package com.eventpilot.domain.state;

import com.eventpilot.domain.Event;
import java.util.EnumSet;
import java.util.Set;

/** Venue and agenda are fixed; invitations can go out. Can be reopened for changes. */
public class ConfirmedState extends AbstractEventState {

    @Override
    public EventStatus status() {
        return EventStatus.CONFIRMED;
    }

    @Override
    public void ensureCanInvite(Event event) {
        // allowed
    }

    @Override
    public Set<LifecycleAction> allowedActions() {
        return EnumSet.of(LifecycleAction.REOPEN, LifecycleAction.COMPLETE, LifecycleAction.CANCEL);
    }

    @Override
    protected EventState next(Event event, LifecycleAction action) {
        return switch (action) {
            case REOPEN -> new PlanningState();
            case COMPLETE -> new CompletedState();
            default -> new CancelledState();
        };
    }
}
