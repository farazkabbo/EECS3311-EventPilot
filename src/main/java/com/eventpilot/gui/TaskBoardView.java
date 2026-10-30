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
                        getStyleClass().remove("overdue");
                        if (empty || t == null) {
                            setText(null);
                            return;
                        }
                        setText(t.id() + "  " + t.title() + "\n" + t.owner() + " - due " + t.deadline()
                                + (t.dependsOn().isEmpty() ? "" : " - after " + t.dependsOn()));
                        if (t.isOverdue(LocalDate.now())) {
                            getStyleClass().add("overdue");
                        }
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
            list.setOnDragOver(e -> {
                if (e.getDragboard().hasString()) {
                    e.acceptTransferModes(TransferMode.MOVE);
                }
                e.consume();
            });
            list.setOnDragDropped(e -> {
                controller.onUpdateTaskStatus(e.getDragboard().getString(), status);
                e.setDropCompleted(true);
                e.consume();
            });
            Label title = new Label(status.name().replace('_', ' '));
            title.getStyleClass().add("column-title");
            VBox col = new VBox(4, title, list);
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
    }
}
