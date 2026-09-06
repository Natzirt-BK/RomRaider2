/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.javafx;

import static org.junit.jupiter.api.Assertions.*;
import com.romraider.logger.analysis.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FxCsvLogLoaderTest {
    @TempDir Path directory;

    @Test void productionLoaderReadsCompatibleCsvOffCallerThreadAndRejectsOversizedFields() throws Exception {
        Path file = directory.resolve("synthetic.csv");
        Files.writeString(file, "\ufeffTime (msec),\"Fuel, trim (%)\"\r\n0,\r\n100,2.5\r\n");
        try (var loader = new FxCsvLogLoader()) {
            LogDataset data = loader.load(file.toFile()).get(5, TimeUnit.SECONDS);
            assertEquals(2, data.getRowCount()); assertTrue(Double.isNaN(data.getValue(0, 1)));
            assertEquals("Fuel, trim (%)", data.getChannels().get(1).getLabel());
            Files.writeString(file, "X".repeat(513) + "\n1\n");
            ExecutionException failure = assertThrows(ExecutionException.class, () -> loader.load(file.toFile()).get(5, TimeUnit.SECONDS));
            assertInstanceOf(IOException.class, failure.getCause());
        }
        Thread caller = Thread.currentThread();
        try (var loader = new FxCsvLogLoader(source -> { assertNotSame(caller, Thread.currentThread()); return data(); })) {
            assertNotNull(loader.load(file.toFile()).get(5, TimeUnit.SECONDS));
        }
    }

    @Test void replacingRunningLoadInterruptsItsWorkerAndOnlyRunsLatestQueuedRequest() throws Exception {
        CountDownLatch started = new CountDownLatch(1), interrupted = new CountDownLatch(1), release = new CountDownLatch(1);
        List<String> parsed = new CopyOnWriteArrayList<>();
        try (var loader = new FxCsvLogLoader(file -> {
            parsed.add(file.getName());
            if (file.getName().equals("first")) {
                started.countDown();
                try { release.await(); }
                catch (InterruptedException expected) { interrupted.countDown(); release.await(); }
            }
            return data();
        })) {
            var first = loader.load(new File("first"));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            CompletableFuture<LogDataset> latest = null;
            for (int i = 0; i < 100; i++) latest = loader.load(new File("queued-" + i));
            assertTrue(first.isCancelled()); assertTrue(interrupted.await(5, TimeUnit.SECONDS));
            release.countDown();
            assertNotNull(latest.get(5, TimeUnit.SECONDS));
            assertEquals(List.of("first", "queued-99"), parsed);
        } finally { release.countDown(); }
    }

    @Test void callerCancellationAndCloseInterruptActualTasksAndRejectNewLoads() throws Exception {
        for (boolean close : new boolean[]{false, true}) {
            CountDownLatch started = new CountDownLatch(1), interrupted = new CountDownLatch(1);
            try (var loader = new FxCsvLogLoader(file -> {
                started.countDown();
                try { new CountDownLatch(1).await(); return data(); }
                catch (InterruptedException expected) { interrupted.countDown(); throw expected; }
            })) {
                var pending = loader.load(new File("blocked"));
                assertTrue(started.await(5, TimeUnit.SECONDS));
                if (close) loader.close(); else pending.cancel(true);
                assertTrue(interrupted.await(5, TimeUnit.SECONDS)); assertTrue(pending.isCancelled());
                loader.close(); loader.close();
                assertThrows(ExecutionException.class, () -> loader.load(new File("closed")).get(5, TimeUnit.SECONDS));
            }
        }
    }

    @Test void failedParseDoesNotPoisonNextLoadOrLeakItsError() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        try (var loader = new FxCsvLogLoader(file -> {
            if (attempts.getAndIncrement() == 0) throw new IOException("synthetic failure");
            return data();
        })) {
            assertEquals("synthetic failure", assertThrows(ExecutionException.class,
                    () -> loader.load(new File("bad")).get(5, TimeUnit.SECONDS)).getCause().getMessage());
            assertEquals(1, loader.load(new File("good")).get(5, TimeUnit.SECONDS).getRowCount());
        }
    }

    private static LogDataset data() throws IOException {
        return new RomRaiderCsvLogParser().parse("synthetic.csv", new StringReader("Value\n1\n"));
    }
}
