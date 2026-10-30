package com.eventpilot.gui;

import com.eventpilot.agent.workflow.InvitationDraftingWorkflow;
import com.eventpilot.domain.Event;
import com.eventpilot.domain.Invitation;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** F10: tone selector, AI drafting, editable previews, sending and delivery status. */
public class InvitationView extends BaseView {

    private final ListView<Invitation> list = new ListView<>();
    private final TextField subject = new TextField();
    private final TextArea body = new TextArea();
    private final Label stateHint = new Label();

    public InvitationView(GuiContext ctx, AttendeeController controller) {
        super(ctx);
        ComboBox<String> tone = new ComboBox<>(FXCollections.observableArrayList(InvitationDraftingWorkflow.TONES));
        tone.setValue("friendly");
        Button draft = new Button("Draft invitations (AI)");
        draft.disableProperty().bind(ctx.busy().isEqualTo(true));
        draft.setOnAction(e -> controller.onDraftInvitations(tone.getValue(), this::refreshNow));
        Button send = new Button("Send");
        send.setOnAction(e -> controller.onSendInvitations());
        Button save = new Button("Save edit");
        save.setOnAction(e -> {
            Invitation i = list.getSelectionModel().getSelectedItem();
            if (i != null) {
                controller.onEditInvitation(i.email(), subject.getText(), body.getText());
            }
        });
        list.setCellFactory(l -> new ListCell<>() {
            @Override
            protected void updateItem(Invitation i, boolean empty) {
                super.updateItem(i, empty);
                setText(empty || i == null ? null : "[" + i.status() + "] " + i.email() + " (" + i.group() + ")"
                        + (i.error() == null ? "" : " - " + i.error()));
            }
        });
        list.getSelectionModel().selectedItemProperty().addListener((o, old, i) -> {
            subject.setText(i == null ? "" : i.subject());
            body.setText(i == null ? "" : i.body());
        });
        list.setPrefWidth(380);
        body.setWrapText(true);
        VBox editor = new VBox(6, new Label("Subject"), subject, new Label("Body"), body, save);
        VBox.setVgrow(body, Priority.ALWAYS);
        HBox main = new HBox(10, list, editor);
        HBox.setHgrow(editor, Priority.ALWAYS);
        stateHint.getStyleClass().add("help");
        VBox box = new VBox(10, new HBox(6, new Label("Tone"), tone, draft, send), stateHint, main);
        VBox.setVgrow(main, Priority.ALWAYS);
        setCenter(box);
    }

    @Override
    protected void refresh(Event e) {
        if (e == null) {
            list.getItems().clear();
            return;
        }
        stateHint.setText("Event status: " + e.status() + " - invitations can be drafted and sent only in PLANNING or "
                + "CONFIRMED. Every draft is checked to contain the real date, time and venue.");
        list.getItems().setAll(e.invitations());
    }
}
