package com.eventpilot.gui;

import com.eventpilot.domain.Event;
import com.eventpilot.domain.state.LifecycleAction;
import java.time.LocalDateTime;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

/** Event-level actions: create, lifecycle (UC15), undo/redo (UC14), venue recommendation (F03). */
public class EventController extends BaseController {

    public EventController(GuiContext ctx) {
        super(ctx);
    }

    public void onNewEventManual() {
        new NewEventDialog().showAndWait().ifPresent(d -> run("Created event", () -> {
            Event e = ctx.facade().createEvent(d.name(), d.type(), d.start(), d.end(), d.attendance(), d.budget(), d.city());
            ctx.selectedEventId().set(e.id());
        }));
    }

    public void onCreateManual(String name, String type, LocalDateTime start, LocalDateTime end, int attendance,
                               double budget, String city) {
        run("Created event", () -> ctx.selectedEventId().set(
                ctx.facade().createEvent(name, type, start, end, attendance, budget, city).id()));
    }

    public void onAction(LifecycleAction action) {
        requireEvent().ifPresent(id -> {
            if (action == LifecycleAction.CANCEL) {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "Cancel this event? It becomes read-only.", ButtonType.YES, ButtonType.NO);
                if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
                    return;
                }
            }
            run("Event is now " + action, () -> ctx.facade().transitionEvent(id, action));
        });
    }

    public void onDelete() {
        requireEvent().ifPresent(id -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Delete this event permanently?",
                    ButtonType.YES, ButtonType.NO);
            if (confirm.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
                run("Deleted event", () -> {
                    ctx.facade().deleteEvent(id);
                    ctx.selectedEventId().set(null);
                });
            }
        });
    }

    public void onUndo() {
        requireEvent().ifPresent(id -> run("Undo", () -> ctx.status().set(
                ctx.facade().undo(id).map(d -> "Undid: " + d).orElse("Nothing to undo"))));
    }

    public void onRedo() {
        requireEvent().ifPresent(id -> run("Redo", () -> ctx.status().set(
                ctx.facade().redo(id).map(d -> "Redid: " + d).orElse("Nothing to redo"))));
    }

    public void onRecommendVenues(String needs) {
        requireEvent().ifPresent(id -> runAsync("Agent is searching venues",
                () -> ctx.facade().recommendVenues(id, needs), this::reviewAndApply));
    }

    public void onAddNote(String note) {
        requireEvent().ifPresent(id -> run("Note added", () -> ctx.facade().addNote(id, note)));
    }
}
