package com.eventpilot.application.event;

import com.eventpilot.domain.Event;

/**
 * What changed. Carries the current event object so observers do not need to
 * reload it; {@code event} is null only for {@link Type#EVENT_DELETED}.
 */
public record DomainEvent(Type type, String eventId, Event event, String detail) {

    public enum Type {
        EVENT_CREATED, EVENT_CHANGED, EVENT_DELETED, STATE_CHANGED,
        AGENDA_CHANGED, ATTENDEES_CHANGED, BUDGET_CHANGED, CATERING_CHANGED,
        TASKS_CHANGED, INVITATIONS_CHANGED, ALERTS_UPDATED, FAQ_ESCALATED
    }

    public static DomainEvent of(Type type, Event event, String detail) {
        return new DomainEvent(type, event == null ? null : event.id(), event, detail == null ? "" : detail);
    }
}
