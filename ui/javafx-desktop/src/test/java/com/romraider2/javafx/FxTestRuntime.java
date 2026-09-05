package com.romraider2.javafx;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;

/** One toolkit per Gradle test worker; never restart an exited JavaFX runtime. */
final class FxTestRuntime {
    static {
        Platform.startup(() -> Platform.setImplicitExit(false));
    }

    interface Action { void run() throws Exception; }

    static void run(Action action) throws Exception {
        CompletableFuture<Void> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try { action.run(); result.complete(null); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        result.get(20, TimeUnit.SECONDS);
    }
}
