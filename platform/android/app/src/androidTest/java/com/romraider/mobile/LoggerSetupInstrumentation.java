/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.mobile;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import com.romraider.mobile.logger.LoggerImportState;
import com.romraider.portable.PortableRomRaiderCsvWriter;
import com.romraider.portable.PortableRomDocument;
import com.romraider.portable.editor.PortableEcuDefinitionReader;
import com.romraider.portable.logger.definition.PortableLoggerDefinition;
import com.romraider.portable.logger.definition.PortableLoggerDefinitionReader;
import com.romraider.portable.logger.definition.PortableLoggerProfile;
import com.romraider.portable.logger.definition.PortableLoggerProfileReader;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.*;

/** Dependency-free framework instrumentation, restricted to the disposable automation app. */
public final class LoggerSetupInstrumentation extends Instrumentation {
    private String phase;
    private volatile Activity activity;
    private static final String DEFINITION = "<logger version=\"370\"><protocols><protocol id=\"SSM\"><parameters>"
            + parameter("P8", "Engine Speed", "rpm", "0x00000E", "x/4")
            + parameter("P1", "Battery Voltage", "V", "0x000010", "x/10")
            + parameter("P2", "Unselected channel", "%", "0x000012", "x")
            + "</parameters></protocol></protocols></logger>";
    private static final String PROFILE = "<profile protocol=\"SSM\"><parameters>"
            + "<parameter id=\"P8\" livedata=\"selected\" units=\"rpm\"/>"
            + "<parameter id=\"P1\" livedata=\"selected\" units=\"V\"/>"
            + "</parameters></profile>";

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        phase = arguments == null ? "verify" : arguments.getString("phase", "verify");
        start();
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            check(getTargetContext().getPackageName().equals("com.romraider.mobile.automation"),
                    "Refusing to test a non-automation installation");
            Intent intent = new Intent().setClassName(getTargetContext(), MainActivity.class.getName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivitySync(intent);
            awaitImports();
            if (phase.equals("seed")) seed();
            else if (phase.equals("gauges")) verifyGaugesOnly();
            else if (phase.equals("live-gauges")) verifyReadOnlySessionViewSwitch();
            else if (phase.equals("gauge-gallery")) captureGaugeGallery();
            else if (phase.equals("verify")) verify(2);
            else if (phase.equals("clear")) {
                verify(2);
                // Hold a save pending while the Activity is closed and reopened.
                CountDownLatch releaseSave = new CountDownLatch(1);
                Future<?> blocker = ((ExecutorService) field("LOGGER_SETUP_IO")).submit(() -> {
                    if (!releaseSave.await(15, TimeUnit.SECONDS)) throw new AssertionError("Save gate timed out");
                    return null;
                });
                try {
                    invoke("chooseLoggerChannels", new Class<?>[0]);
                    clickDialogText("Clear all");
                    Activity closing = activity;
                    runOnMainSync(closing::finish);
                    waitForIdleSync();
                    startActivitySync(new Intent().setClassName(getTargetContext(), MainActivity.class.getName())
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } finally { releaseSave.countDown(); }
                blocker.get(15, TimeUnit.SECONDS);
                awaitImports();
                verify(0);
            } else if (phase.equals("verify-empty")) verify(0);
            else if (phase.equals("corrupt")) {
                Files.write(new File(getTargetContext().getFilesDir(), "logger-setup.workspace").toPath(),
                        new byte[] {1, 2, 3});
            } else if (phase.equals("verify-corrupt")) {
                check(field("loggerDefinition") == null && field("loggerProfile") == null,
                        "Corrupt setup did not fail closed");
                check(((String) field("loggerSetupState")).contains("could not be restored"),
                        "Missing restore failure message");
                verifyNotRunning();
            } else throw new AssertionError("Unknown automation phase");
            result.putString("stream", "\nPASS logger setup automation: " + phase + "\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            StringWriter trace = new StringWriter();
            failure.printStackTrace(new PrintWriter(trace));
            result.putString("stream", "\nFAIL logger setup automation: " + phase + "\n" + trace);
            finish(Activity.RESULT_CANCELED, result);
        }
    }
    private void seed() throws Exception {
        verifyXmlImportSecurity();
        verifyRomSaveRecovery();
        verifyActivityRecreation();
        verifyMissingGaugeReading();
        File folder = getTargetContext().getFilesDir();
        File definition = new File(folder, "automation-definition.xml");
        File profile = new File(folder, "automation-profile.xml");
        Files.write(definition.toPath(), DEFINITION.getBytes(StandardCharsets.UTF_8));
        Files.write(profile.toPath(), PROFILE.getBytes(StandardCharsets.UTF_8));
        invoke("loadLoggerProfile", new Class<?>[] {Uri.class, String.class}, Uri.fromFile(profile), profile.getName());
        invoke("loadLoggerDefinition", new Class<?>[] {Uri.class, String.class}, Uri.fromFile(definition), definition.getName());
        awaitImports();
        verifySelection(2);
        invoke("loadLoggerDefinition", new Class<?>[] {Uri.class, String.class}, Uri.fromFile(definition), definition.getName());
        awaitImports();
        verifySelection(2);
        // The next launch must restore independently of both original documents.
        Files.delete(definition.toPath());
        Files.delete(profile.toPath());
        File recordings = new File(folder, "recordings");
        check(recordings.isDirectory() || recordings.mkdir(), "Cannot create synthetic recording folder");
        Files.write(new File(recordings, "automation-recording.csv.part").toPath(),
                ("500,P8,Engine Speed,750.0,rpm\n500,P1,Battery Voltage,13.25,V\n"
                + "600,P8,Engine Speed,800.0,rpm\n600,P1,Battery Voltage,13.24,V\n")
                        .getBytes(StandardCharsets.UTF_8));
        verify(2);
    }
    private void verifyXmlImportSecurity() throws Exception {
        String ecu = "<roms><rom><romid><xmlid>TEST</xmlid><filesize>8</filesize>"
                + "<internalidaddress>0</internalidaddress><internalidstring>TEST</internalidstring>"
                + "</romid><table type='2D' name='Fixture' storageaddress='4' storagetype='uint8' sizey='1'>"
                + "<scaling units='raw' expression='x' to_byte='x'/></table></rom></roms>";
        String[] roots = {"profile", "logger", "roms"};
        String[] documents = {PROFILE.replace("rpm", "°C"), DEFINITION, ecu};
        for (String encoding : new String[] {"UTF-8", "UTF-16", "UTF-16LE", "UTF-16BE",
                "UTF-32LE", "UTF-32BE", "ISO-8859-1", "windows-1252"}) {
            String declaration = "<?xml version='1.0' encoding='" + encoding + "'?>";
            for (int reader = 0; reader < documents.length; reader++) {
                String valid = declaration + "<!DOCTYPE " + roots[reader] + " [<!ELEMENT "
                        + roots[reader] + " ANY>]>" + documents[reader];
                readXmlFixture(reader, valid.getBytes(encoding));
                for (String entity : new String[] {"<!ENTITY x 'bad'>", "<!ENTITY % x 'bad'>",
                        "<!ENTITY x SYSTEM 'file:///nonexistent-rr2-entity'>"}) {
                    String hostile = declaration + "<!DOCTYPE " + roots[reader] + " [" + entity
                            + "]>" + documents[reader];
                    boolean rejected = false;
                    try { readXmlFixture(reader, hostile.getBytes(encoding)); }
                    catch (IOException expected) {
                        check(expected.getMessage().contains("entity declarations"), "Wrong XML rejection");
                        rejected = true;
                    }
                    check(rejected, "Android accepted entity declaration: " + encoding + " reader " + reader);
                }
            }
        }
        for (int reader = 0; reader < documents.length; reader++) {
            for (String encoding : new String[] {"UTF-8", "UTF-16LE", "UTF-16BE"}) {
                readXmlFixture(reader, ("\ufeff" + documents[reader]).getBytes(encoding));
            }
            byte[] truncated = ("\ufeff" + documents[reader]).getBytes(StandardCharsets.UTF_16LE);
            boolean rejected = false;
            try { readXmlFixture(reader, java.util.Arrays.copyOf(truncated, truncated.length - 1)); }
            catch (IOException expected) {
                check(expected.getMessage().contains("Malformed XML text"), "Wrong truncated XML rejection");
                rejected = true;
            }
            check(rejected, "Android accepted truncated UTF-16");
        }
        File dtd = new File(getTargetContext().getFilesDir(), "automation-external.dtd");
        try {
            Files.write(dtd.toPath(), "<!ATTLIST parameter units CDATA 'EXTERNAL_SENTINEL'>"
                    .getBytes(StandardCharsets.UTF_8));
            String xml = "<!DOCTYPE profile SYSTEM '" + dtd.toURI() + "'>"
                    + "<profile protocol='SSM'><parameters><parameter id='P8' livedata='selected'/>"
                    + "</parameters></profile>";
            check(PortableLoggerProfileReader.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
                    .selections().get(0).getUnits().isEmpty(), "Android loaded an external DTD");
        } finally { Files.deleteIfExists(dtd.toPath()); }
    }
    private void readXmlFixture(int reader, byte[] xml) throws IOException {
        ByteArrayInputStream input = new ByteArrayInputStream(xml);
        if (reader == 0) {
            check(PortableLoggerProfileReader.read(input).selections().get(0).getUnits().equals("°C"),
                    "Android XML encoding damaged profile units");
        } else if (reader == 1) {
            check(PortableLoggerDefinitionReader.read(input, "SSM").size() == 3,
                    "Android XML definition lost channels");
        } else {
            check(PortableEcuDefinitionReader.read(input, new PortableRomDocument("synthetic.bin",
                    new byte[] {'T', 'E', 'S', 'T', 1, 0, 0, 0})).getTables().size() == 1,
                    "Android XML ECU definition lost table");
        }
    }
    private void verifyRomSaveRecovery() throws Exception {
        File directory = new File(getTargetContext().getFilesDir(), "automation-rom-save");
        check(directory.isDirectory() || directory.mkdir(), "Cannot create isolated ROM-save fixture");
        PortableRomDocument document = new PortableRomDocument("Synthetic ROM", new byte[] {0, 0});
        document.replace(0, new byte[] {1});
        MobileRomRecoveryStore.save(directory, document);
        File recovery = new File(directory, "unsaved-rom.workspace");
        byte[] before = Files.readAllBytes(recovery.toPath());
        boolean failed = false;
        try {
            MobileRomSave.save(document, document.snapshot(), () -> new ByteArrayOutputStream() {
                @Override public void close() throws IOException { throw new IOException("Synthetic close failure"); }
            });
        } catch (IOException expected) { failed = true; }
        check(failed && document.hasChanges(), "Failed ROM save cleared dirty state");
        MobileRomRecoveryStore.save(directory, document);
        check(java.util.Arrays.equals(before, Files.readAllBytes(recovery.toPath())),
                "Failed ROM save changed the Android recovery file");
        check(java.util.Arrays.equals(document.snapshot(), MobileRomRecoveryStore.restore(directory).snapshot()),
                "Failed-save edits did not restore on Android");
        ByteArrayOutputStream saved = new ByteArrayOutputStream();
        check(MobileRomSave.save(document, document.snapshot(), () -> saved), "Successful save stayed dirty");
        check(java.util.Arrays.equals(saved.toByteArray(), new byte[] {1, 0}), "Wrong saved bytes");
        MobileRomRecoveryStore.save(directory, document);
        check(!recovery.exists(), "Successful save did not resolve its isolated recovery snapshot");
        System.out.println("PASS: Android ROM save close-failure recovery and successful save publication.");
    }
    private void verify(int channels) throws Exception {
        verifySelection(channels);
        verifyNotRunning();
        File recording = new File(getTargetContext().getFilesDir(), "recordings/automation-recording.csv.part");
        StringWriter csv = new StringWriter();
        PortableRomRaiderCsvWriter.writeSpool(recording, csv);
        check(csv.toString().equals("Time (msec),Engine Speed (rpm),Battery Voltage (V)\n"
                + "0,750,13.25\n100,800,13.24\n"), "Retained recording/export changed across restart or upgrade");
        Files.write(new File(getTargetContext().getFilesDir(), "automation-export.csv").toPath(),
                csv.toString().getBytes(StandardCharsets.UTF_8));
    }
    private void verifySelection(int channels) throws Exception {
        check(((PortableLoggerDefinition) field("loggerDefinition")).size() == 3, "Definition was not restored");
        PortableLoggerProfile profile = (PortableLoggerProfile) field("loggerProfile");
        check(profile.size() == channels, "Wrong selected channel count");
        if (channels == 2) {
            check(profile.selections().get(0).getId().equals("P8"), "Profile order changed");
            check(profile.selections().get(1).getUnits().equals("V"), "Selected units changed");
            check(field("loggerProfileName").equals("automation-profile.xml"), "Profile name changed");
        }
    }
    private void verifyNotRunning() throws Exception {
        check(field("liveLogger") == null && !(Boolean) field("previewRunning"),
                "Restore started logging automatically");
    }
    private void awaitImports() throws Exception {
        long deadline = SystemClock.uptimeMillis() + 15_000;
        while (SystemClock.uptimeMillis() < deadline) {
            Activity[] ready = {null};
            runOnMainSync(() -> {
                if (activity != null && !activity.isDestroyed()
                        && !((LoggerImportState) fieldUnchecked("loggerImports")).isLoading()) ready[0] = activity;
            });
            if (ready[0] != null && settle(ready[0])) return;
            SystemClock.sleep(50);
        }
        throw new AssertionError("Logger setup import timed out");
    }
    private boolean settle(Activity expected) throws Exception {
        waitForIdleSync();
        ExecutorService[] worker = {null};
        runOnMainSync(() -> {
            if (activity == expected && !expected.isDestroyed()) {
                worker[0] = (ExecutorService) fieldUnchecked("workerExecutor");
            }
        });
        if (worker[0] == null) return false;
        try { worker[0].submit(() -> { }).get(15, TimeUnit.SECONDS); }
        catch (RejectedExecutionException failure) {
            // Only retry a superseded/destroyed Activity, never a live executor failure.
            if (activity != expected || expected.isDestroyed()) return false;
            throw failure;
        }
        waitForIdleSync();
        ((ExecutorService) field("LOGGER_SETUP_IO")).submit(() -> { }).get(15, TimeUnit.SECONDS);
        waitForIdleSync();
        boolean[] settled = {false};
        runOnMainSync(() -> settled[0] = activity == expected && !expected.isDestroyed()
                && !((LoggerImportState) fieldUnchecked("loggerImports")).isLoading());
        return settled[0];
    }

    @Override public void callActivityOnResume(Activity resumed) {
        super.callActivityOnResume(resumed);
        if (resumed instanceof MainActivity) activity = resumed;
    }

    @Override public void callActivityOnDestroy(Activity destroyed) {
        super.callActivityOnDestroy(destroyed);
        if (activity == destroyed) activity = null;
    }

    private void verifyActivityRecreation() throws Exception {
        Activity previous = activity;
        ExecutorService previousWorker = (ExecutorService) field("workerExecutor");
        runOnMainSync(previous::recreate);
        long deadline = SystemClock.uptimeMillis() + 15_000;
        while ((activity == previous || activity == null) && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(50);
        }
        check(activity != null && activity != previous, "Activity recreation did not resume a replacement");
        awaitImports();
        check(previous.isDestroyed() && previousWorker.isShutdown(), "Old Activity worker was not closed");
        check(!((ExecutorService) field("workerExecutor")).isShutdown(), "Replacement Activity worker is closed");
        System.out.println("PASS: instrumentation follows recreated Activity and never reuses its closed worker.");
    }

    private void verifyMissingGaugeReading() {
        runOnMainSync(() -> {
            MobileGaugeView gauge = new MobileGaugeView(activity);
            gauge.layout(0, 0, 400, 450);
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(400, 450,
                    android.graphics.Bitmap.Config.ARGB_8888);
            try {
                MobileGaugeSnapshot snapshot = new MobileGaugeSnapshot("fixture", "Fixture", "V", "0.0", 12);
                for (MobileGaugeTheme theme : MobileGaugeTheme.values()) {
                  gauge.setTheme(theme);
                  for (double value : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0}) {
                    snapshot.accept(value);
                    gauge.setValue(snapshot.id, snapshot.name, snapshot.displayValue(), snapshot.units,
                            snapshot.value, snapshot.minimum, snapshot.maximum);
                    gauge.draw(new android.graphics.Canvas(bitmap));
                    check(gauge.getContentDescription().toString().contains("no valid data") == !Double.isFinite(value),
                            "Gauge accessibility did not reflect missing/recovered data");
                  }
                }
            } finally { bitmap.recycle(); }
        });
    }
    private void verifyGaugesOnly() throws Exception {
        verifyBundledNotices();
        verifyMissingGaugeReading();
        verifySelection(2);
        invoke("toggleLoggerPreview", new Class<?>[0]);
        long deadline = SystemClock.uptimeMillis() + 5000;
        while ((Integer) field("previewCycle") < 2 && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50);
        check((Boolean) field("previewRunning"), "Synthetic session did not start");
        Object session = field("previewSession");
        Object grid = field("loggerGaugeGrid");
        int cycle = (Integer) field("previewCycle");
        for (MobileGaugeTheme theme : MobileGaugeTheme.values()) {
            invoke("setLoggerGaugeTheme", new Class<?>[] {MobileGaugeTheme.class}, theme);
            invoke("showGaugesOnly", new Class<?>[0]);
            check((Boolean) field("gaugesVisible"), "Gauges view is not visible");
            check(field("previewSession") == session && field("loggerGaugeGrid") == grid
                    && (Boolean) field("previewRunning"), "Gauges view replaced/stopped the session");
            invoke("leaveGaugesOnly", new Class<?>[0]);
            check(field("previewSession") == session && field("loggerGaugeGrid") == grid,
                    "Returning to LOGGER replaced session or gauges");
        }
        deadline = SystemClock.uptimeMillis() + 5000;
        while ((Integer) field("previewCycle") <= cycle && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50);
        check((Integer) field("previewCycle") > cycle, "Recording stopped advancing across view switches");
        invoke("stopLoggerPreview", new Class<?>[] {String.class}, (Object) null);
        invoke("refreshGaugeAvailability", new Class<?>[0]);
        runOnMainSync(() -> {
            java.util.Map<?, ?> gauges = (java.util.Map<?, ?>) fieldUnchecked("loggerGaugeViews");
            for (Object gauge : gauges.values()) check(((MobileGaugeView) gauge).getContentDescription()
                    .toString().contains("STOPPED"), "Stopped gauge still appears live");
        });
        invoke("showLoggerGaugeDemo", new Class<?>[0]);
        invoke("setLoggerGaugeTheme", new Class<?>[] {MobileGaugeTheme.class}, MobileGaugeTheme.RALLY_PRECISION);
        invoke("showGaugesOnly", new Class<?>[0]);
        System.out.println("PASS: all themes preserve the same simulated session and gauge instances across view switches.");
    }

