package com.eventpilot.gui;

import com.eventpilot.agent.TimeParser;
import java.time.LocalDate;
import java.time.LocalDateTime;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

/** Manual "New event" form - the fallback when the AI is unavailable (UC01 alternative flow). */
public class NewEventDialog extends Dialog<NewEventDialog.Data> {

    public record Data(String name, String type, LocalDateTime start, LocalDateTime end, int attendance,
                       double budget, String city) {
    }

    public NewEventDialog() {
        setTitle("New event");
        setHeaderText("Create an event manually");
        TextField name = new TextField();
        TextField type = new TextField("meetup");
        DatePicker date = new DatePicker(LocalDate.now().plusWeeks(4));
        TextField start = new TextField("09:00");
        TextField end = new TextField("17:00");
        TextField attendance = new TextField("50");
        TextField budget = new TextField("5000");
        TextField city = new TextField("Toronto");
        GridPane g = new GridPane();
        g.setHgap(8);
        g.setVgap(6);
        g.setPadding(new Insets(10));
        String[] labels = {"Name", "Type", "Date", "Start (HH:MM)", "End (HH:MM)", "Expected attendance", "Budget (CAD)", "City"};
        javafx.scene.Node[] fields = {name, type, date, start, end, attendance, budget, city};
        for (int i = 0; i < labels.length; i++) {
            g.addRow(i, new Label(labels[i]), fields[i]);
        }
        getDialogPane().setContent(g);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        setResultConverter(b -> {
            if (b != ButtonType.OK) {
                return null;
            }
            try {
                LocalDate d = date.getValue();
                return new Data(name.getText(), type.getText(), LocalDateTime.of(d, TimeParser.parseTime(start.getText())),
                        LocalDateTime.of(d, TimeParser.parseTime(end.getText())), Integer.parseInt(attendance.getText().trim()),
                        Double.parseDouble(budget.getText().trim()), city.getText());
            } catch (RuntimeException e) {
                BaseController.error("Invalid input: " + e.getMessage());
                return null;
            }
        });
    }
}
