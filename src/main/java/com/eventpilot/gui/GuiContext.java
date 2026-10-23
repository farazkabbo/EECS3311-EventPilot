package com.eventpilot.gui;

import com.eventpilot.application.EventPlannerFacade;
import com.eventpilot.domain.Event;
import java.util.Optional;
import javafx.beans.property.ObjectProperty;
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
    private Stage stage;

    public GuiContext(EventPlannerFacade facade) {
        this.facade = facade;
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
