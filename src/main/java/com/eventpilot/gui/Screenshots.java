package com.eventpilot.gui;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javax.imageio.ImageIO;

/** Saves a PNG of every tab (used for documentation: -Deventpilot.smokeTest=true -Deventpilot.screenshotDir=dir). */
final class Screenshots {

    private Screenshots() {
    }

    static void captureTabs(Scene scene, Path dir) {
        try {
            Files.createDirectories(dir);
            TabPane tabs = (TabPane) scene.getRoot().lookup(".tab-pane");
            for (Tab tab : tabs.getTabs()) {
                tabs.getSelectionModel().select(tab);
                scene.getRoot().applyCss();
                scene.getRoot().layout();
                WritableImage img = scene.snapshot(null);
                ImageIO.write(toBuffered(img), "png", dir.resolve("gui_" + tab.getText().toLowerCase() + ".png").toFile());
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("Screenshot failed: " + e.getMessage());
        }
    }

    private static BufferedImage toBuffered(WritableImage img) {
        int w = (int) img.getWidth();
        int h = (int) img.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        PixelReader r = img.getPixelReader();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out.setRGB(x, y, r.getArgb(x, y));
            }
        }
        return out;
    }
}
