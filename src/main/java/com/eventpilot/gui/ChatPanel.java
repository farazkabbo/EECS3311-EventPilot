package com.eventpilot.gui;

import java.util.List;
import java.util.Map;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/** The agent chat: "Create event" (F01), "Change plan" (F12) and "Ask FAQ" (F11) modes, shown as chat bubbles. */
public class ChatPanel extends VBox {

    static final String CREATE = "Create event";
    static final String CHANGE = "Change plan";
    static final String FAQ = "Ask FAQ";

    private static final Map<String, List<String>> SUGGESTIONS = Map.of(
            CREATE, List.of("AI meetup for 120 people on Dec 5, 6-9pm in Toronto, budget $3000",
                    "Robotics workshop for 80 people on Dec 12, 9am-5pm, budget $9000"),
            CHANGE, List.of("Move the keynote to 2pm", "Add a 15-minute break after the keynote",
                    "Rename the closing networking to Farewell reception"),
            FAQ, List.of("Is there parking?", "What time does it start?", "Are vegan meals available?"));

    private static final Map<String, String> PLACEHOLDERS = Map.of(
            CREATE, "Describe the event you want to plan...",
            CHANGE, "Describe a change to the plan...",
            FAQ, "Ask a question as an attendee would...");

    private final VBox messages = new VBox(10);
    private final ScrollPane scroll = new ScrollPane(messages);
    private final FlowPane chips = new FlowPane(6, 6);
    private final TextField input = new TextField();
    private String mode = CREATE;

    public ChatPanel(GuiContext ctx, AgentChatController controller) {
        super(10);
        getStyleClass().add("chat-panel");
        setPadding(new Insets(14));
        setPrefWidth(350);
        setMinWidth(300);

        Label title = new Label("✦ Agent");
        title.getStyleClass().add("chat-title");
        Label subtitle = new Label("Plans with tools, then asks you to review");
        subtitle.getStyleClass().add("page-subtitle");
        Button discard = Ui.iconButton("↺", "Discard the pending (half-finished) request", () ->
                controller.cancelPending(r -> say(false, "Agent", r)));
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        HBox head = new HBox(8, new VBox(0, title, subtitle), sp, discard);
        head.setAlignment(Pos.CENTER_LEFT);

        ToggleGroup modes = new ToggleGroup();
        HBox segmented = new HBox();
        segmented.getStyleClass().add("segmented");
        for (String m : List.of(CREATE, CHANGE, FAQ)) {
            ToggleButton t = new ToggleButton(m);
            t.setToggleGroup(modes);
            t.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(t, Priority.ALWAYS);
            t.setSelected(m.equals(CREATE));
            t.setOnAction(e -> {
                t.setSelected(true);
                setMode(m);
            });
            segmented.getChildren().add(t);
        }

        messages.setPadding(new Insets(4));
        messages.getStyleClass().add("messages");
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("chat-scroll");
        messages.heightProperty().addListener((o, old, h) -> scroll.setVvalue(1.0));
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Label typing = new Label("Agent is thinking");
        typing.getStyleClass().add("typing");
        typing.visibleProperty().bind(ctx.busy().isEqualTo(true));
        typing.managedProperty().bind(typing.visibleProperty());
        Timeline dots = new Timeline(new KeyFrame(Duration.millis(400), e -> {
            String t = typing.getText();
            typing.setText(t.endsWith("...") ? "Agent is thinking" : t + ".");
        }));
        dots.setCycleCount(Timeline.INDEFINITE);
        dots.play();

        input.getStyleClass().add("chat-input");
        Button send = new Button("➤");
        send.getStyleClass().addAll("primary", "send-button");
        send.setTooltip(new Tooltip("Send (Enter)"));
        send.disableProperty().bind(ctx.busy().isEqualTo(true).or(input.textProperty().isEmpty()));
        send.setOnAction(e -> {
            String text = input.getText().trim();
            if (text.isEmpty() || ctx.busy().get()) {
                return;
            }
            input.clear();
            say(true, "You", text);
            switch (mode) {
                case CHANGE -> controller.onModifyPlan(text, r -> say(false, "Agent", r));
                case FAQ -> controller.onAskFaq(text, r -> say(false, "FAQ agent", r));
                default -> controller.submitMessage(text, r -> say(false, "Agent", r));
            }
        });
        input.setOnAction(send.getOnAction());
        HBox row = new HBox(6, input, send);
        row.setAlignment(Pos.CENTER);
        HBox.setHgrow(input, Priority.ALWAYS);

        chips.getStyleClass().add("chips");
        setMode(CREATE);
        say(false, "Agent", "Hi! Describe the event you want to plan and I will extract the details, ask for anything "
                + "missing and set it up. Switch to “Change plan” to edit the plan in plain language, or "
                + "“Ask FAQ” to answer attendee questions. Try one of the suggestions below.");
        getChildren().addAll(head, segmented, scroll, typing, chips, row);
    }

    private void setMode(String m) {
        mode = m;
        input.setPromptText(PLACEHOLDERS.get(m));
        chips.getChildren().clear();
        for (String s : SUGGESTIONS.get(m)) {
            Button chip = new Button(s);
            chip.getStyleClass().add("suggestion");
            chip.setWrapText(true);
            chip.maxWidthProperty().bind(widthProperty().subtract(40));
            chip.setMinHeight(Region.USE_PREF_SIZE);
            chip.setOnAction(e -> {
                input.setText(s);
                input.requestFocus();
                input.end();
            });
            chips.getChildren().add(chip);
        }
    }

    private void say(boolean mine, String who, String text) {
        Runnable add = () -> {
            Label author = new Label(who);
            author.getStyleClass().add("bubble-author");
            Label body = new Label(text);
            body.setWrapText(true);
            body.getStyleClass().add("bubble-text");
            VBox bubble = new VBox(2, author, body);
            bubble.getStyleClass().addAll("bubble", mine ? "bubble-me" : "bubble-agent");
            bubble.maxWidthProperty().bind(scroll.widthProperty().multiply(0.82));
            HBox line = new HBox(bubble);
            line.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            messages.getChildren().add(line);
            Ui.enter(line, 8, 180);
        };
        if (Platform.isFxApplicationThread()) {
            add.run();
        } else {
            Platform.runLater(add);
        }
    }
}
