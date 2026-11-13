package com.eventpilot.domain.state;

import com.eventpilot.domain.Event;
import java.util.EnumSet;
import java.util.Set;

/** A newly created event. It can be edited but not announced. */
public class DraftState extends AbstractEventState {

    @Override
    public EventStatus status() {
        return EventStatus.DRAFT;
    }

    @Override
    public Set<LifecycleAction> allowedActions() {
        return EnumSet.of(LifecycleAction.START_PLANNING, LifecycleAction.CANCEL);
    }

    @Override
    protected EventState next(Event event, LifecycleAction action) {
        return action == LifecycleAction.START_PLANNING ? new PlanningState() : new CancelledState();
    }
}
