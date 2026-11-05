package com.eventpilot.gui;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** The agent chat: "Create event" (F01), "Change plan" (F12) and "Ask FAQ" (F11) modes. */
public class ChatPanel extends VBox {

    static final String CREATE = "Create event";
    static final String CHANGE = "Change plan";
    static final String FAQ = "Ask FAQ (attendee)";

    private final TextArea transcript = new TextArea();

    public ChatPanel(GuiContext ctx, AgentChatController controller) {
        super(8);
        setPadding(new Insets(10));
        setPrefWidth(360);
        Label title = new Label("Agent chat");
        title.getStyleClass().add("view-title");
        ComboBox<String> mode = new ComboBox<>(FXCollections.observableArrayList(CREATE, CHANGE, FAQ));
        mode.setValue(CREATE);
        transcript.setEditable(false);
        transcript.setWrapText(true);
        say("Agent", "Hi! Describe the event you want to plan, e.g. \"AI meetup for 120 people on Dec 5 from 6 to 9pm "
                + "in Toronto, budget $3000\". Switch the mode to change the plan or to answer attendee questions.");
        TextField input = new TextField();
        input.setPromptText("Type a message and press Enter");
        Button send = new Button("Send");
        send.disableProperty().bind(ctx.busy().isEqualTo(true));
        Button discard = new Button("Discard pending");
        discard.setOnAction(e -> controller.cancelPending(r -> say("Agent", r)));
        send.setOnAction(e -> {
            String text = input.getText().trim();
            if (text.isEmpty()) {
                return;
            }
            input.clear();
            say("You", text);
            switch (mode.getValue()) {
                case CHANGE -> controller.onModifyPlan(text, r -> say("Agent", r));
                case FAQ -> controller.onAskFaq(text, r -> say("FAQ agent", r));
                default -> controller.submitMessage(text, r -> say("Agent", r));
            }
        });
        input.setOnAction(send.getOnAction());
        HBox row = new HBox(6, input, send);
        HBox.setHgrow(input, Priority.ALWAYS);
        getChildren().addAll(title, new HBox(6, new Label("Mode"), mode, discard), transcript, row);
        VBox.setVgrow(transcript, Priority.ALWAYS);
    }

    private void say(String who, String text) {
        transcript.appendText((transcript.getText().isEmpty() ? "" : "\n\n") + who + ": " + text);
    }
}
