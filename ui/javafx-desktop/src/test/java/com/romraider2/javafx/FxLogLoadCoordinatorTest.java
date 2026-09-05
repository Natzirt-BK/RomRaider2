/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import com.romraider.logger.analysis.LogDataset;
import com.romraider.logger.analysis.RomRaiderCsvLogParser;
import org.junit.jupiter.api.Test;

/** Exercises controlled completion ordering on every CI OS, without a toolkit. */
class FxLogLoadCoordinatorTest {
    @Test void reversedCompletionOnlyPublishesLatestFileAndDataset() throws Exception {
        Harness h = new Harness();
        h.loader.open(new File("first.csv"));
        h.loader.open(new File("second.csv"));
        LogDataset second = dataset("second.csv");
        h.pending.get(1).complete(second);
        h.drain();
        h.pending.get(0).complete(dataset("first.csv"));
        h.drain();
        assertEquals(List.of(new File("second.csv").getAbsoluteFile()), h.sources);
        assertEquals(List.of(second), h.datasets);
        assertTrue(h.failures.isEmpty());
    }

    @Test void completionQueuedBeforeNewSelectionIsStillStaleAtDelivery() throws Exception {
        Harness h = new Harness();
        h.loader.open(new File("first.csv"));
        h.pending.get(0).complete(dataset("first.csv"));
        h.loader.open(new File("second.csv"));
        h.drain();
        assertTrue(h.datasets.isEmpty());
        h.pending.get(1).complete(dataset("second.csv"));
        h.drain();
        assertEquals("second.csv", h.sources.get(0).getName());
    }

    @Test void staleFailureDoesNotShowAnErrorOverNewerSuccess() throws Exception {
        Harness h = new Harness();
        h.loader.open(new File("first.csv"));
        h.loader.open(new File("second.csv"));
        h.pending.get(1).complete(dataset("second.csv"));
        h.pending.get(0).completeExceptionally(new IOException("old failure"));
        h.drain();
        assertTrue(h.failures.isEmpty());
        assertEquals(1, h.datasets.size());
    }

    @Test void latestFailureDoesNotResurrectOlderSuccessfulRequest() throws Exception {
        Harness h = new Harness();
        h.loader.open(new File("first.csv"));
        h.loader.open(new File("second.csv"));
        IOException failure = new IOException("new failure");
        h.pending.get(1).completeExceptionally(failure);
        h.pending.get(0).complete(dataset("first.csv"));
        h.drain();
        assertTrue(h.datasets.isEmpty());
        assertEquals(List.of(failure), h.failures);
        assertEquals("second.csv", h.errorSources.get(0).getName());
    }

    @Test void cancelledChooserDoesNotInvalidateAnAcceptedLoad() throws Exception {
        Harness h = new Harness();
        h.loader.open(new File("first.csv"));
        h.loader.open(null);
        h.pending.get(0).complete(dataset("first.csv"));
        h.drain();
        assertEquals(1, h.pending.size());
        assertEquals(1, h.datasets.size());
    }

    @Test void repeatedSamePathStillHasDifferentRequestIdentity() throws Exception {
        Harness h = new Harness();
        File source = new File("reloaded.csv");
        h.loader.open(source);
        h.loader.open(source);
        LogDataset fresh = dataset("fresh content");
        h.pending.get(1).complete(fresh);
        h.pending.get(0).complete(dataset("old content"));
        h.drain();
        assertEquals(List.of(fresh), h.datasets);
        assertEquals(source.getAbsoluteFile(), h.sources.get(0));
    }

    @Test void closeRejectsQueuedAndFutureSuccessesAndErrors() throws Exception {
        LogDataset data = dataset("closed.csv");
        for (boolean queued : new boolean[] {false, true}) {
            for (boolean fail : new boolean[] {false, true}) {
                Harness h = new Harness();
                h.loader.open(new File("closed.csv"));
                Runnable finish = () -> {
                    if (fail) h.pending.get(0).completeExceptionally(new IOException("late"));
                    else h.pending.get(0).complete(data);
                };
                if (queued) finish.run();
                h.loader.close();
                h.loader.close();
                if (!queued) finish.run();
                h.loader.open(new File("ignored.csv"));
                h.drain();
                assertTrue(h.sources.isEmpty());
                assertTrue(h.failures.isEmpty());
                assertEquals(1, h.pending.size());
            }
        }
    }

    @Test void synchronousParserFailureIsMarshalledToUi() {
        Queue<Runnable> queue = new ArrayDeque<>();
        List<Throwable> failures = new ArrayList<>();
        IllegalStateException failure = new IllegalStateException("executor unavailable");
        FxLogLoadCoordinator loader = new FxLogLoadCoordinator(file -> { throw failure; },
                queue::add, (file, data) -> fail("Unexpected data"), (file, error) -> failures.add(error));
        loader.open(new File("sync.csv"));
        assertTrue(failures.isEmpty());
        queue.remove().run();
        assertEquals(List.of(failure), failures);
    }

    @Test void missingDatasetIsAnErrorNotAPane() {
        Harness h = new Harness();
        h.loader.open(new File("empty.csv"));
        h.pending.get(0).complete(null);
        h.drain();
        assertEquals(1, h.failures.size());
        assertTrue(h.datasets.isEmpty());
    }

    @Test void failedPresentationReportsErrorWithItsOwnSource() throws Exception {
        Queue<Runnable> queue = new ArrayDeque<>();
        List<File> failures = new ArrayList<>();
        LogDataset data = dataset("data.csv");
        FxLogLoadCoordinator loader = new FxLogLoadCoordinator(file -> CompletableFuture.completedFuture(data),
                queue::add, (file, parsed) -> { throw new IllegalArgumentException("bad presentation"); },
                (file, error) -> failures.add(file));
        File source = new File("presentation.csv").getAbsoluteFile();
        loader.open(source);
        queue.remove().run();
        assertEquals(List.of(source), failures);
    }

    private static LogDataset dataset(String name) throws IOException {
        return new RomRaiderCsvLogParser().parse(name, new StringReader("Time (msec),Value\n0,2\n100,3\n"));
    }

    private static final class Harness {
        final Queue<Runnable> ui = new ArrayDeque<>();
        final List<CompletableFuture<LogDataset>> pending = new ArrayList<>();
        final List<File> sources = new ArrayList<>();
        final List<LogDataset> datasets = new ArrayList<>();
        final List<File> errorSources = new ArrayList<>();
        final List<Throwable> failures = new ArrayList<>();
        final FxLogLoadCoordinator loader = new FxLogLoadCoordinator(file -> {
            CompletableFuture<LogDataset> result = new CompletableFuture<>();
            pending.add(result);
            return result;
        }, ui::add, (file, data) -> { sources.add(file); datasets.add(data); },
                (file, error) -> { errorSources.add(file); failures.add(error); });
        void drain() { while (!ui.isEmpty()) ui.remove().run(); }
    }
}
