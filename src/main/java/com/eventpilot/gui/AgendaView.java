package com.eventpilot.gui;

import com.eventpilot.agent.workflow.AgendaPreferences;
import com.eventpilot.domain.AgendaItem;
import com.eventpilot.domain.Conflict;
import com.eventpilot.domain.Event;
import com.eventpilot.domain.Speaker;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Agenda timeline with conflicts highlighted in red, speakers panel and the "Modify with AI" box. */
public class AgendaView extends BaseView {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private final TableView<AgendaItem> table = new TableView<>();
    private final ListView<String> conflicts = new ListView<>();
    private final ListView<String> speakers = new ListView<>();
    private final Set<String> conflicting = new HashSet<>();
    private Event current;

    public AgendaView(GuiContext ctx, AgendaController controller) {
        super(ctx);
        table.getColumns().add(col("Id", 50, i -> i.id()));
        table.getColumns().add(col("Time", 100, i -> i.start().format(HM) + "-" + i.end().format(HM)));
        table.getColumns().add(col("Type", 90, i -> i.type().name().toLowerCase()));
        table.getColumns().add(col("Title", 260, AgendaItem::title));
        table.getColumns().add(col("Room", 120, i -> i.room() == null ? "" : i.room()));
        table.getColumns().add(col("Speaker", 140, i -> i.speakerId() == null || current == null ? ""
                : current.findSpeaker(i.speakerId()).map(Speaker::name).orElse(i.speakerId())));
        table.setRowFactory(t -> new TableRow<>() {
            @Override
            protected void updateItem(AgendaItem item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("conflict-row");
                if (!empty && item != null && conflicting.contains(item.id())) {
                    getStyleClass().add("conflict-row");
                }
            }
        });
        table.setPlaceholder(new Label("No agenda yet - click \"Generate agenda\"."));

        Button generate = new Button("Generate agenda (AI)");
        generate.setOnAction(e -> preferencesDialog().ifPresent(controller::onGenerateAgenda));
        Button schedule = new Button("Auto-schedule speakers (AI)");
        schedule.setOnAction(e -> controller.onScheduleSpeakers());
        Button check = new Button("Check conflicts");
        check.setOnAction(e -> controller.onDetectConflicts(this::showConflicts));
        Button fix = new Button("Ask agent to fix");
        fix.setOnAction(e -> controller.onResolveConflicts());
        for (Button b : List.of(generate, schedule, fix)) {
            b.disableProperty().bind(ctx.busy().isEqualTo(true));
        }
        HBox actions = new HBox(6, generate, schedule, check, fix);

        TextField modify = new TextField();
        modify.setPromptText("Modify with AI, e.g. \"move the keynote to 2pm and add a 15-minute break after it\"");
        Button go = new Button("Modify");
        go.disableProperty().bind(ctx.busy().isEqualTo(true));
        go.setOnAction(e -> {
            controller.onModifyPlan(modify.getText());
            modify.clear();
        });
        modify.setOnAction(go.getOnAction());
        HBox modifyRow = new HBox(6, modify, go);
        HBox.setHgrow(modify, Priority.ALWAYS);

        conflicts.setPrefHeight(90);
        conflicts.setPlaceholder(new Label("Click \"Check conflicts\"."));
        VBox center = new VBox(8, actions, table, new Label("Conflicts"), conflicts, modifyRow);
        VBox.setVgrow(table, Priority.ALWAYS);
        setCenter(center);

        TextField sName = new TextField();
        sName.setPromptText("Name");
        TextField sTopic = new TextField();
        sTopic.setPromptText("Topic");
        TextField sAvail = new TextField();
        sAvail.setPromptText("09:00-12:00, 14:00-16:00 (empty = all day)");
        Button addSpeaker = new Button("Add speaker");
        addSpeaker.setOnAction(e -> {
            controller.onAddSpeaker(sName.getText(), sTopic.getText(), sAvail.getText());
            sName.clear();
            sTopic.clear();
            sAvail.clear();
        });
        VBox right = new VBox(6, new Label("Speakers"), speakers, sName, sTopic, sAvail, addSpeaker);
        right.setPadding(new Insets(0, 0, 0, 10));
        right.setPrefWidth(260);
        VBox.setVgrow(speakers, Priority.ALWAYS);
        setRight(right);
    }

    private static TableColumn<AgendaItem, String> col(String name, int width,
                                                       java.util.function.Function<AgendaItem, String> value) {
        TableColumn<AgendaItem, String> c = new TableColumn<>(name);
        c.setPrefWidth(width);
        c.setCellValueFactory(cd -> new SimpleStringProperty(value.apply(cd.getValue())));
        c.setSortable(false);
        return c;
    }

    private void showConflicts(List<Conflict> list) {
        conflicting.clear();
        list.forEach(c -> conflicting.addAll(c.itemIds()));
        conflicts.getItems().setAll(list.isEmpty() ? List.of("No conflicts found.")
                : list.stream().map(Conflict::toString).toList());
        table.refresh();
    }

    private java.util.Optional<AgendaPreferences> preferencesDialog() {
        Dialog<AgendaPreferences> d = new Dialog<>();
        d.setTitle("Agenda preferences");
        Spinner<Integer> tracks = new Spinner<>(1, 4, 1);
        Spinner<Integer> session = new Spinner<>(20, 120, 45, 5);
        int savedBreak = Integer.parseInt(ctx.facade().preferences().getOrDefault("break_minutes", "15"));
        Spinner<Integer> brk = new Spinner<>(5, 60, savedBreak, 5);
        TextField lunch = new TextField("12:00");
        TextField topics = new TextField();
        topics.setPromptText("comma-separated topics (optional)");
        CheckBox extend = new CheckBox("Keep the current agenda and add to it (extend)");
        GridPane g = new GridPane();
        g.setHgap(8);
        g.setVgap(6);
        g.addRow(0, new Label("Parallel tracks"), tracks);
        g.addRow(1, new Label("Session length (min)"), session);
        g.addRow(2, new Label("Break length (min)"), brk);
        g.addRow(3, new Label("Lunch time"), lunch);
        g.addRow(4, new Label("Topics"), topics);
        g.add(extend, 1, 5);
        d.getDialogPane().setContent(g);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        d.setResultConverter(b -> b != ButtonType.OK ? null : new AgendaPreferences(tracks.getValue(), session.getValue(),
                brk.getValue(), lunch.getText(), Arrays.stream(topics.getText().split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList(), !extend.isSelected()));
        return d.showAndWait();
    }

    @Override
    protected void refresh(Event e) {
        current = e;
        if (e == null) {
            table.getItems().clear();
            speakers.getItems().clear();
            conflicts.getItems().clear();
            return;
        }
        table.getItems().setAll(e.agenda().items());
        speakers.getItems().setAll(e.speakers().stream().map(s -> s.id() + " " + s.name()
                + (s.topic().isBlank() ? "" : " - " + s.topic())
                + (s.availability().isEmpty() ? " (all day)" : " " + s.availability().stream()
                .map(w -> w.start().format(HM) + "-" + w.end().format(HM)).toList())).toList());
        showConflicts(ctx.facade().detectConflicts(e.id()));
    }
}
