package com.eventpilot.gui;

import com.eventpilot.domain.Event;
import com.eventpilot.domain.EventTask;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** F09: task board (To do / In progress / Done) with drag-and-drop between columns. */
public class TaskBoardView extends BaseView {

    private final Map<EventTask.Status, ListView<EventTask>> columns = new EnumMap<>(EventTask.Status.class);
    private final Map<EventTask.Status, Label> counts = new EnumMap<>(EventTask.Status.class);

    private static VBox taskCard(EventTask t) {
        Label title = new Label(t.title());
        title.getStyleClass().add("task-title");
        title.setWrapText(true);
        Label id = new Label(t.id());
        id.getStyleClass().add("task-id");
        String owner = t.owner() == null || t.owner().isBlank() ? "?" : t.owner();
        Label avatar = new Label(owner.substring(0, 1).toUpperCase());
        avatar.getStyleClass().add("avatar");
        Label who = new Label(owner);
        who.getStyleClass().add("task-meta");
        boolean overdue = t.isOverdue(LocalDate.now());
        Label due = new Label((overdue ? "⚠ overdue " : "due ") + t.deadline());
        due.getStyleClass().addAll("chip", overdue ? "chip-danger" : "chip-muted");
        javafx.scene.layout.Region sp = new javafx.scene.layout.Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        HBox meta = new HBox(6, avatar, who, sp, due);
        meta.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        VBox card = new VBox(6, new HBox(6, id, title), meta);
        if (!t.dependsOn().isEmpty()) {
            Label dep = new Label("↳ after " + String.join(", ", t.dependsOn()));
            dep.getStyleClass().add("task-meta");
            card.getChildren().add(dep);
        }
        card.getStyleClass().add("task-card");
        if (overdue) {
            card.getStyleClass().add("overdue");
        }
        return card;
    }

    public TaskBoardView(GuiContext ctx, TaskController controller) {
        super(ctx);
        HBox board = new HBox(10);
        for (EventTask.Status status : EventTask.Status.values()) {
            ListView<EventTask> list = new ListView<>();
            list.setCellFactory(l -> {
                ListCell<EventTask> cell = new ListCell<>() {
                    @Override
                    protected void updateItem(EventTask t, boolean empty) {
                        super.updateItem(t, empty);
                        setText(null);
                        if (empty || t == null) {
                            setGraphic(null);
                            return;
                        }
                        setGraphic(taskCard(t));
                    }
                };
                cell.setOnDragDetected(e -> {
                    if (cell.getItem() == null) {
                        return;
                    }
                    Dragboard db = cell.startDragAndDrop(TransferMode.MOVE);
                    ClipboardContent content = new ClipboardContent();
                    content.putString(cell.getItem().id());
                    db.setContent(content);
                    e.consume();
                });
                return cell;
            });
            list.getStyleClass().add("task-column");
            list.setPlaceholder(new Label("Drop tasks here"));
            list.setOnDragOver(e -> {
                if (e.getDragboard().hasString()) {
                    e.acceptTransferModes(TransferMode.MOVE);
                }
                e.consume();
            });
            list.setOnDragEntered(e -> list.getStyleClass().add("drag-over"));
            list.setOnDragExited(e -> list.getStyleClass().remove("drag-over"));
            list.setOnDragDropped(e -> {
                list.getStyleClass().remove("drag-over");
                controller.onUpdateTaskStatus(e.getDragboard().getString(), status);
                e.setDropCompleted(true);
                e.consume();
            });
            String name = status.name().charAt(0) + status.name().substring(1).toLowerCase().replace('_', ' ');
            Label title = new Label(name);
            title.getStyleClass().add("column-title");
            Label count = new Label("0");
            count.getStyleClass().add("count-badge");
            counts.put(status, count);
            HBox head = new HBox(8, title, count);
            head.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            VBox col = new VBox(8, head, list);
            col.getStyleClass().addAll("card", "board-column");
            VBox.setVgrow(list, Priority.ALWAYS);
            HBox.setHgrow(col, Priority.ALWAYS);
            board.getChildren().add(col);
            columns.put(status, list);
        }

        TextField team = new TextField();
        team.setPromptText("Team members, comma-separated (e.g. Sam, Lee)");
        Button generate = new Button("Generate task plan (AI)");
        generate.disableProperty().bind(ctx.busy().isEqualTo(true));
        generate.setOnAction(e -> controller.onGenerateTasks(team.getText()));
        TextField title = new TextField();
        title.setPromptText("New task title");
        TextField owner = new TextField();
        owner.setPromptText("owner");
        DatePicker due = new DatePicker(LocalDate.now().plusDays(7));
        Button add = new Button("Add task");
        add.setOnAction(e -> {
            controller.onAddTask(title.getText(), owner.getText(), due.getValue());
            title.clear();
        });
        HBox top = new HBox(6, team, generate);
        HBox.setHgrow(team, Priority.ALWAYS);
        Label help = new Label("Drag a card to another column to change its status. A task cannot be done while a "
                + "dependency is still open. Overdue tasks are shown in red and raise Dashboard alerts.");
        help.getStyleClass().add("help");
        help.setWrapText(true);
        VBox box = new VBox(8, top, help, board, new HBox(6, title, owner, due, add));
        VBox.setVgrow(board, Priority.ALWAYS);
        setCenter(box);
    }

    @Override
    protected void refresh(Event e) {
        columns.values().forEach(l -> l.getItems().clear());
        if (e == null) {
            return;
        }
        for (EventTask t : ctx.facade().orderedTasks(e.id())) {
            columns.get(t.status()).getItems().add(t);
        }
        columns.forEach((s, l) -> counts.get(s).setText(String.valueOf(l.getItems().size())));
    }
}
