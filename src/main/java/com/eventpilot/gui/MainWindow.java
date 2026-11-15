package com.eventpilot.gui;

import com.eventpilot.application.event.DomainEvent;
import com.eventpilot.domain.Event;
import com.eventpilot.domain.state.LifecycleAction;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Main window: a navigation sidebar on the left; a header with the event selector, lifecycle
 * actions, undo/redo and theme toggle; the selected page in the middle (animated when it changes);
 * the agent chat on the right (can be hidden); a slim status bar at the bottom.
 */
public class MainWindow extends BorderPane {

    /** A page of the app: sidebar entry + header text + the view itself. */
    private record Page(String name, String glyph, String subtitle, Node view) {
    }

    private final GuiContext ctx;
    private final EventController events;
    private final ComboBox<Event> selector = new ComboBox<>();
    private final Label stateBadge = new Label();
    private final HBox lifecycleButtons = new HBox(6);
    private final Map<String, Page> pages = new LinkedHashMap<>();
    private final Map<String, ToggleButton> navButtons = new LinkedHashMap<>();
    private final StackPane content = new StackPane();
    private final Label pageTitle = new Label();
    private final Label pageSubtitle = new Label();
    private final ChatPanel chat;
    private boolean updatingSelector;

    public MainWindow(GuiContext ctx) {
        this.ctx = ctx;
        this.events = new EventController(ctx);
        getStyleClass().add("app-root");
        AgendaController agenda = new AgendaController(ctx);
        AttendeeController attendees = new AttendeeController(ctx);

        addPage("Dashboard", "▦", "Overview of the selected event and live alerts",
                new DashboardView(ctx, events));
        addPage("Agenda", "◷", "F04 generate · F05 schedule speakers · F06 conflicts · F12 change in plain language",
                new AgendaView(ctx, agenda));
        addPage("Venue", "⌂", "F03 venue recommendations with reasons and trade-offs",
                new VenueView(ctx, events));
        addPage("Budget", "$", "F07 budget plan, expenses and over-budget alerts",
                new BudgetView(ctx, new BudgetController(ctx)));
        addPage("Attendees", "☺", "F02 guest list, RSVPs and CSV import",
                new AttendeeView(ctx, attendees));
        addPage("Catering", "☕", "F08 dietary-aware catering plan",
                new CateringView(ctx, attendees));
        addPage("Invitations", "✉", "F10 draft and send invitations",
                new InvitationView(ctx, attendees));
        addPage("Tasks", "☑", "F09 task plan with dependencies — drag cards between columns",
                new TaskBoardView(ctx, new TaskController(ctx)));

        chat = new ChatPanel(ctx, new AgentChatController(ctx));
        pages.values().forEach(p -> Ui.decorateAiButtons(p.view()));

        setLeft(buildSidebar());
        setCenter(buildCenter());
        setRight(chat);
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
        showPage("Dashboard", false);
        installShortcuts();
    }

    private void addPage(String name, String glyph, String subtitle, Node view) {
        view.getStyleClass().add("page");
        pages.put(name, new Page(name, glyph, subtitle, view));
    }

    /** Names of all pages, in sidebar order (used by the screenshot tool). */
    List<String> pageNames() {
        return new ArrayList<>(pages.keySet());
    }

    void showPage(String name, boolean animate) {
        Page p = pages.get(name);
        if (p == null) {
            return;
        }
        navButtons.get(name).setSelected(true);
        pageTitle.setText(p.name());
        pageSubtitle.setText(p.subtitle());
        content.getChildren().setAll(p.view());
        if (animate) {
            Ui.enter(p.view(), 10, 200);
        }
    }

