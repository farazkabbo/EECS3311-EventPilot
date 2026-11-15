package com.eventpilot.gui;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javax.imageio.ImageIO;

/**
 * Saves a PNG of every page, plus the dashboard in dark mode (used for documentation:
 * -Deventpilot.smokeTest=true -Deventpilot.screenshotDir=dir).
 */
final class Screenshots {

    private Screenshots() {
    }

    static void capturePages(Scene scene, MainWindow window, GuiContext ctx, Path dir) {
        try {
            Files.createDirectories(dir);
            boolean wasDark = ctx.darkMode().get();
            ctx.darkMode().set(false);
            for (String page : window.pageNames()) {
                window.showPage(page, false);
                save(scene, dir.resolve("gui_" + page.toLowerCase() + ".png"));
            }
            ctx.darkMode().set(true);
            window.showPage("Dashboard", false);
            save(scene, dir.resolve("gui_dashboard_dark.png"));
            ctx.darkMode().set(wasDark);
        } catch (IOException | RuntimeException e) {
            System.err.println("Screenshot failed: " + e.getMessage());
        }
    }

    private static void save(Scene scene, Path file) throws IOException {
        scene.getRoot().applyCss();
        scene.getRoot().layout();
        WritableImage img = scene.snapshot(null);
        ImageIO.write(toBuffered(img), "png", file.toFile());
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
