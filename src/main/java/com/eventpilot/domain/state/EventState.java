package com.eventpilot.domain.state;

import com.eventpilot.domain.Event;
import java.util.Set;

/**
 * State pattern - the "State" role. Each lifecycle stage of an event is one
 * class that decides what is allowed in that stage and which stage comes next.
 * {@link Event} (the "Context") forwards these questions to its current state.
 */
public interface EventState {

    EventStatus status();

    /** Throws {@link com.eventpilot.domain.DomainException} when the event cannot be changed. */
    void ensureEditable(Event event);

    /** Throws when invitations cannot be drafted or sent in this stage. */
    void ensureCanInvite(Event event);

    /** Handles a lifecycle action and installs the next state on the event (or refuses). */
    void onAction(Event event, LifecycleAction action);

    /** The actions this state accepts (used by the GUI/CLI to show the right buttons). */
    Set<LifecycleAction> allowedActions();

    static EventState of(EventStatus status) {
        return switch (status) {
            case DRAFT -> new DraftState();
            case PLANNING -> new PlanningState();
            case CONFIRMED -> new ConfirmedState();
            case COMPLETED -> new CompletedState();
            case CANCELLED -> new CancelledState();
        };
    }
}
