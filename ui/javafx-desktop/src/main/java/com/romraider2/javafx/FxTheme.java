/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import java.io.InputStream;
import java.net.URL;

import com.romraider.ui.ThemeMode;
import com.romraider.util.SettingsManager;

import javafx.scene.Scene;
import javafx.scene.control.DialogPane;
import javafx.scene.image.Image;
import javafx.stage.Stage;

final class FxTheme {
    private static final String ICON =
            "/com/romraider2/ui/assets/icons/app/romraider2-app-64.png";
    private static final String LOGO =
            "/com/romraider2/ui/assets/branding/romraider2-logo-horizontal.png";

    private FxTheme() { }

    static void apply(Stage stage, Scene scene) {
        URL stylesheet = FxTheme.class.getResource("/romraider2-javafx.css");
        if (stylesheet != null) scene.getStylesheets().add(
                stylesheet.toExternalForm());
        scene.getRoot().getStyleClass().add(isDark() ? "theme-dark" : "theme-light");
        try (InputStream stream = FxTheme.class.getResourceAsStream(ICON)) {
            if (stream != null) stage.getIcons().add(new Image(stream));
        } catch (Exception ignored) {
        }
    }

    static void applyDialog(DialogPane pane) {
        URL stylesheet = FxTheme.class.getResource("/romraider2-javafx.css");
        if (stylesheet != null) pane.getStylesheets().add(
                stylesheet.toExternalForm());
        pane.getStyleClass().add(isDark() ? "theme-dark" : "theme-light");
    }

    static Image logo() {
        try (InputStream stream = FxTheme.class.getResourceAsStream(LOGO)) {
            return stream == null ? null : new Image(stream);
        } catch (Exception ignored) {
            return null;
        }
    }

    static boolean isDark() {
        ThemeMode mode = SettingsManager.getSettings().getThemeMode();
        return mode == ThemeMode.DARK || mode == ThemeMode.HIGH_CONTRAST;
    }
}
