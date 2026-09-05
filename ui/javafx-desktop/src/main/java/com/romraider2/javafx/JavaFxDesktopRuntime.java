/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import com.romraider.desktop.DesktopApplicationCommands;

import javafx.application.Platform;

/** Owns JavaFX lifecycle without leaking JavaFX into the application core. */
final class JavaFxDesktopRuntime {
    private JavaFxDesktopRuntime() { }

    static void launch(String[] arguments) {
        CountDownLatch stopped = new CountDownLatch(1);
        AtomicReference<FxDesktopHost> host = new AtomicReference<>();
        Runnable start = () -> {
            Platform.setImplicitExit(false);
            FxDesktopHost value = new FxDesktopHost(stopped);
            host.set(value);
            value.start(arguments);
        };
        try {
            Platform.startup(start);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(start);
        }
        try {
            stopped.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            FxDesktopHost value = host.get();
            if (value != null) Platform.runLater(value::closeAll);
        }
    }

    private static final class FxDesktopHost {
        private final CountDownLatch stopped;
        private final DesktopApplicationCommands.Handler commandHandler;
        private FxEditorWindow editor;
        private FxLoggerWindow logger;

        FxDesktopHost(CountDownLatch stopped) {
            this.stopped = stopped;
            commandHandler = arguments -> {
                Platform.runLater(() -> route(arguments));
                return true;
            };
        }

        void start(String[] arguments) {
            DesktopApplicationCommands.register(commandHandler);
            route(arguments);
        }

        private void route(String[] arguments) {
            if (isLoggerLaunch(arguments)) {
                openLogger();
                if (Arrays.stream(arguments).anyMatch(value -> value.equalsIgnoreCase("-logger.touch")))
                    logger.setTouchMode();
                if (Arrays.stream(arguments).anyMatch(value -> value.equalsIgnoreCase("-logger.fullscreen")))
                    logger.enterFullScreen();
            } else {
                List<File> files = Arrays.stream(arguments)
                        .map(File::new).filter(File::isFile).toList();
                openEditor(files);
            }
        }

        private void openEditor(List<File> files) {
            if (editor == null) {
                editor = new FxEditorWindow(this::editorClosed,
                        this::openLogger);
            }
            editor.show();
            editor.openFiles(files);
        }

        private void openLogger() {
            if (logger == null) {
                logger = new FxLoggerWindow(this::loggerClosed);
            }
            logger.show();
        }

        private void editorClosed() {
            editor = null;
            stopIfEmpty();
        }

        private void loggerClosed() {
            logger = null;
            stopIfEmpty();
        }

        private void stopIfEmpty() {
            if (editor != null || logger != null) return;
            DesktopApplicationCommands.unregister(commandHandler);
            Platform.exit();
            stopped.countDown();
        }

        void closeAll() {
            if (editor != null) editor.close();
            if (logger != null) logger.close();
            // Window callbacks clear only windows whose guarded close succeeded.
            stopIfEmpty();
        }

        private static boolean isLoggerLaunch(String[] arguments) {
            return Arrays.stream(arguments).map(value ->
                    value.toLowerCase(Locale.ROOT)).anyMatch(value ->
                    value.equals("-logger") || value.equals("-logger.fullscreen")
                            || value.equals("-logger.touch"));
        }
    }
}
