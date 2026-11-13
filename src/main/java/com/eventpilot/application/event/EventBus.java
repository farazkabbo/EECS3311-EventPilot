package com.eventpilot.application.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Observer pattern - the "Subject". The facade publishes one {@link DomainEvent}
 * after every saved change; views, the alert service and the FAQ indexer
 * subscribe without the facade knowing about them. A failing observer is
 * reported but never stops the others.
 */
public class EventBus {

    private final List<EventObserver> observers = new CopyOnWriteArrayList<>();

    public void subscribe(EventObserver observer) {
        if (!observers.contains(observer)) {
            observers.add(observer);
        }
    }

    public void unsubscribe(EventObserver observer) {
        observers.remove(observer);
    }

    public void publish(DomainEvent change) {
        for (EventObserver observer : observers) {
            try {
                observer.onEventChanged(change);
            } catch (RuntimeException e) {
                System.err.println("Observer " + observer.getClass().getSimpleName() + " failed: " + e.getMessage());
            }
        }
    }

    public int observerCount() {
        return observers.size();
    }
}