    private VBox buildSidebar() {
        Label logo = new Label("✦");
        logo.getStyleClass().add("brand-logo");
        Label brand = new Label("EventPilot");
        brand.getStyleClass().add("brand");
        Label tagline = new Label("AI event planning agent");
        tagline.getStyleClass().add("brand-tagline");
        VBox brandBox = new VBox(0, new HBox(8, logo, brand), tagline);
        brandBox.setPadding(new Insets(4, 8, 18, 8));

        ToggleGroup group = new ToggleGroup();
        VBox nav = new VBox(2);
        int i = 1;
        for (Page p : pages.values()) {
            Label icon = new Label(p.glyph());
            icon.getStyleClass().add("nav-icon");
            ToggleButton b = new ToggleButton(p.name(), icon);
            b.getStyleClass().add("nav-button");
            b.setToggleGroup(group);
            b.setMaxWidth(Double.MAX_VALUE);
            b.setAlignment(Pos.CENTER_LEFT);
            b.setTooltip(new Tooltip(p.subtitle() + "   (Ctrl+" + i++ + ")"));
            b.setOnAction(e -> {
                if (!b.isSelected()) {
                    b.setSelected(true); // keep one page selected at all times
                }
                showPage(p.name(), true);
            });
            navButtons.put(p.name(), b);
            nav.getChildren().add(b);
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        Label hint = new Label("Ctrl+1..8  switch page\nCtrl+Z / Ctrl+Y  undo / redo\nCtrl+N  new event\nCtrl+J  toggle agent chat");
        hint.getStyleClass().add("sidebar-hint");
        ToggleButton theme = new ToggleButton(ctx.darkMode().get() ? "☀  Light mode" : "☾  Dark mode");
        theme.getStyleClass().add("nav-button");
        theme.setMaxWidth(Double.MAX_VALUE);
        theme.setAlignment(Pos.CENTER_LEFT);
        theme.setSelected(false);
        theme.setOnAction(e -> {
            theme.setSelected(false);
            ctx.darkMode().set(!ctx.darkMode().get());
        });
        ctx.darkMode().addListener((o, old, dark) -> theme.setText(dark ? "☀  Light mode" : "☾  Dark mode"));
        VBox side = new VBox(4, brandBox, nav, spacer, theme, hint);
        side.getStyleClass().add("sidebar");
        side.setPadding(new Insets(16, 10, 14, 10));
        side.setPrefWidth(200);
        return side;
    }

    private BorderPane buildCenter() {
        selector.setPrefWidth(250);
        selector.setPromptText("Select an event");
        selector.setCellFactory(l -> new EventCell());
        selector.setButtonCell(new EventCell());
        selector.valueProperty().addListener((o, old, e) -> {
            if (!updatingSelector && e != null && !Objects.equals(e.id(), ctx.selectedEventId().get())) {
                ctx.selectedEventId().set(e.id());
            }
        });
        Button newEvent = new Button("+ New event");
        newEvent.getStyleClass().add("primary");
        newEvent.setTooltip(new Tooltip("Create an event with a form (Ctrl+N). Or describe it to the agent in the chat."));
        newEvent.setOnAction(e -> events.onNewEventManual());
        Button delete = Ui.iconButton("✕", "Delete the selected event", events::onDelete);
        delete.getStyleClass().add("icon-danger");
        Button undo = Ui.iconButton("↶", "Undo (Ctrl+Z)", events::onUndo);
        Button redo = Ui.iconButton("↷", "Redo (Ctrl+Y)", events::onRedo);
        Button chatToggle = Ui.iconButton("✦", "Show / hide the agent chat (Ctrl+J)", this::toggleChat);
        chatToggle.getStyleClass().add("ai-button");

        stateBadge.getStyleClass().add("state-badge");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8, selector, stateBadge, lifecycleButtons, spacer, undo, redo, delete, newEvent, chatToggle);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("header");
        for (Node n : List.of(undo, redo, delete, newEvent, chatToggle, stateBadge)) {
            ((Region) n).setMinWidth(Region.USE_PREF_SIZE);
        }
        selector.setMinWidth(170);

        pageTitle.getStyleClass().add("page-title");
        pageSubtitle.getStyleClass().add("page-subtitle");
        VBox titles = new VBox(2, pageTitle, pageSubtitle);
        titles.setPadding(new Insets(16, 20, 4, 20));

        ProgressBar busy = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);
        busy.getStyleClass().add("busy-bar");
        busy.setMaxWidth(Double.MAX_VALUE);
        busy.visibleProperty().bind(ctx.busy().isEqualTo(true));
        busy.managedProperty().bind(busy.visibleProperty());

