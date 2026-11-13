package com.eventpilot.domain.state;

import com.eventpilot.domain.DomainException;
import com.eventpilot.domain.Event;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** The event is being planned: editable, and invitations may be sent. */
public class PlanningState extends AbstractEventState {

    @Override
    public EventStatus status() {
        return EventStatus.PLANNING;
    }

    @Override
    public void ensureCanInvite(Event event) {
        // allowed
    }

    @Override
    public Set<LifecycleAction> allowedActions() {
        return EnumSet.of(LifecycleAction.CONFIRM, LifecycleAction.CANCEL);
    }

    @Override
    protected EventState next(Event event, LifecycleAction action) {
        if (action == LifecycleAction.CANCEL) {
            return new CancelledState();
        }
        List<String> missing = new ArrayList<>();
        if (event.venue() == null) {
            missing.add("choose a venue");
        }
        if (event.agenda().isEmpty()) {
            missing.add("create an agenda");
        }
        if (!missing.isEmpty()) {
            throw new DomainException("Cannot confirm yet: " + String.join(" and ", missing) + ".");
        }
        return new ConfirmedState();
    }
}
