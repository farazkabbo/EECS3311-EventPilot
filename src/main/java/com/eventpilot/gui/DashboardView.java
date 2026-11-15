package com.eventpilot.gui;

import com.eventpilot.application.event.Alert;
import com.eventpilot.domain.Attendee;
import com.eventpilot.domain.Event;
import com.eventpilot.domain.EventTask;
import com.eventpilot.domain.service.BudgetTracker;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Overview of the selected event: stat cards, details, and the live alert list (conflicts, budget, overdue tasks, FAQ). */
public class DashboardView extends BaseView {

    /** One stat tile: a big number, a caption and an optional progress bar. */
    private static final class Stat extends VBox {
        final Label value = new Label("-");
        final Label caption = new Label();
        final ProgressBar bar = new ProgressBar(0);

        Stat(String title, String glyph) {
            super(6);
            getStyleClass().addAll("card", "stat-card");
            Label t = new Label(glyph + "  " + title);
            t.getStyleClass().add("stat-title");
            value.getStyleClass().add("stat-value");
            caption.getStyleClass().add("stat-caption");
            bar.setMaxWidth(Double.MAX_VALUE);
            getChildren().addAll(t, value, caption, bar);
            HBox.setHgrow(this, Priority.ALWAYS);
            setMaxWidth(Double.MAX_VALUE);
            setMinWidth(150);
        }

        void set(String v, String c, double progress, boolean danger) {
            value.setText(v);
            caption.setText(c);
            bar.setVisible(progress >= 0);
            bar.setProgress(Math.max(0, Math.min(1, progress)));
            bar.getStyleClass().remove("danger");
            if (danger) {
                bar.getStyleClass().add("danger");
            }
        }
    }

    private final Label title = new Label();
    private final Label when = new Label();
    private final Label countdown = new Label();
    private final Stat attendance = new Stat("Attendance", "☺");
    private final Stat budget = new Stat("Budget", "$");
    private final Stat agenda = new Stat("Agenda", "◷");
    private final Stat tasks = new Stat("Tasks", "☑");
    private final GridPane facts = new GridPane();
    private final ListView<Alert> alerts = new ListView<>();
    private final Label alertCount = new Label();
    private final TextField note = new TextField();
    private final VBox body;
    private final VBox empty;

