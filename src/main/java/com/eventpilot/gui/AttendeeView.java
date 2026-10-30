package com.eventpilot.gui;

import com.eventpilot.domain.Attendee;
import com.eventpilot.domain.Event;
import java.util.EnumMap;
import java.util.Map;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** F02: attendee table with an RSVP drop-down per row, add form, CSV import and RSVP pie chart. */
public class AttendeeView extends BaseView {

    private final TableView<Attendee> table = new TableView<>();
    private final PieChart rsvpChart = new PieChart();

    public AttendeeView(GuiContext ctx, AttendeeController controller) {
        super(ctx);
        table.getColumns().add(col("Id", 50, Attendee::id));
        table.getColumns().add(col("Name", 160, Attendee::name));
        table.getColumns().add(col("E-mail", 220, Attendee::email));
        table.getColumns().add(col("Role", 80, a -> a.role().name()));
        table.getColumns().add(col("Dietary", 150, a -> String.join(", ", a.dietary())));
        table.getColumns().add(col("Accessibility", 120, Attendee::accessibility));
        TableColumn<Attendee, String> rsvp = new TableColumn<>("RSVP");
        rsvp.setPrefWidth(130);
        rsvp.setCellFactory(c -> new TableCell<>() {
            private final ComboBox<Attendee.Rsvp> box = new ComboBox<>(FXCollections.observableArrayList(Attendee.Rsvp.values()));

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                Attendee a = empty || getTableRow() == null ? null : getTableRow().getItem();
                if (a == null) {
                    setGraphic(null);
                    return;
                }
                box.setOnAction(null);
                box.setValue(a.rsvp());
                box.setOnAction(e -> {
                    if (box.getValue() != a.rsvp()) {
                        controller.onUpdateRsvp(a.id(), box.getValue());
                    }
                });
                setGraphic(box);
            }
        });
        table.getColumns().add(rsvp);
        table.setPlaceholder(new Label("No attendees - add one or import samples/attendees.csv"));

        TextField name = new TextField();
        name.setPromptText("name");
        TextField email = new TextField();
        email.setPromptText("e-mail");
        ComboBox<Attendee.Role> role = new ComboBox<>(FXCollections.observableArrayList(Attendee.Role.values()));
        role.setValue(Attendee.Role.GUEST);
        TextField diets = new TextField();
        diets.setPromptText("diets: vegan; halal");
        TextField access = new TextField();
        access.setPromptText("accessibility");
        Button add = new Button("Add");
        add.setOnAction(e -> {
            controller.onAddAttendee(name.getText(), email.getText(), role.getValue(), diets.getText(), access.getText());
            name.clear();
            email.clear();
            diets.clear();
            access.clear();
        });
        Button importCsv = new Button("Import CSV...");
        importCsv.setOnAction(e -> controller.onImportCsv());
        Button remove = new Button("Remove selected");
        remove.setOnAction(e -> {
            Attendee a = table.getSelectionModel().getSelectedItem();
            if (a != null) {
                controller.onRemove(a.id());
            }
        });
        HBox form = new HBox(6, name, email, role, diets, access, add, importCsv, remove);
        rsvpChart.setTitle("RSVP");
        rsvpChart.setPrefWidth(300);
        rsvpChart.setAnimated(false);
        HBox main = new HBox(10, table, rsvpChart);
        HBox.setHgrow(table, Priority.ALWAYS);
        VBox box = new VBox(10, main, form);
        VBox.setVgrow(main, Priority.ALWAYS);
        setCenter(box);
    }

    private static TableColumn<Attendee, String> col(String n, int w, java.util.function.Function<Attendee, String> f) {
        TableColumn<Attendee, String> c = new TableColumn<>(n);
        c.setPrefWidth(w);
        c.setCellValueFactory(cd -> new SimpleStringProperty(f.apply(cd.getValue())));
        return c;
    }

    @Override
    protected void refresh(Event e) {
        rsvpChart.getData().clear();
        if (e == null) {
            table.getItems().clear();
            return;
        }
        table.getItems().setAll(e.attendees());
        Map<Attendee.Rsvp, Integer> counts = new EnumMap<>(Attendee.Rsvp.class);
        e.attendees().forEach(a -> counts.merge(a.rsvp(), 1, Integer::sum));
        counts.forEach((k, v) -> rsvpChart.getData().add(new PieChart.Data(k.name() + " (" + v + ")", v)));
    }
}
