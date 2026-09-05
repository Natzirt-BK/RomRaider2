/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import com.romraider.logger.analysis.LogDataset;
import com.romraider.logger.analysis.RomRaiderCsvLogParser;

/** Latest-selection-wins CSV loading. Open, close and delivery share the UI thread. */
final class FxLogLoadCoordinator implements AutoCloseable {
    private final Function<File, CompletableFuture<LogDataset>> parser;
    private final Consumer<Runnable> dispatch;
    private final BiConsumer<File, LogDataset> loaded;
    private final BiConsumer<File, Throwable> failed;
    private long generation;
    private boolean closed;

    FxLogLoadCoordinator(Function<File, CompletableFuture<LogDataset>> parser,
            Consumer<Runnable> dispatch, BiConsumer<File, LogDataset> loaded,
            BiConsumer<File, Throwable> failed) {
        this.parser = Objects.requireNonNull(parser);
        this.dispatch = Objects.requireNonNull(dispatch);
        this.loaded = Objects.requireNonNull(loaded);
        this.failed = Objects.requireNonNull(failed);
    }

    void open(File file) {
        // A cancelled chooser must not invalidate a previously accepted load.
        if (closed || file == null) return;
        final File source = file.getAbsoluteFile();
        final long request = ++generation;
        try {
            Objects.requireNonNull(parser.apply(source), "Missing CSV parse operation")
                    .whenComplete((dataset, failure) ->
                            deliver(request, source, dataset, failure));
        } catch (RuntimeException failure) {
            deliver(request, source, null, failure);
        }
    }

    private void deliver(long request, File source, LogDataset dataset, Throwable failure) {
        dispatch.accept(() -> {
            // Check at UI delivery, not just when background parsing finishes.
            if (closed || request != generation) return;
            if (failure != null) failed.accept(source, failure);
            else if (dataset == null) failed.accept(source,
                    new IOException("The CSV parser returned no dataset"));
            else {
                try {
                    loaded.accept(source, dataset);
                } catch (RuntimeException presentationFailure) {
                    failed.accept(source, presentationFailure);
                }
            }
        });
    }

    static CompletableFuture<LogDataset> parseAsync(File source) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return new RomRaiderCsvLogParser().parse(source);
            } catch (Exception failure) {
                throw new CompletionException(failure);
            }
        });
    }

    @Override public void close() {
        closed = true;
        // Parsing may finish, but cannot publish a pane, status, or error dialog.
        generation++;
    }
}
