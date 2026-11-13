package com.eventpilot.application.event;

/** Observer pattern - the "Observer" role. */
@FunctionalInterface
public interface EventObserver {

    void onEventChanged(DomainEvent change);
}
