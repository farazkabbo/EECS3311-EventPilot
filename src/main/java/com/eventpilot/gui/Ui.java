package com.eventpilot.gui;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.beans.property.BooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.Duration;

/** Small helpers shared by the views: cards, icon buttons, AI-button styling, toasts and theming. */
final class Ui {

    static final String AI_MARK = "✦ "; // four-pointed star used on every AI action

    private Ui() {
    }

    /** A rounded surface with an optional heading. */
    static VBox card(String heading, Node... content) {
        VBox box = new VBox(8);
        box.getStyleClass().add("card");
        if (heading != null) {
            Label h = new Label(heading);
            h.getStyleClass().add("card-title");
            box.getChildren().add(h);
        }
        box.getChildren().addAll(content);
        return box;
    }

    static Button iconButton(String glyph, String tooltip, Runnable action) {
        Button b = new Button(glyph);
        b.getStyleClass().add("icon-button");
        b.setTooltip(new Tooltip(tooltip));
        b.setOnAction(e -> action.run());
        return b;
    }

    /**
     * Gives every button whose label ends with "(AI)" the accent "AI" look: the suffix is replaced
     * by a star in front, so the views keep their plain labels and stay readable in code.
     */
    static void decorateAiButtons(Node node) {
        if (node instanceof Button b && b.getText() != null && b.getText().endsWith("(AI)")) {
            b.setText(AI_MARK + b.getText().substring(0, b.getText().length() - 4).trim());
            b.getStyleClass().add("ai-button");
            b.setTooltip(new Tooltip("Runs the AI agent. You review its proposal before anything changes."));
        }
        if (node instanceof Parent p) {
            for (Node child : p.getChildrenUnmodifiable()) {
                decorateAiButtons(child);
            }
        }
        if (node instanceof javafx.scene.control.ScrollPane sp && sp.getContent() != null) {
            decorateAiButtons(sp.getContent());
        }
    }

    /** Fade + slide-up entrance used when switching pages and for chat bubbles. */
    static void enter(Node node, double fromY, int millis) {
        node.setOpacity(0);
        node.setTranslateY(fromY);
        FadeTransition fade = new FadeTransition(Duration.millis(millis), node);
        fade.setToValue(1);
        TranslateTransition slide = new TranslateTransition(Duration.millis(millis), node);
        slide.setToY(0);
        new ParallelTransition(fade, slide).play();
    }

    /** A toast: appears in the given container, then fades out after a few seconds. */
    static void toast(VBox container, String message) {
        Label toast = new Label("✔  " + message);
        toast.getStyleClass().add("toast");
        toast.setWrapText(true);
        toast.setMaxWidth(380);
        container.getChildren().add(toast);
        container.setAlignment(Pos.BOTTOM_RIGHT);
        enter(toast, 16, 220);
        PauseTransition wait = new PauseTransition(Duration.seconds(3.2));
        wait.setOnFinished(e -> {
            FadeTransition out = new FadeTransition(Duration.millis(400), toast);
            out.setToValue(0);
            out.setOnFinished(x -> container.getChildren().remove(toast));
            out.play();
        });
        wait.play();
        while (container.getChildren().size() > 4) {
            container.getChildren().remove(0);
        }
    }

    /**
     * Applies the stylesheet (and the current theme) to every window - including dialogs and alerts,
     * which JavaFX creates in their own scenes - and keeps them in sync when the theme changes.
     */
    static void installTheme(String css, BooleanProperty dark) {
        Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            while (change.next()) {
                for (Window w : change.getAddedSubList()) {
                    style(w.getScene(), css, dark.get());
                    w.sceneProperty().addListener((o, old, scene) -> style(scene, css, dark.get()));
                }
            }
        });
        dark.addListener((o, old, isDark) -> Window.getWindows().forEach(w -> style(w.getScene(), css, isDark)));
    }

    static void style(Scene scene, String css, boolean dark) {
        if (scene == null) {
            return;
        }
        if (!scene.getStylesheets().contains(css)) {
            scene.getStylesheets().add(css);
        }
        Parent root = scene.getRoot();
        if (root != null) {
            root.getStyleClass().remove("dark");
            if (dark) {
                root.getStyleClass().add("dark");
            }
        }
    }
}
