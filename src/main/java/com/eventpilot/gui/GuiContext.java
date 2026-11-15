package com.eventpilot.gui;

import com.eventpilot.application.EventPlannerFacade;
import com.eventpilot.domain.Event;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.stage.Stage;

/** State shared by all views and controllers: the facade, the selected event and the status line. */
public class GuiContext {

    private final EventPlannerFacade facade;
    private final ObjectProperty<String> selectedEventId = new SimpleObjectProperty<>();
    private final StringProperty status = new SimpleStringProperty("Ready");
    private final SimpleObjectProperty<Boolean> busy = new SimpleObjectProperty<>(false);
    private final BooleanProperty darkMode = new SimpleBooleanProperty(false);
    private Consumer<String> toaster = msg -> { };
    private Stage stage;

    public GuiContext(EventPlannerFacade facade) {
        this.facade = facade;
    }

    /** Light/dark theme switch; the stylesheet swaps its colour palette on the root "dark" class. */
    public BooleanProperty darkMode() {
        return darkMode;
    }

    /** Shows a short, self-dismissing notification in the corner of the window. */
    public void toast(String message) {
        toaster.accept(message);
    }

    void setToaster(Consumer<String> toaster) {
        this.toaster = toaster;
    }

    public EventPlannerFacade facade() {
        return facade;
    }

    public ObjectProperty<String> selectedEventId() {
        return selectedEventId;
    }

    public Optional<Event> selectedEvent() {
        String id = selectedEventId.get();
        if (id == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(facade.getEvent(id));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    public StringProperty status() {
        return status;
    }

    public SimpleObjectProperty<Boolean> busy() {
        return busy;
    }

    public Stage stage() {
        return stage;
    }

    void setStage(Stage stage) {
        this.stage = stage;
    }
}
