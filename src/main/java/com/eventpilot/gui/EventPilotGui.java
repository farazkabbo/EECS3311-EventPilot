package com.eventpilot.gui;

import com.eventpilot.EventPilotApp;
import com.eventpilot.infra.AppConfig;
import java.util.prefs.Preferences;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

/** JavaFX application: bootstraps EventPilot and shows the main window with a toast layer on top. */
public class EventPilotGui extends Application {

    private static final String THEME_KEY = "darkMode";

    @Override
    public void start(Stage stage) {
        EventPilotApp app = EventPilotApp.bootstrap(AppConfig.load());
        app.runStartupChecks(); // overdue-task check at start-up
        GuiContext ctx = new GuiContext(app.facade());
        ctx.setStage(stage);

        // the theme is a per-user UI setting, so it lives in Java preferences (not in the agent's memory)
        Preferences prefs = Preferences.userNodeForPackage(EventPilotGui.class);
        ctx.darkMode().set(prefs.getBoolean(THEME_KEY, false));
        ctx.darkMode().addListener((o, old, dark) -> prefs.putBoolean(THEME_KEY, dark));

        MainWindow window = new MainWindow(ctx);
        VBox toasts = new VBox(8);
        toasts.setAlignment(Pos.BOTTOM_RIGHT);
        toasts.setPadding(new Insets(0, 24, 44, 0));
        toasts.setPickOnBounds(false);
        toasts.setMouseTransparent(true);
        ctx.setToaster(msg -> Platform.runLater(() -> Ui.toast(toasts, msg)));
        StackPane root = new StackPane(window, toasts);

        Scene scene = new Scene(root, 1440, 880);
        var css = EventPilotGui.class.getResource("/gui/eventpilot.css");
        if (css != null) {
            Ui.installTheme(css.toExternalForm(), ctx.darkMode());
            Ui.style(scene, css.toExternalForm(), ctx.darkMode().get());
        }
        stage.setTitle("EventPilot - AI Event Planning Agent");
        stage.setMinWidth(1100);
        stage.setMinHeight(700);
        stage.setScene(scene);
        stage.show();

        // re-check overdue tasks every hour (catches the change of day while the app stays open)
        Timeline daily = new Timeline(new KeyFrame(Duration.hours(1), e -> app.runStartupChecks()));
        daily.setCycleCount(Timeline.INDEFINITE);
        daily.play();

        if (Boolean.getBoolean("eventpilot.smokeTest")) {
            String shots = System.getProperty("eventpilot.screenshotDir");
            Timeline close = new Timeline(new KeyFrame(Duration.seconds(3), e -> {
                if (shots != null) {
                    Screenshots.capturePages(scene, window, ctx, java.nio.file.Path.of(shots));
                }
                System.out.println("GUI_SMOKE_OK events=" + app.facade().listEvents().size());
                Platform.exit();
            }));
            close.play();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
