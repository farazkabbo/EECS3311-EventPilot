package com.eventpilot.gui;

import com.eventpilot.application.event.DomainEvent;
import com.eventpilot.domain.Event;
import com.eventpilot.domain.state.LifecycleAction;
import java.util.List;
import java.util.Objects;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * Main window: event selector, lifecycle buttons and undo/redo on top; the
 * feature tabs in the middle; the agent chat on the right; status at the bottom.
 */
public class MainWindow extends BorderPane {

    private final GuiContext ctx;
    private final EventController events;
    private final ComboBox<Event> selector = new ComboBox<>();
    private final Label stateBadge = new Label();
    private final HBox lifecycleButtons = new HBox(4);
    private boolean updatingSelector;

    public MainWindow(GuiContext ctx) {
        this.ctx = ctx;
        this.events = new EventController(ctx);
        AgendaController agenda = new AgendaController(ctx);
        AttendeeController attendees = new AttendeeController(ctx);

        TabPane tabs = new TabPane(
                tab("Dashboard", new DashboardView(ctx, events)),
                tab("Agenda", new AgendaView(ctx, agenda)),
                tab("Venue", new VenueView(ctx, events)),
                tab("Budget", new BudgetView(ctx, new BudgetController(ctx))),
                tab("Attendees", new AttendeeView(ctx, attendees)),
                tab("Catering", new CateringView(ctx, attendees)),
                tab("Invitations", new InvitationView(ctx, attendees)),
                tab("Tasks", new TaskBoardView(ctx, new TaskController(ctx))));
        setCenter(tabs);
        setRight(new ChatPanel(ctx, new AgentChatController(ctx)));
        setTop(buildTopBar());
        setBottom(buildStatusBar());

        ctx.facade().subscribe(change -> {
            if (change.type() != DomainEvent.Type.ALERTS_UPDATED) {
                Platform.runLater(this::reloadEvents);
            }
        });
        ctx.selectedEventId().addListener((o, old, id) -> reloadEvents());
        reloadEvents();
        List<Event> all = ctx.facade().listEvents();
        if (!all.isEmpty()) {
            ctx.selectedEventId().set(all.get(0).id());
        }
    }

    private static Tab tab(String name, javafx.scene.Node content) {
        Tab t = new Tab(name, content);
        t.setClosable(false);
        return t;
    }

    private HBox buildTopBar() {
        selector.setPrefWidth(320);
        selector.setPromptText("Select an event");
        selector.setCellFactory(l -> new EventCell());
        selector.setButtonCell(new EventCell());
        selector.valueProperty().addListener((o, old, e) -> {
            if (!updatingSelector && e != null && !Objects.equals(e.id(), ctx.selectedEventId().get())) {
                ctx.selectedEventId().set(e.id());
            }
        });
        Button newEvent = new Button("New event...");
        newEvent.setOnAction(e -> events.onNewEventManual());
        Button delete = new Button("Delete");
        delete.setOnAction(e -> events.onDelete());
        Button undo = new Button("Undo");
        undo.setOnAction(e -> events.onUndo());
        Button redo = new Button("Redo");
        redo.setOnAction(e -> events.onRedo());
        sceneProperty().addListener((o, old, scene) -> {
            if (scene != null) {
                scene.getAccelerators().put(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN), events::onUndo);
                scene.getAccelerators().put(new KeyCodeCombination(KeyCode.Y, KeyCombination.SHORTCUT_DOWN), events::onRedo);
            }
        });
        stateBadge.getStyleClass().add("state-badge");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(8, new Label("Event"), selector, newEvent, delete, stateBadge, lifecycleButtons, spacer, undo, redo);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8));
        bar.getStyleClass().add("top-bar");
        return bar;
    }

    private HBox buildStatusBar() {
        Label status = new Label();
        status.textProperty().bind(ctx.status());
        ProgressIndicator busy = new ProgressIndicator();
        busy.setPrefSize(16, 16);
        busy.visibleProperty().bind(ctx.busy().isEqualTo(true));
        Label ai = new Label("AI: " + ctx.facade().aiDescription());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(8, busy, status, spacer, ai);
        bar.setPadding(new Insets(4, 8, 4, 8));
        bar.getStyleClass().add("status-bar");
        return bar;
    }

    private void reloadEvents() {
        updatingSelector = true;
        try {
            List<Event> all = ctx.facade().listEvents();
            selector.getItems().setAll(all);
            String id = ctx.selectedEventId().get();
            Event selected = all.stream().filter(e -> e.id().equals(id)).findFirst().orElse(null);
            selector.setValue(selected);
            stateBadge.setText(selected == null ? "" : selected.status().name());
            lifecycleButtons.getChildren().clear();
            if (selected != null) {
                for (LifecycleAction a : selected.state().allowedActions()) {
                    Button b = new Button(label(a));
                    b.setOnAction(e -> events.onAction(a));
                    lifecycleButtons.getChildren().add(b);
                }
            }
        } finally {
            updatingSelector = false;
        }
    }

    private static String label(LifecycleAction a) {
        return switch (a) {
            case START_PLANNING -> "Start planning";
            case CONFIRM -> "Confirm";
            case REOPEN -> "Reopen";
            case COMPLETE -> "Complete";
            case CANCEL -> "Cancel event";
        };
    }

    private static final class EventCell extends ListCell<Event> {
        @Override
        protected void updateItem(Event e, boolean empty) {
            super.updateItem(e, empty);
            setText(empty || e == null ? null : e.name() + " - " + e.start().toLocalDate() + " [" + e.id() + "]");
        }
    }
}
