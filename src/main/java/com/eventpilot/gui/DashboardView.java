package com.eventpilot.gui;

import com.eventpilot.application.event.Alert;
import com.eventpilot.domain.Attendee;
import com.eventpilot.domain.Event;
import com.eventpilot.domain.service.BudgetTracker;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Overview of the selected event plus the live alert list (conflicts, budget, overdue tasks, FAQ). */
public class DashboardView extends BaseView {

    private final Label title = new Label();
    private final GridPane facts = new GridPane();
    private final ListView<Alert> alerts = new ListView<>();
    private final TextField note = new TextField();

    public DashboardView(GuiContext ctx, EventController controller) {
        super(ctx);
        title.getStyleClass().add("view-title");
        facts.setHgap(16);
        facts.setVgap(4);
        alerts.setCellFactory(l -> new ListCell<>() {
            @Override
            protected void updateItem(Alert a, boolean empty) {
                super.updateItem(a, empty);
                setText(empty || a == null ? null : a.toString());
                getStyleClass().removeAll("alert-critical", "alert-warning");
                if (!empty && a != null) {
                    getStyleClass().add(a.level() == Alert.Level.CRITICAL ? "alert-critical" : "alert-warning");
                }
            }
        });
        Button clearFaq = new Button("Mark FAQ questions handled");
        clearFaq.setOnAction(e -> ctx.selectedEvent().ifPresent(ev -> ctx.facade().clearEscalations(ev.id())));
        note.setPromptText("Organizer note for the FAQ agent, e.g. \"Free parking in the north lot\"");
        Button addNote = new Button("Add note");
        addNote.setOnAction(e -> {
            controller.onAddNote(note.getText());
            note.clear();
        });
        HBox noteRow = new HBox(6, note, addNote);
        HBox.setHgrow(note, Priority.ALWAYS);
        VBox box = new VBox(10, title, facts, new Label("Alerts"), alerts, clearFaq, noteRow);
        VBox.setVgrow(alerts, Priority.ALWAYS);
        box.setPadding(new Insets(4));
        setCenter(box);
    }

    @Override
    protected void refresh(Event e) {
        facts.getChildren().clear();
        if (e == null) {
            title.setText("No event selected - describe one in the chat (Create event) or use New event.");
            alerts.getItems().clear();
            return;
        }
        title.setText(e.name() + "  [" + e.status() + "]");
        DateTimeFormatter f = DateTimeFormatter.ofPattern("EEE MMM d, yyyy HH:mm");
        BudgetTracker.BudgetStatus b = ctx.facade().budgetStatus(e.id());
        long accepted = e.attendees().stream().filter(a -> a.rsvp() == Attendee.Rsvp.ACCEPTED).count();
        String[][] rows = {
                {"When", e.start().format(f) + " - " + e.end().toLocalTime()},
                {"Type / city", e.type() + " / " + (e.city().isBlank() ? "-" : e.city())},
                {"Venue", e.venue() == null ? "not chosen (Venue tab)" : e.venue().name() + ", " + e.venue().address()},
                {"Attendance", e.expectedAttendance() + " expected, " + e.attendees().size() + " invited, " + accepted + " accepted"},
                {"Agenda", e.agenda().size() + " items, " + e.speakers().size() + " speakers"},
                {"Budget", String.format("$%.0f limit, $%.0f planned, $%.0f spent", b.limit(), b.totalPlanned(), b.totalSpent())},
                {"Tasks", e.tasks().size() + " (" + e.tasks().stream().filter(t -> t.status().name().equals("DONE")).count() + " done)"},
                {"Next steps", e.state().allowedActions().toString()},
                {"AI", ctx.facade().aiDescription()}};
        for (int i = 0; i < rows.length; i++) {
            Label k = new Label(rows[i][0]);
            k.getStyleClass().add("fact-key");
            facts.addRow(i, k, new Label(rows[i][1]));
        }
        List<Alert> list = ctx.facade().currentAlerts(e.id());
        alerts.getItems().setAll(list);
        if (list.isEmpty()) {
            alerts.setPlaceholder(new Label("No alerts - all good."));
        }
    }
}
