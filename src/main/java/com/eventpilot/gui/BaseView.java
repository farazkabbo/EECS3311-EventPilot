package com.eventpilot.gui;

import com.eventpilot.application.event.DomainEvent;
import com.eventpilot.application.event.EventObserver;
import com.eventpilot.domain.Event;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.layout.BorderPane;

/**
 * MVC view base and concrete Observer: subscribes to the EventBus through the
 * facade and redraws itself (on the JavaFX thread) whenever the selected event
 * changes - whoever made the change (this view, another view, or the chat).
 */
public abstract class BaseView extends BorderPane implements EventObserver {

    protected final GuiContext ctx;

    protected BaseView(GuiContext ctx) {
        this.ctx = ctx;
        setPadding(new Insets(10));
        ctx.facade().subscribe(this);
        ctx.selectedEventId().addListener((obs, old, id) -> refreshNow());
    }

    @Override
    public void onEventChanged(DomainEvent change) {
        if (change.type() == DomainEvent.Type.ALERTS_UPDATED) {
            return; // views compute what they show from the event itself
        }
        String id = ctx.selectedEventId().get();
        if (id != null && id.equals(change.eventId()) || change.type() == DomainEvent.Type.EVENT_CREATED
                || change.type() == DomainEvent.Type.EVENT_DELETED) {
            Platform.runLater(this::refreshNow);
        }
    }

    public final void refreshNow() {
        refresh(ctx.selectedEvent().orElse(null));
    }

    /** Redraws the view for the given event (null when no event is selected). */
    protected abstract void refresh(Event event);
}