        content.setPadding(new Insets(6, 10, 10, 10));
        BorderPane center = new BorderPane(content);
        center.setTop(new VBox(header, busy, titles));
        center.getStyleClass().add("main-area");
        return center;
    }

    private HBox buildStatusBar() {
        Label status = new Label();
        status.textProperty().bind(ctx.status());
        Label dot = new Label("●");
        dot.getStyleClass().add("status-dot");
        ctx.busy().addListener((o, old, b) -> {
            dot.getStyleClass().remove("busy");
            if (b) {
                dot.getStyleClass().add("busy");
            }
        });
        Label ai = new Label("✦ " + ctx.facade().aiDescription());
        ai.getStyleClass().add("chip");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(8, dot, status, spacer, ai);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(4, 12, 4, 12));
        bar.getStyleClass().add("status-bar");
        return bar;
    }

    private void toggleChat() {
        if (getRight() == null) {
            setRight(chat);
            Ui.enter(chat, 0, 180);
        } else {
            setRight(null);
        }
    }

    private void installShortcuts() {
        sceneProperty().addListener((o, old, scene) -> {
            if (scene == null) {
                return;
            }
            var acc = scene.getAccelerators();
            acc.put(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN), events::onUndo);
            acc.put(new KeyCodeCombination(KeyCode.Y, KeyCombination.SHORTCUT_DOWN), events::onRedo);
            acc.put(new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN), events::onNewEventManual);
            acc.put(new KeyCodeCombination(KeyCode.J, KeyCombination.SHORTCUT_DOWN), this::toggleChat);
            List<String> names = pageNames();
            KeyCode[] digits = {KeyCode.DIGIT1, KeyCode.DIGIT2, KeyCode.DIGIT3, KeyCode.DIGIT4,
                    KeyCode.DIGIT5, KeyCode.DIGIT6, KeyCode.DIGIT7, KeyCode.DIGIT8};
            for (int i = 0; i < names.size() && i < digits.length; i++) {
                String name = names.get(i);
                acc.put(new KeyCodeCombination(digits[i], KeyCombination.SHORTCUT_DOWN), () -> showPage(name, true));
            }
        });
    }

    private void reloadEvents() {
        updatingSelector = true;
        try {
            List<Event> all = ctx.facade().listEvents();
            selector.getItems().setAll(all);
            String id = ctx.selectedEventId().get();
            Event selected = all.stream().filter(e -> e.id().equals(id)).findFirst().orElse(null);
            selector.setValue(selected);
            stateBadge.getStyleClass().removeIf(c -> c.startsWith("status-"));
            stateBadge.setVisible(selected != null);
            lifecycleButtons.getChildren().clear();
            if (selected != null) {
                stateBadge.setText(selected.status().name());
                stateBadge.getStyleClass().add("status-" + selected.status().name().toLowerCase());
                for (LifecycleAction a : selected.state().allowedActions()) {
                    Button b = new Button(label(a));
                    b.getStyleClass().add(a == LifecycleAction.CANCEL ? "ghost-danger" : "ghost");
                    b.setMinWidth(Region.USE_PREF_SIZE);
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
            case START_PLANNING -> "▶ Start planning";
            case CONFIRM -> "✔ Confirm";
            case REOPEN -> "↺ Reopen";
            case COMPLETE -> "⚑ Complete";
            case CANCEL -> "Cancel event";
        };
    }

    private static final class EventCell extends ListCell<Event> {
        @Override
        protected void updateItem(Event e, boolean empty) {
            super.updateItem(e, empty);
            setText(empty || e == null ? null : e.name() + "  ·  " + e.start().toLocalDate());
        }
    }
}
