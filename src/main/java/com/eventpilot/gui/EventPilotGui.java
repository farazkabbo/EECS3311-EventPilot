package com.eventpilot.gui;

import com.eventpilot.EventPilotApp;
import com.eventpilot.infra.AppConfig;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.util.Duration;

/** JavaFX application: bootstraps EventPilot and shows the main window. */
public class EventPilotGui extends Application {

    @Override
    public void start(Stage stage) {
        EventPilotApp app = EventPilotApp.bootstrap(AppConfig.load());
        app.runStartupChecks(); // overdue-task check at start-up
        GuiContext ctx = new GuiContext(app.facade());
        ctx.setStage(stage);

        Scene scene = new Scene(new MainWindow(ctx), 1400, 860);
        var css = EventPilotGui.class.getResource("/gui/eventpilot.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        stage.setTitle("EventPilot - AI Event Planning Agent");
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
                    Screenshots.captureTabs(scene, java.nio.file.Path.of(shots));
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