    private void verifyBundledNotices() throws Exception {
        for (String asset : new String[] {"license.txt", "STI-wordmark-NOTICE.txt"}) {
            try (java.io.InputStream input = getTargetContext().getAssets().open("notices/" + asset)) {
                check(input.read(new byte[256]) > 100, "Bundled notice is missing or empty: " + asset);
            }
        }
        invoke("showAbout", new Class<?>[0]);
        clickDialogText("Brand notice");
        clickDialogText("Close");
        invoke("showAbout", new Class<?>[0]);
        clickDialogText("Software license");
        clickDialogText("Close");
    }
    private void captureGaugeGallery() throws Exception {
        invoke("showLoggerGaugeDemo", new Class<?>[0]);
        invoke("showGaugesOnly", new Class<?>[0]);
        File directory = new File(getTargetContext().getExternalFilesDir(null), "gauge-gallery");
        check(directory.isDirectory() || directory.mkdirs(), "Cannot create render directory");
        for (MobileGaugeTheme theme : MobileGaugeTheme.values()) {
            if (theme.instrumentStyle() == null) continue;
            invoke("setLoggerGaugeTheme", new Class<?>[] {MobileGaugeTheme.class}, theme);
            waitForIdleSync();
            SystemClock.sleep(250); // Allow a native layout/draw frame before screen capture.
            AccessibilityNodeInfo active = getUiAutomation().getRootInActiveWindow();
            long deadline = SystemClock.uptimeMillis() + 5000;
            while ((active == null || !getTargetContext().getPackageName().contentEquals(active.getPackageName()))
                    && SystemClock.uptimeMillis() < deadline) {
                SystemClock.sleep(50);
                active = getUiAutomation().getRootInActiveWindow();
            }
            check(active != null && getTargetContext().getPackageName().contentEquals(active.getPackageName()),
                    "Gauge render is obstructed by another window: " + (active == null ? "none" : active.getPackageName()));
            android.graphics.Bitmap bitmap = getUiAutomation().takeScreenshot();
            check(bitmap != null, "Native screenshot failed");
            try (OutputStream file = new FileOutputStream(new File(directory, theme.name() + ".png"))) {
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, file), "PNG encoding failed");
            } finally { bitmap.recycle(); }
        }
    }
    private void verifyReadOnlySessionViewSwitch() throws Exception {
        verifySelection(2);
        File spool = File.createTempFile("synthetic-gauge-session-", ".csv.part", getTargetContext().getCacheDir());
        com.romraider.portable.PortableLogSession log = com.romraider.portable.PortableLogSession.streaming(spool, 20);
        java.util.concurrent.atomic.AtomicInteger identifies = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger closes = new java.util.concurrent.atomic.AtomicInteger();
        com.romraider.portable.logger.ReadOnlyLoggerTransport transport = new com.romraider.portable.logger.ReadOnlyLoggerTransport() {
            public String identifyEcu(com.romraider.portable.logger.PortableLoggerProtocol protocol) {
                identifies.incrementAndGet(); return "SYNTHETIC";
            }
            public byte[] read(com.romraider.portable.logger.PortableLoggerQueryBatch batch) {
                SystemClock.sleep(25);
                byte[] result = new byte[batch.getAddresses().length];
                java.util.Arrays.fill(result, (byte) 120); return result;
            }
            public void closeReadOnlyKLine() { closes.incrementAndGet(); }
        };
        com.romraider.mobile.logger.ReadOnlyLoggerSession session = new com.romraider.mobile.logger.ReadOnlyLoggerSession(
                transport, (PortableLoggerDefinition) field("loggerDefinition"),
                (PortableLoggerProfile) field("loggerProfile"), log,
                new com.romraider.mobile.logger.ReadOnlyLoggerSession.Listener() {
                    public void onIdentified(String id, int ready, int unavailable) {
                        setField("liveEcuIdentified", true);
                    }
                    public void onValues(String id, long timestamp,
                            java.util.List<com.romraider.portable.logger.PortableLoggerValue> values, int samples) {
                        invoke("updateLoggerGauges", new Class<?>[] {java.util.List.class}, values);
                    }
                    public void onStopped(String message) { setField("liveLogger", null); }
                });
        invoke("clearLoggerGauges", new Class<?>[0]);
        setField("liveLogger", session); setField("liveLog", log);
        Thread worker = new Thread(session::run, "synthetic-read-only-gauge-test");
        worker.start();
        try {
            long deadline = SystemClock.uptimeMillis() + 5000;
            while (log.size() < 4 && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(30);
            check(log.size() >= 4 && identifies.get() == 1, "Read-only synthetic logger did not begin");
            int before = log.size();
            long bytes = spool.length();
            Object grid = field("loggerGaugeGrid");
            for (MobileGaugeTheme theme : MobileGaugeTheme.values()) {
                invoke("setLoggerGaugeTheme", new Class<?>[] {MobileGaugeTheme.class}, theme);
                invoke("showGaugesOnly", new Class<?>[0]);
                check(field("liveLogger") == session && field("liveLog") == log && field("loggerGaugeGrid") == grid,
                        "Mounted view replaced the running read-only session, writer or gauges");
                invoke("leaveGaugesOnly", new Class<?>[0]);
                check(closes.get() == 0 && identifies.get() == 1, "View switching disconnected/reidentified the ECU");
            }
            deadline = SystemClock.uptimeMillis() + 5000;
            while (log.size() <= before && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(30);
            check(log.size() > before && spool.length() > bytes, "CSV spool stopped growing during view switches");
        } finally {
            session.stop(); worker.join(5000);
            check(!worker.isAlive(), "Synthetic logger failed to stop");
            setField("liveLogger", null); setField("liveLog", null);
            try {
                StringWriter csv = new StringWriter(); log.writeRomRaiderCsv(csv);
                check(csv.toString().startsWith("Time (msec),"), "Recording no longer exports standard RomRaider CSV");
                check(closes.get() == 1, "Transport was not closed exactly once");
            } finally { log.discard(); }
        }
        System.out.println("PASS: actual read-only session and disk-backed CSV writer survive every theme/view switch with a fake transport.");
    }
    private void setField(String name, Object value) {
        runOnMainSync(() -> {
            try {
                Field field = MainActivity.class.getDeclaredField(name);
                field.setAccessible(true); field.set(activity, value);
            } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
        });
    }
    private Object field(String name) throws Exception {
        Object[] result = {null};
        runOnMainSync(() -> result[0] = fieldUnchecked(name));
        return result[0];
    }
    private Object fieldUnchecked(String name) {
        try {
            Field field = MainActivity.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(activity);
        } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }
    private void invoke(String name, Class<?>[] types, Object... args) {
        runOnMainSync(() -> {
            try {
                Method method = MainActivity.class.getDeclaredMethod(name, types);
                method.setAccessible(true);
                method.invoke(activity, args);
            } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
        });
    }
    private void clickDialogText(String text) {
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            if (root != null) for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(text)) {
                if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { waitForIdleSync(); return; }
            }
            SystemClock.sleep(50);
        }
        throw new AssertionError("Dialog button not found: " + text);
    }
    private static String parameter(String id, String name, String units, String address, String expression) {
        return "<parameter id=\"" + id + "\" name=\"" + name + "\"><address length=\"2\">" + address
                + "</address><conversions><conversion units=\"" + units + "\" expr=\"" + expression
                + "\" format=\"0.00\"/></conversions></parameter>";
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