    public DashboardView(GuiContext ctx, EventController controller) {
        super(ctx);
        title.getStyleClass().add("hero-title");
        when.getStyleClass().add("hero-sub");
        countdown.getStyleClass().add("chip");
        facts.setHgap(18);
        facts.setVgap(8);
        alerts.getStyleClass().add("alert-list");
        alerts.setCellFactory(l -> new ListCell<>() {
            @Override
            protected void updateItem(Alert a, boolean isEmpty) {
                super.updateItem(a, isEmpty);
                getStyleClass().removeAll("alert-critical", "alert-warning");
                if (isEmpty || a == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                boolean critical = a.level() == Alert.Level.CRITICAL;
                Label icon = new Label(critical ? "⚠" : "ⓘ");
                icon.getStyleClass().add("alert-icon");
                setGraphic(icon);
                setText(a.toString());
                setWrapText(true);
                setPrefWidth(0); // let the text wrap to the list width instead of widening the cell
                getStyleClass().add(critical ? "alert-critical" : "alert-warning");
            }
        });
        alerts.setPlaceholder(new Label("✔  No alerts — all good."));
        alerts.setPrefHeight(220);

        Button clearFaq = new Button("Mark FAQ questions handled");
        clearFaq.getStyleClass().add("ghost");
        clearFaq.setOnAction(e -> ctx.selectedEvent().ifPresent(ev -> {
            ctx.facade().clearEscalations(ev.id());
            ctx.toast("FAQ questions marked as handled");
        }));
        note.setPromptText("Organizer note for the FAQ agent, e.g. \"Free parking in the north lot\"");
        Button addNote = new Button("Add note");
        addNote.getStyleClass().add("primary");
        addNote.setOnAction(e -> {
            controller.onAddNote(note.getText());
            note.clear();
        });
        note.setOnAction(addNote.getOnAction());
        HBox noteRow = new HBox(6, note, addNote);
        HBox.setHgrow(note, Priority.ALWAYS);

        HBox hero = new HBox(12, new VBox(2, title, when), countdown);
        hero.setAlignment(Pos.CENTER_LEFT);
        HBox stats = new HBox(12, attendance, budget, agenda, tasks);

        alertCount.getStyleClass().add("count-badge");
        HBox alertHead = new HBox(8, cardTitle("Alerts"), alertCount);
        alertHead.setAlignment(Pos.CENTER_LEFT);
        VBox alertCard = Ui.card(null, alertHead, alerts, clearFaq);
        VBox detailCard = Ui.card("Details", facts);
        VBox noteCard = Ui.card("Knowledge for the FAQ agent", noteRow);
        HBox.setHgrow(alertCard, Priority.ALWAYS);
        detailCard.setMinWidth(360);
        HBox lower = new HBox(12, detailCard, alertCard);

        body = new VBox(14, hero, stats, lower, noteCard);
        body.setPadding(new Insets(4, 6, 10, 6));

        Label emptyIcon = new Label("✦");
        emptyIcon.getStyleClass().add("empty-icon");
        Label emptyTitle = new Label("No event selected");
        emptyTitle.getStyleClass().add("hero-title");
        Label emptyText = new Label("Describe an event to the agent in the chat on the right, e.g.\n"
                + "“AI meetup for 120 people on Dec 5 from 6 to 9pm in Toronto, budget $3000”,\n"
                + "or create one with a form.");
        emptyText.getStyleClass().add("hero-sub");
        emptyText.setWrapText(true);
        Button create = new Button("+ New event");
        create.getStyleClass().add("primary");
        create.setOnAction(e -> controller.onNewEventManual());
        empty = new VBox(12, emptyIcon, emptyTitle, emptyText, create);
        empty.setAlignment(Pos.CENTER);
        empty.getStyleClass().add("empty-state");

        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("page-scroll");
        setCenter(scroll);
    }

    private static Label cardTitle(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("card-title");
        return l;
    }

    @Override
    protected void refresh(Event e) {
        facts.getChildren().clear();
        if (e == null) {
            setCenter(empty);
            return;
        }
        if (getCenter() == empty) {
            ScrollPane scroll = new ScrollPane(body);
            scroll.setFitToWidth(true);
            scroll.getStyleClass().add("page-scroll");
            setCenter(scroll);
        }
        title.setText(e.name());
        DateTimeFormatter f = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy · HH:mm");
        when.setText(e.start().format(f) + " – " + e.end().toLocalTime()
                + (e.city().isBlank() ? "" : "  ·  " + e.city()) + "  ·  " + e.type());
        long days = ChronoUnit.DAYS.between(LocalDate.now(), e.start().toLocalDate());
        countdown.setText(days > 1 ? "in " + days + " days" : days == 1 ? "tomorrow" : days == 0 ? "today" : "past");

        long accepted = e.attendees().stream().filter(a -> a.rsvp() == Attendee.Rsvp.ACCEPTED).count();
        int invited = e.attendees().size();
        attendance.set(accepted + " / " + e.expectedAttendance(), invited + " invited · " + accepted + " accepted",
                e.expectedAttendance() == 0 ? 0 : (double) accepted / e.expectedAttendance(), false);

        BudgetTracker.BudgetStatus b = ctx.facade().budgetStatus(e.id());
        double used = b.limit() <= 0 ? 0 : b.totalSpent() / b.limit();
        budget.set(String.format("$%,.0f", b.totalSpent()),
                String.format("spent of $%,.0f · $%,.0f planned", b.limit(), b.totalPlanned()), used, used > 0.9);

        agenda.set(String.valueOf(e.agenda().size()), "items · " + e.speakers().size() + " speakers", -1, false);

        long done = e.tasks().stream().filter(t -> t.status() == EventTask.Status.DONE).count();
        long overdue = e.tasks().stream().filter(t -> t.isOverdue(LocalDate.now())).count();
        tasks.set(done + " / " + e.tasks().size(), overdue > 0 ? overdue + " overdue" : "done",
                e.tasks().isEmpty() ? 0 : (double) done / e.tasks().size(), overdue > 0);

        String[][] rows = {
                {"Venue", e.venue() == null ? "not chosen yet (Venue page)" : e.venue().name() + ", " + e.venue().address()},
                {"Status", e.status().name()},
                {"Next steps", String.join(", ", e.state().allowedActions().stream()
                        .map(a -> a.name().toLowerCase().replace('_', ' ')).toList())},
                {"AI", ctx.facade().aiDescription()}};
        for (int i = 0; i < rows.length; i++) {
            Label k = new Label(rows[i][0]);
            k.getStyleClass().add("fact-key");
            Label v = new Label(rows[i][1]);
            v.setWrapText(true);
            facts.addRow(i, k, v);
        }
        List<Alert> list = ctx.facade().currentAlerts(e.id());
        alerts.getItems().setAll(list);
        alertCount.setText(String.valueOf(list.size()));
        alertCount.getStyleClass().remove("zero");
        if (list.isEmpty()) {
            alertCount.getStyleClass().add("zero");
        }
    }
}
