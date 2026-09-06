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
import com.romraider.mobile.logger.ReadOnlyRecording;
import com.romraider.portable.PortableRomRaiderCsvWriter;
import com.romraider.portable.PortableRomDocument;
import com.romraider.portable.editor.PortableEcuDefinitionReader;
import com.romraider.portable.logger.definition.PortableLoggerDefinition;
import com.romraider.portable.logger.definition.PortableLoggerDefinitionReader;
import com.romraider.portable.logger.definition.PortableLoggerProfile;
import com.romraider.portable.logger.definition.PortableLoggerProfileReader;
import com.romraider.portable.logger.definition.PortableLoggerSetup;
import com.romraider.portable.logger.PortableLoggerProtocol;
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
            else if (phase.equals("mounted-fullscreen")) verifyMountedFullScreen();
            else if (phase.equals("mounted-layouts")) verifyMountedLayouts();
            else if (phase.equals("live-gauges")) verifyReadOnlySessionViewSwitch();
            else if (phase.equals("calculated-gauges")) verifyCalculatedGauges();
            else if (phase.equals("channel-transfer")) verifyChannelTransfer();
            else if (phase.equals("background-service")) verifyBackgroundService();
            else if (phase.equals("background-denied")) verifyBackgroundDenied();
            else if (phase.equals("background-process-death")) prepareBackgroundProcessDeath();
            else if (phase.equals("background-after-death")) verifyBackgroundAfterDeath();
            else if (phase.equals("recording-recovery")) verifyRecordingRecovery();
            else if (phase.equals("csv-import")) verifyCsvImport();
            else if (phase.equals("gauge-gallery") || phase.equals("gauge-gallery-landscape")) captureGaugeGallery();
            else if (phase.equals("gauge-contact-sheet")) captureGaugeContactSheet();
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

    private void verifyChannelTransfer() throws Exception {
        File folder = getTargetContext().getFilesDir();
        File definitionFile = new File(folder, "transfer-definition.xml");
        File source = new File(folder, "transfer.rr2logger");
        File bad = new File(folder, "bad-transfer.rr2logger");
        File exported = new File(folder, "exported.rr2logger");
        byte[] definitionBytes = DEFINITION.getBytes(StandardCharsets.UTF_8);
        Files.write(definitionFile.toPath(), definitionBytes);
        invoke("loadLoggerDefinition", new Class<?>[] {Uri.class, String.class}, Uri.fromFile(definitionFile), definitionFile.getName());
        awaitImports();
        PortableLoggerProfile desired = new PortableLoggerProfile("SSM", java.util.Arrays.asList(
                new PortableLoggerProfile.Selection("P1", "V"),
                new PortableLoggerProfile.Selection("P8", "rpm")), java.util.Collections.emptyList());
        PortableLoggerSetup setup = PortableLoggerSetup.capture(PortableLoggerProtocol.SSM, definitionBytes,
                (PortableLoggerDefinition) field("loggerDefinition"), desired);
        Files.write(source.toPath(), setup.encode());
        Object original = field("loggerProfile");
        invoke("loadPortableLoggerSetup", new Class<?>[] {Uri.class}, Uri.fromFile(source));
        awaitTransfer();
        clickDialogText("Cancel");
        check(field("loggerProfile") == original, "Cancel changed the selected profile");
        invoke("loadPortableLoggerSetup", new Class<?>[] {Uri.class}, Uri.fromFile(source));
        awaitTransfer();
        clickDialogText("Use setup");
        PortableLoggerProfile imported = (PortableLoggerProfile) field("loggerProfile");
        check(imported.selections().get(0).getId().equals("P1")
                && imported.selections().get(1).getId().equals("P8"), "Import changed channel order");
        verifyNotRunning();
        Files.write(bad.toPath(), new byte[] {1, 2, 3});
        invoke("loadPortableLoggerSetup", new Class<?>[] {Uri.class}, Uri.fromFile(bad));
        awaitTransfer();
        check(field("loggerProfile") == imported, "Invalid import changed selection");
        String mismatch = new String(setup.encode(), StandardCharsets.UTF_8)
                .replace(setup.definitionSha256(), String.join("", java.util.Collections.nCopies(64, "0")));
        Files.write(bad.toPath(), mismatch.getBytes(StandardCharsets.UTF_8));
        invoke("loadPortableLoggerSetup", new Class<?>[] {Uri.class}, Uri.fromFile(bad));
        awaitTransfer();
        check(field("loggerProfile") == imported, "Wrong definition import changed selection");
        // A reviewed dialog cannot apply after an intervening configuration change.
        invoke("loadPortableLoggerSetup", new Class<?>[] {Uri.class}, Uri.fromFile(source));
        awaitTransfer();
        setField("loggerSetupRevision", (Integer) field("loggerSetupRevision") + 1);
        clickDialogText("Use setup");
        check(field("loggerProfile") == imported, "Stale review replaced selection");
        CountDownLatch release = new CountDownLatch(1);
        Future<?> blocker = ((ExecutorService) field("workerExecutor")).submit(() -> {
            if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("Transfer gate timed out");
            return null;
        });
        try {
            invoke("loadPortableLoggerSetup", new Class<?>[] {Uri.class}, Uri.fromFile(source));
            setField("loggerSetupRevision", (Integer) field("loggerSetupRevision") + 1);
        } finally { release.countDown(); }
        blocker.get(10, TimeUnit.SECONDS);
        awaitTransfer();
        check(field("loggerProfile") == imported, "Late worker replaced edited selection");
        invoke("toggleLoggerPreview", new Class<?>[0]);
        check((Boolean) field("previewRunning"), "Synthetic logger did not start");
        invoke("loadPortableLoggerSetup", new Class<?>[] {Uri.class}, Uri.fromFile(source));
        check((Boolean) field("previewRunning") && field("loggerProfile") == imported,
                "Transfer interrupted active synthetic logging");
        invoke("stopLoggerPreview", new Class<?>[] {String.class}, (Object) null);
        invoke("preparePortableLoggerSetupExport", new Class<?>[0]);
        awaitTransfer();
        clickDialogText("Cancel");
        check(field("setupExportBytes") == null, "Cancelled export retained a pending destination write");
        // Exercise the Activity's actual stream writer with a frozen, reviewed snapshot.
        setField("setupExportBytes", setup.encode());
        invoke("savePortableLoggerSetup", new Class<?>[] {Uri.class}, Uri.fromFile(exported));
        ((ExecutorService) field("workerExecutor")).submit(() -> {}).get(10, TimeUnit.SECONDS);
        check(java.util.Arrays.equals(setup.encode(), Files.readAllBytes(exported.toPath())), "Export bytes changed");
        check(java.util.Arrays.equals(setup.encode(), Files.readAllBytes(source.toPath())), "Import altered source file");
        check(field("setupExportBytes") == null, "Export snapshot was not consumed");
        // Clear is intentional and survives restart; restore the fixture afterward.
        PortableLoggerSetup empty = PortableLoggerSetup.capture(PortableLoggerProtocol.SSM, definitionBytes,
                (PortableLoggerDefinition) field("loggerDefinition"), new PortableLoggerProfile("SSM",
                        java.util.Collections.emptyList(), java.util.Collections.emptyList()));
        Files.write(source.toPath(), empty.encode());
        invoke("loadPortableLoggerSetup", new Class<?>[] {Uri.class}, Uri.fromFile(source));
        awaitTransfer();
        clickDialogText("Use setup");
        check(((PortableLoggerProfile) field("loggerProfile")).size() == 0, "Empty setup selected default channels");
        Files.write(source.toPath(), setup.encode());
        invoke("loadPortableLoggerSetup", new Class<?>[] {Uri.class}, Uri.fromFile(source));
        awaitTransfer();
        clickDialogText("Use setup");
        ((ExecutorService) field("LOGGER_SETUP_IO")).submit(() -> {}).get(10, TimeUnit.SECONDS);
        Activity closing = activity;
        runOnMainSync(closing::finish);
        waitForIdleSync();
        startActivitySync(new Intent().setClassName(getTargetContext(), MainActivity.class.getName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        awaitImports();
        check(((PortableLoggerProfile) field("loggerProfile")).selections().get(0).getId().equals("P1"),
                "Imported order did not survive restart");
        verifyNotRunning();
        for (File file : new File[] {definitionFile, source, bad, exported}) Files.deleteIfExists(file.toPath());
        System.out.println("PASS: channel transfer review, cancel, invalid/mismatched files, stale worker/dialog, active logger guard, stream export, empty selection and restart.");
    }

    private void awaitTransfer() throws Exception {
        long deadline = SystemClock.uptimeMillis() + 15_000;
        while ((Boolean) field("setupTransferLoading") && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(30);
        check(!(Boolean) field("setupTransferLoading"), "Setup transfer timed out");
        waitForIdleSync();
    }
    private void verifyXmlImportSecurity() throws Exception {
        String orderedProfile = "<profile protocol='SSM'><parameters>"
                + "<parameter id='P8' livedata='selected' units='rpm' rr2-order='2'/>"
                + "<parameter id='P1' livedata='selected' units='V' rr2-order='0'/></parameters>"
                + "<switches><switch id='S1' dash='selected' rr2-order='1'/></switches></profile>";
        PortableLoggerProfile ordered = PortableLoggerProfileReader.read(new ByteArrayInputStream(orderedProfile.getBytes(StandardCharsets.UTF_8)));
        check(ordered.selections().get(0).getId().equals("P1") && ordered.selections().get(1).getId().equals("S1")
                && ordered.selections().get(2).getId().equals("P8"), "Android lost the desktop cross-category profile order");
        try {
            PortableLoggerProfileReader.read(new ByteArrayInputStream(orderedProfile.replace("rr2-order='2'", "rr2-order='0'").getBytes(StandardCharsets.UTF_8)));
            throw new AssertionError("Android accepted duplicate profile order");
        } catch (IOException expected) { }
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
        check(!(Boolean) field("previewRunning"),
                "Restore started logging automatically");
        runOnMainSync(() -> {
            ReadOnlyLoggingService service = (ReadOnlyLoggingService) fieldUnchecked("recordingService");
            check(service != null && !service.busy() && service.recording() == null,
                    "Restoration acquired or resumed a service recording");
        });
    }
    private void awaitImports() throws Exception {
        long deadline = SystemClock.uptimeMillis() + 15_000;
        while (SystemClock.uptimeMillis() < deadline) {
            Activity[] ready = {null};
            runOnMainSync(() -> {
                if (activity != null && !activity.isDestroyed()
                        && fieldUnchecked("recordingService") != null
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
    private void verifyMountedFullScreen() throws Exception {
        invoke("showLoggerGaugeDemo", new Class<?>[0]);
        invoke("showGaugesOnly", new Class<?>[0]);
        Object grid = field("loggerGaugeGrid");
        runOnMainSync(() -> ((android.widget.Button) fieldUnchecked("mountedModeButton")).performClick());
        assertMountedWindow(true);
        check(field("loggerGaugeGrid") == grid, "Full screen replaced the gauge grid");
        for (int orientation : new int[]{android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT}) {
            rotateMountedDisplay(orientation);
            assertMountedWindow(true);
            check(field("loggerGaugeGrid") == grid, "Rotation replaced the mounted gauge grid");
        }
        runOnMainSync(() -> check(clickViewText((android.view.View) fieldUnchecked("gaugesPage"), "STOP"),
                "Mounted Stop control missing"));
        invoke("refreshGaugeAvailability", new Class<?>[0]);
        invoke("refreshRecording", new Class<?>[0]);
        assertMountedWindow(true); // STOPPED still stays awake; never manufactures live data.
        check(((android.widget.TextView) field("gaugesStatus")).getText().toString().contains("STOPPED"),
                "Stopped mounted display lost its state label");
        shell("input keyevent KEYCODE_HOME");
        long deadline = SystemClock.uptimeMillis() + 10_000;
        while ((Boolean) field("activityResumed") && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50);
        check(!(Boolean) field("activityResumed"), "Mounted Activity did not background");
        check(!screenAwake(), "Mounted view retained keep-screen-on in the background");
        // singleTop reuses the Activity: startActivitySync would wait for a new onCreate forever.
        getTargetContext().startActivity(new Intent().setClassName(getTargetContext(), MainActivity.class.getName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        deadline = SystemClock.uptimeMillis() + 10_000;
        while (!(Boolean) field("activityResumed") && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50);
        check((Boolean) field("activityResumed"), "Mounted Activity did not return");
        waitForIdleSync();
        assertMountedWindow(true);
        shell("input keyevent KEYCODE_BACK");
        deadline = SystemClock.uptimeMillis() + 5000;
        while ((Boolean) field("mountedFullScreen") && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50);
        assertMountedWindow(false);
        check((Boolean) field("gaugesVisible") && field("loggerGaugeGrid") == grid,
                "Back left the dashboard or replaced gauges");
        invoke("setMountedFullScreen", new Class<?>[]{boolean.class}, true);
        invoke("leaveGaugesOnly", new Class<?>[0]);
        assertMountedWindow(false);
        check(field("loggerGaugeGrid") == grid, "Leaving mounted view rebuilt the gauges");
        System.out.println("PASS: immersive gauges hide bars, stay awake when stopped, release awake in background, and exit through Back/LOGGER.");
    }

    private void verifyMountedLayouts() throws Exception {
        invoke("showLoggerGaugeDemo", new Class<?>[0]);
        invoke("showGaugesOnly", new Class<?>[0]);
        invoke("setMountedFullScreen", new Class<?>[]{boolean.class}, true);
        Object grid = field("loggerGaugeGrid");
        Object profile = field("loggerProfile");
        for (MobileGaugeTheme theme : new MobileGaugeTheme[]{MobileGaugeTheme.RR2_CLASSIC, MobileGaugeTheme.STI_NIGHT}) {
            invoke("setLoggerGaugeTheme", new Class<?>[]{MobileGaugeTheme.class}, theme);
            for (int orientation : new int[]{android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}) {
                rotateMountedDisplay(orientation);
                for (int count = 1; count <= 6; count++) {
                    invoke("setMountedGaugeCount", new Class<?>[]{int.class}, count);
                    CountDownLatch frame = new CountDownLatch(1);
                    runOnMainSync(() -> ((android.view.View) grid).postOnAnimation(
                            () -> ((android.view.View) grid).postOnAnimation(frame::countDown)));
                    check(frame.await(5, TimeUnit.SECONDS), "Mounted layout frame did not complete");
                    final int expected = count;
                    runOnMainSync(() -> {
                        android.view.ViewGroup group = (android.view.ViewGroup) grid;
                        android.view.View viewport = (android.view.View) fieldUnchecked("mountedGaugeViewport");
                        check(group.getWidth() == viewport.getWidth() && group.getHeight() == viewport.getHeight(),
                                "Mounted grid does not fill the available viewport");
                        check(group.getChildCount() == 8, "Changing display count discarded hidden gauges");
                        int visible = 0;
                        for (int i = 0; i < group.getChildCount(); i++) {
                            android.view.View child = group.getChildAt(i);
                            if (child.getVisibility() != android.view.View.VISIBLE) continue;
                            visible++;
                            check(child.getWidth() > 0 && child.getHeight() > 0 && child.getLeft() >= 0 && child.getTop() >= 0
                                    && child.getRight() <= group.getWidth() && child.getBottom() <= group.getHeight(),
                                    "Mounted gauge is clipped or requires scrolling");
                            for (int j = 0; j < i; j++) {
                                android.view.View other = group.getChildAt(j);
                                if (other.getVisibility() == android.view.View.VISIBLE)
                                    check(child.getRight() <= other.getLeft() || other.getRight() <= child.getLeft()
                                            || child.getBottom() <= other.getTop() || other.getBottom() <= child.getTop(),
                                            "Mounted gauge cells overlap");
                            }
                        }
                        check(visible == expected, "Wrong visible gauge count");
                    });
                    check(field("loggerGaugeGrid") == grid && field("loggerProfile") == profile && screenAwake(),
                            "Mounted layout replaced profile/grid or released keep-awake");
                    File renders = new File(getTargetContext().getExternalFilesDir(null), "mounted-layouts");
                    check(renders.isDirectory() || renders.mkdirs(), "Cannot create mounted render directory");
                    captureMountedScreenshot(new File(renders, theme.name() + "-"
                            + (orientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            ? "portrait" : "landscape") + "-" + count + ".png"));
                }
            }
        }
        invoke("chooseMountedGaugeCount", new Class<?>[0]);
        clickDialogText("3 gauges");
        check((Integer) field("mountedGaugeCount") == 3, "Layout picker did not apply selection");
        invoke("leaveGaugesOnly", new Class<?>[0]);
        runOnMainSync(() -> {
            android.view.ViewGroup group = (android.view.ViewGroup) grid;
            for (int i = 0; i < group.getChildCount(); i++)
                check(group.getChildAt(i).getVisibility() == android.view.View.VISIBLE, "LOGGER did not restore hidden gauges");
        });
        verifyActivityRecreation();
        check((Integer) field("mountedGaugeCount") == 3 && !(Boolean) field("mountedFullScreen"),
                "Display count did not restore independently of mounted/running state");
        invoke("setMountedGaugeCount", new Class<?>[]{int.class}, 6);
        System.out.println("PASS: all 1–6 mounted layouts fit portrait/landscape with retained gauges/profile, no scrolling, and count-only persistence.");
    }

    private boolean screenAwake() {
        boolean[] awake = new boolean[1];
        runOnMainSync(() -> awake[0] = (activity.getWindow().getAttributes().flags
                & android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0);
        return awake[0];
    }

    private void rotateMountedDisplay(int orientation) {
        runOnMainSync(() -> activity.setRequestedOrientation(orientation));
        int expected = orientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                ? android.content.res.Configuration.ORIENTATION_LANDSCAPE
                : android.content.res.Configuration.ORIENTATION_PORTRAIT;
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (activity.getResources().getConfiguration().orientation != expected
                && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50);
        check(activity.getResources().getConfiguration().orientation == expected, "Mounted rotation did not complete");
        waitForIdleSync();
    }

    private void assertMountedWindow(boolean enabled) throws Exception {
        check((Boolean) field("mountedFullScreen") == enabled, "Unexpected mounted mode state");
        check(screenAwake() == enabled, "Idle mounted keep-screen-on state incorrect");
        runOnMainSync(() -> check(((android.view.View) fieldUnchecked("workspaceTabs")).getVisibility()
                == (enabled ? android.view.View.GONE : android.view.View.VISIBLE), "Mounted navigation chrome incorrect"));
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            boolean[] barsVisible = new boolean[1];
            long deadline = SystemClock.uptimeMillis() + 5000;
            do {
                runOnMainSync(() -> {
                    android.view.WindowInsets insets = activity.getWindow().getDecorView().getRootWindowInsets();
                    barsVisible[0] = insets == null || insets.isVisible(android.view.WindowInsets.Type.statusBars())
                            || insets.isVisible(android.view.WindowInsets.Type.navigationBars());
                });
                if (barsVisible[0] != enabled) break;
                SystemClock.sleep(50);
            } while (SystemClock.uptimeMillis() < deadline);
            check(barsVisible[0] != enabled, "System bars did not follow mounted mode");
        }
    }

    private void captureGaugeContactSheet() throws Exception {
        // Draw the actual mobile View for every selectable theme, including legacy faces.
        File file = new File(getTargetContext().getExternalFilesDir(null), "all-mobile-gauge-styles.png");
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(1440, 1930,
                android.graphics.Bitmap.Config.ARGB_8888);
        try {
            runOnMainSync(() -> {
                android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
                canvas.drawColor(0xFF0B1117);
                android.graphics.Paint text = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                text.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
                text.setColor(0xFFF0F4F8); text.setTextSize(34);
                canvas.drawText("ROMRAIDER2 / THE GAUGE COLLECTION", 32, 51, text);
                text.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
                text.setTextSize(18); text.setColor(0xFFA6B3C0);
                canvas.drawText("21 mobile styles • Actual Android rendering • Simulated values • 1.1.3 development source", 32, 85, text);
                float density = getTargetContext().getResources().getDisplayMetrics().density;
                int index = 0;
                for (MobileGaugeTheme theme : MobileGaugeTheme.values()) {
                    float x = 32 + (index % 4) * 350, y = 124 + (index / 4) * 295;
                    text.setTextSize(18); text.setColor(0xFFE6EDF4);
                    text.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
                    canvas.drawText(String.format(java.util.Locale.ROOT, "%02d  %s", index + 1, theme.displayName), x, y, text);
                    MobileGaugeView gauge = new MobileGaugeView(activity);
                    gauge.setTheme(theme);
                    gauge.setValue("P8", "Engine Speed", "4210", "rpm", 4210, 800, 6650);
                    gauge.setDataState("SIMULATED");
                    int width = Math.round(320 * density);
                    int height = Math.round((theme.instrumentStyle() == null ? 205 : 250) * density);
                    gauge.measure(android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
                            android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY));
                    gauge.layout(0, 0, width, height);
                    canvas.save(); canvas.translate(x, y + 12); canvas.scale(1 / density, 1 / density);
                    gauge.draw(canvas); canvas.restore();
                    index++;
                }
                text.setTextSize(17); text.setColor(0xFFA6B3C0);
                canvas.drawText("Display scales are not engine limits. Configure while parked.", 32, 1910, text);
            });
            try (OutputStream output = new FileOutputStream(file)) {
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output), "Contact sheet encoding failed");
            }
        } finally { bitmap.recycle(); }
    }

    private void captureGaugeGallery() throws Exception {
        rotateMountedDisplay(phase.endsWith("landscape")
                ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        invoke("showLoggerGaugeDemo", new Class<?>[0]);
        invoke("showGaugesOnly", new Class<?>[0]);
        invoke("setMountedFullScreen", new Class<?>[]{boolean.class}, true);
        File directory = new File(getTargetContext().getExternalFilesDir(null), phase);
        check(directory.isDirectory() || directory.mkdirs(), "Cannot create render directory");
        for (MobileGaugeTheme theme : MobileGaugeTheme.values()) {
            if (theme.instrumentStyle() == null) continue;
            invoke("setLoggerGaugeTheme", new Class<?>[] {MobileGaugeTheme.class}, theme);
            captureMountedScreenshot(new File(directory, theme.name() + ".png"));
        }
    }

    private void captureMountedScreenshot(File destination) throws Exception {
        // View bounds can be current while the compositor still presents
        // the preceding count or Android's rotation animation.
        waitForIdleSync();
        SystemClock.sleep(600);
        AccessibilityNodeInfo active = getUiAutomation().getRootInActiveWindow();
        long deadline = SystemClock.uptimeMillis() + 5000;
        while ((active == null || !getTargetContext().getPackageName().contentEquals(active.getPackageName()))
                && SystemClock.uptimeMillis() < deadline) {
            // Android can present its one-time immersive-mode tutorial after the first frame.
            // Acknowledge only that known tutorial; every other obstruction remains a failure.
            if (active != null && "com.android.systemui".contentEquals(active.getPackageName())
                    && !active.findAccessibilityNodeInfosByText("Viewing full screen").isEmpty()) {
                for (AccessibilityNodeInfo confirm : active.findAccessibilityNodeInfosByText("Got it"))
                    confirm.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
            SystemClock.sleep(50);
            active = getUiAutomation().getRootInActiveWindow();
        }
        check(active != null && getTargetContext().getPackageName().contentEquals(active.getPackageName()),
                "Gauge render is obstructed by another window: " + (active == null ? "none" : active.getPackageName()));
        android.graphics.Bitmap bitmap = getUiAutomation().takeScreenshot();
        check(bitmap != null, "Native screenshot failed");
        try (OutputStream file = new FileOutputStream(destination)) {
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, file), "PNG encoding failed");
        } finally { bitmap.recycle(); }
    }
    private void verifyCalculatedGauges() throws Exception {
        String definitionXml = "<logger><protocol id='SSM'><parameters>"
                + parameter("P8", "Engine Speed", "rpm", "0x0E", "x/4")
                + parameter("P12", "Mass Airflow", "g/s", "0x13", "x/100")
                + parameter("P21", "Pulse", "ms", "0x20", "x/100")
                + "<parameter id='P200' name='Engine Load'><depends><ref parameter='P12'/><ref parameter='P8'/></depends>"
                + "<conversions><conversion units='g/rev' expr='P12*60/P8' format='0.00'/></conversions></parameter>"
                + "<parameter id='P201' name='Injector Duty'><depends><ref parameter='P8'/><ref parameter='P21'/></depends>"
                + "<conversions><conversion units='%' expr='P8*[P21:ms]/1200' format='0.00'/></conversions></parameter>"
                + "</parameters></protocol></logger>";
        String profileXml = "<profile protocol='SSM'><parameters>"
                + "<parameter id='P200' livedata='selected' units='g/rev'/>"
                + "<parameter id='P201' livedata='selected' units='%'/></parameters></profile>";
        File definition = new File(getTargetContext().getFilesDir(), "calculated-definition.xml");
        File profile = new File(getTargetContext().getFilesDir(), "calculated-profile.xml");
        Files.write(definition.toPath(), definitionXml.getBytes(StandardCharsets.UTF_8));
        Files.write(profile.toPath(), profileXml.getBytes(StandardCharsets.UTF_8));
        invoke("loadLoggerProfile", new Class<?>[] {Uri.class, String.class}, Uri.fromFile(profile), profile.getName());
        invoke("loadLoggerDefinition", new Class<?>[] {Uri.class, String.class}, Uri.fromFile(definition), definition.getName());
        awaitImports();
        Files.delete(definition.toPath()); Files.delete(profile.toPath());
        Activity closing = activity;
        runOnMainSync(closing::finish);
        waitForIdleSync();
        startActivitySync(new Intent().setClassName(getTargetContext(), MainActivity.class.getName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        awaitImports();
        verifyNotRunning();
        check(((PortableLoggerDefinition) field("loggerDefinition")).parameters().size() == 5,
                "Calculated definition did not restore");
        check(((PortableLoggerProfile) field("loggerProfile")).selections().size() == 2,
                "Hidden dependencies expanded the saved profile");
        invoke("toggleLoggerPreview", new Class<?>[0]);
        try {
            long deadline = SystemClock.uptimeMillis() + 5000;
            while ((Integer) field("previewCycle") < 2 && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50);
            com.romraider.portable.PortableLogSession log =
                    (com.romraider.portable.PortableLogSession) field("previewSession");
            check(log != null && log.size() >= 4, "Calculated simulation did not produce samples");
            for (com.romraider.portable.PortableLogSample sample : log.snapshot()) {
                check((sample.getChannelId().equals("P200") || sample.getChannelId().equals("P201"))
                        && Double.isFinite(sample.getValue()), "Calculated simulation exposed an input or invalid value");
            }
            check(((java.util.Map<?, ?>) field("loggerGaugeViews")).size() == 2,
                    "Hidden dependencies became visible gauges");
        } finally { invoke("stopLoggerPreview", new Class<?>[] {String.class}, (Object) null); }
        verifyReadOnlySessionViewSwitch(true);
    }
    private void verifyReadOnlySessionViewSwitch() throws Exception {
        verifySelection(2);
        verifyReadOnlySessionViewSwitch(false);
    }
    private void verifyReadOnlySessionViewSwitch(boolean calculated) throws Exception {
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
                java.util.Arrays.fill(result, (byte) 120);
                if (calculated) for (int i = 0; i < result.length; i++) {
                    switch (batch.getAddresses()[i]) {
                        case 14: result[i] = 0x2e; break;
                        case 15: result[i] = (byte) 0xe0; break;
                        case 19: result[i] = 0x27; break;
                        case 20: result[i] = 0x10; break;
                        case 32: result[i] = 0x01; break;
                        case 33: result[i] = 0x00; break;
                        default: throw new AssertionError("Unexpected calculated input address");
                    }
                }
                return result;
            }
            public void closeReadOnlyKLine() { closes.incrementAndGet(); }
        };
        invoke("clearLoggerGauges", new Class<?>[0]);
        ReadOnlyLoggingService service = (ReadOnlyLoggingService) field("recordingService");
        ReadOnlyRecording session = startServiceRecording(service, transport,
                (PortableLoggerDefinition) field("loggerDefinition"),
                (PortableLoggerProfile) field("loggerProfile"), log, () -> { }, false);
        try {
            long deadline = SystemClock.uptimeMillis() + 5000;
            // Worker samples can precede the Activity's bounded 100 ms snapshot poll.
            while ((log.size() < 4 || field("displayedRecording") != session)
                    && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(30);
            check(log.size() >= 4 && identifies.get() == 1, "Read-only synthetic logger did not begin");
            check(field("displayedRecording") == session, "Activity did not attach the service recording before view-switch checks");
            int before = log.size();
            long bytes = spool.length();
            Object grid = field("loggerGaugeGrid");
            for (MobileGaugeTheme theme : MobileGaugeTheme.values()) {
                invoke("setLoggerGaugeTheme", new Class<?>[] {MobileGaugeTheme.class}, theme);
                invoke("showGaugesOnly", new Class<?>[0]);
                invoke("setMountedFullScreen", new Class<?>[]{boolean.class}, true);
                invoke("setMountedGaugeCount", new Class<?>[]{int.class}, 1 + theme.ordinal() % 6);
                check(field("displayedRecording") == session, "Mounted view replaced the displayed recording owner");
                check(session.completedLog() == null, "Mounted view exposed a completed writer: " + session.snapshot().message());
                check(field("loggerGaugeGrid") == grid, "Mounted view replaced the gauge grid");
                invoke("setMountedFullScreen", new Class<?>[]{boolean.class}, false);
                check(screenAwake(), "Exiting full screen released the active foreground logger's screen flag");
                invoke("leaveGaugesOnly", new Class<?>[0]);
                check(closes.get() == 0 && identifies.get() == 1, "View switching disconnected/reidentified the ECU");
            }
            deadline = SystemClock.uptimeMillis() + 5000;
            while (log.size() <= before && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(30);
            check(log.size() > before && spool.length() > bytes, "CSV spool stopped growing during view switches");
            invoke("showGaugesOnly", new Class<?>[0]);
            invoke("setMountedFullScreen", new Class<?>[]{boolean.class}, true);
            runOnMainSync(() -> check(clickViewText((android.view.View) fieldUnchecked("gaugesPage"), "STOP"),
                    "Mounted live Stop control missing"));
            waitForServiceIdle(service);
            invoke("refreshRecording", new Class<?>[0]);
            check(screenAwake() && (Boolean) field("mountedFullScreen"), "Stopping capture left/dimmed mounted mode");
            invoke("leaveGaugesOnly", new Class<?>[0]);
            check(!screenAwake(), "Stopped logger retained screen-awake after mounted exit");
        } finally {
            runOnMainSync(service::stop);
            waitForServiceIdle(service);
            check(session.completedLog() == log, "Service replaced the completed writer");
            try {
                StringWriter csv = new StringWriter(); log.writeRomRaiderCsv(csv);
                check(csv.toString().startsWith("Time (msec),"), "Recording no longer exports standard RomRaider CSV");
                if (calculated) {
                    check(csv.toString().startsWith("Time (msec),Engine Load (g/rev),Injector Duty (%)\n"),
                            "Calculated CSV changed selected order or exposed hidden inputs");
                    for (com.romraider.portable.PortableLogSample sample : log.snapshot()) {
                        check(sample.getChannelId().equals("P200") || sample.getChannelId().equals("P201"),
                                "Unselected input reached the recording");
                        double expected = sample.getChannelId().equals("P200") ? 2.0 : 6.4;
                        check(Math.abs(sample.getValue() - expected) < 1e-9, "Calculated recording value is incorrect");
                    }
                }
                check(closes.get() == 1, "Transport was not closed exactly once");
            } finally { log.discard(); }
        }
        System.out.println("PASS: actual read-only session and disk-backed CSV writer survive every theme/view switch with a fake transport.");
    }
    private void verifyBackgroundService() throws Exception {
        ReadOnlyLoggingService service = (ReadOnlyLoggingService) field("recordingService");
        android.content.pm.ServiceInfo info = getTargetContext().getPackageManager().getServiceInfo(
                new android.content.ComponentName(getTargetContext(), ReadOnlyLoggingService.class), 0);
        check(!info.exported && info.getForegroundServiceType() == android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                "Automation must use its isolated USB-free service manifest, never weaken production permissions");
        ServiceFixture first = new ServiceFixture(service);
        runOnMainSync(() -> check(!service.start(null, null, first.definition, first.profile)
                && service.recording() == null, "Idle service accepted missing USB authority"));
        first.start(false);
        android.app.PendingIntent oldStop;
        try {
            waitForSamples(first, 4);
            runOnMainSync(() -> {
                check(service.busy(), "Service not active");
                check((Boolean) serviceField(service, "foreground"), "Service was not promoted");
                android.os.PowerManager.WakeLock wake = (android.os.PowerManager.WakeLock) serviceField(service, "wakeLock");
                check(wake != null && wake.isHeld(), "Recording wake lock missing");
                check(!service.start(null, null, first.definition, first.profile), "Missing USB authority was accepted");
            });
            android.service.notification.StatusBarNotification[] notifications = getTargetContext()
                    .getSystemService(android.app.NotificationManager.class).getActiveNotifications();
            check(notifications.length == 1 && notifications[0].getNotification().actions.length == 1,
                    "Recording notification or Stop action is missing");
            oldStop = notifications[0].getNotification().actions[0].actionIntent;
            Object profileBefore = field("loggerProfile");
            invoke("chooseLoggerChannels", new Class<?>[0]);
            check(field("loggerProfile") == profileBefore, "Active capture allowed setup replacement");
            waitForIdleSync();
            AccessibilityNodeInfo setupRoot = getUiAutomation().getRootInActiveWindow();
            long setupWindowDeadline = SystemClock.uptimeMillis() + 5000;
            while (setupRoot == null && SystemClock.uptimeMillis() < setupWindowDeadline) {
                SystemClock.sleep(50);
                setupRoot = getUiAutomation().getRootInActiveWindow();
            }
            check(setupRoot != null, "Active recording screen was unavailable to accessibility");
            check(setupRoot.findAccessibilityNodeInfosByText("Channels (fewer = faster cycles)").isEmpty(),
                    "Active recording opened editable channel controls");
            invoke("showGaugesOnly", new Class<?>[0]);
            invoke("setMountedFullScreen", new Class<?>[]{boolean.class}, true);
            int before = first.recording.snapshot().samples();
            shell("input keyevent KEYCODE_HOME");
            long stoppedDeadline = SystemClock.uptimeMillis() + 10_000;
            while ((Boolean) field("activityResumed") && SystemClock.uptimeMillis() < stoppedDeadline) SystemClock.sleep(50);
            check(!(Boolean) field("activityResumed"), "Activity did not leave foreground");
            check(!screenAwake(), "Background recording retained the display-awake flag");
            waitForSamples(first, before + 4);
            shell("input keyevent KEYCODE_SLEEP");
            before = first.recording.snapshot().samples();
            waitForSamples(first, before + 4);
            shell("input keyevent KEYCODE_WAKEUP");
            shell("input keyevent 82");
            // Exercise the actual notification return action; singleTop can reuse the screen.
            notifications[0].getNotification().contentIntent.send();
            long resumedDeadline = SystemClock.uptimeMillis() + 10_000;
            while (!(Boolean) field("activityResumed") && SystemClock.uptimeMillis() < resumedDeadline) SystemClock.sleep(50);
            check((Boolean) field("activityResumed"), "Existing Activity did not resume");
            awaitImports();
            check(field("recordingService") == service, "Returning to app replaced the service");
            verifyActivityRecreation();
            invoke("refreshRecording", new Class<?>[0]);
            check(field("recordingService") == service && field("displayedRecording") == first.recording,
                    "Activity replacement restarted the recording owner");
            check(first.identifies.get() == 1 && first.releases.get() == 0,
                    "Screen lifecycle disconnected or reidentified the synthetic ECU");
            oldStop.send();
            waitForServiceIdle(service);
            check(first.recording.completedLog() == first.log && first.releases.get() == 1,
                    "Notification Stop did not finish the same recording and release once");
            check(first.recording.snapshot().samples() > 4, "Background recording did not accumulate samples");
            StringWriter csv = new StringWriter(); first.log.writeRomRaiderCsv(csv);
            check(csv.toString().startsWith("Time (msec),Engine Speed (rpm),Battery Voltage (V)\n"),
                    "Service recording changed the standard RomRaider CSV layout");
            runOnMainSync(() -> {
                check(!(Boolean) serviceField(service, "foreground") && serviceField(service, "wakeLock") == null,
                        "Foreground notification/wake lock survived completion");
            });
            check(getTargetContext().getSystemService(android.app.NotificationManager.class)
                    .getActiveNotifications().length == 0, "Stopped notification was not removed");
        } finally { first.stop(); }

        ServiceFixture second = new ServiceFixture(service);
        second.start(false);
        try {
            waitForSamples(second, 4);
            oldStop.send();
            SystemClock.sleep(300);
            check(second.recording.snapshot().active(), "An old notification stopped a newer recording");
            runOnMainSync(() -> {
                String token = (String) serviceField(service, "token");
                service.onStartCommand(new Intent().setAction("com.romraider.mobile.START_RECORDING")
                        .putExtra("recording_token", token), 0, 999);
                check(service.recording() == second.recording, "Duplicate start replaced the recording");
            });
            check(second.identifies.get() == 1, "Duplicate start probed the ECU again");
            second.fail = true;
            waitForServiceIdle(service);
            check(second.recording.snapshot().message().contains("Synthetic service detach"),
                    "Read failure was hidden");
            check(second.releases.get() == 1 && second.log.size() >= 4, "Read failure lost the completed recording");
        } finally { second.stop(); }

        ServiceFixture cancelled = new ServiceFixture(service);
        cancelled.start(true); // Cancel on the same main-thread turn, before onStartCommand.
        try {
            waitForServiceIdle(service);
            check(cancelled.identifies.get() == 0 && cancelled.releases.get() == 1,
                    "Cancelled pending start acquired the ECU or leaked the transferred adapter");
        } finally { cancelled.stop(); }
        System.out.println("PASS: real Android service promotion, Home/screen-off capture, Activity recreation, notification Stop, stale/duplicate commands and failure cleanup with a synthetic transport.");
    }

    private final class ServiceFixture {
        final ReadOnlyLoggingService service;
        final PortableLoggerDefinition definition;
        final PortableLoggerProfile profile;
        final com.romraider.portable.PortableLogSession log;
        final File spool;
        final java.util.concurrent.atomic.AtomicInteger identifies = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger releases = new java.util.concurrent.atomic.AtomicInteger();
        volatile boolean fail;
        ReadOnlyRecording recording;
        final com.romraider.portable.logger.ReadOnlyLoggerTransport transport = new com.romraider.portable.logger.ReadOnlyLoggerTransport() {
            public String identifyEcu(PortableLoggerProtocol protocol) { identifies.incrementAndGet(); return "SYNTHETIC_SERVICE"; }
            public byte[] read(com.romraider.portable.logger.PortableLoggerQueryBatch batch) throws IOException {
                SystemClock.sleep(25);
                if (fail) throw new IOException("Synthetic service detach");
                byte[] values = new byte[batch.getAddresses().length];
                java.util.Arrays.fill(values, (byte) 120);
                return values;
            }
            public void closeReadOnlyKLine() { }
        };
        ServiceFixture(ReadOnlyLoggingService service) throws Exception {
            this.service = service;
            definition = PortableLoggerDefinitionReader.read(new ByteArrayInputStream(DEFINITION.getBytes(StandardCharsets.UTF_8)), "SSM");
            profile = PortableLoggerProfileReader.read(new ByteArrayInputStream(PROFILE.getBytes(StandardCharsets.UTF_8)));
            File folder = new File(getTargetContext().getFilesDir(), "recordings");
            check(folder.isDirectory() || folder.mkdirs(), "Fixture recording directory unavailable");
            spool = File.createTempFile("automation-service-", ".csv.part", folder);
            log = com.romraider.portable.PortableLogSession.streaming(spool, 1);
        }
        void start(boolean cancelImmediately) {
            recording = startServiceRecording(service, transport, definition, profile, log,
                    () -> releases.incrementAndGet(), cancelImmediately);
        }
        void stop() throws Exception {
            runOnMainSync(service::stop);
            waitForServiceIdle(service);
            log.finish();
        }
    }

    private ReadOnlyRecording startServiceRecording(ReadOnlyLoggingService service,
            com.romraider.portable.logger.ReadOnlyLoggerTransport transport,
            PortableLoggerDefinition definition, PortableLoggerProfile profile,
            com.romraider.portable.PortableLogSession log, Closeable fullClose, boolean cancelImmediately) {
        ReadOnlyRecording[] result = {null};
        runOnMainSync(() -> {
            try {
                Class<?> leaseType = Class.forName(ReadOnlyLoggingService.class.getName() + "$Lease");
                Constructor<?> constructor = leaseType.getDeclaredConstructor(Closeable.class);
                constructor.setAccessible(true);
                Object lease = constructor.newInstance(fullClose);
                Method accept = ReadOnlyLoggingService.class.getDeclaredMethod("accept", ReadOnlyRecording.ResourceFactory.class,
                        leaseType, android.hardware.usb.UsbDevice.class, PortableLoggerDefinition.class, PortableLoggerProfile.class);
                accept.setAccessible(true);
                ReadOnlyRecording.ResourceFactory factory = cancellation -> new ReadOnlyRecording.Resources(transport, log, (Closeable) lease);
                check((Boolean) accept.invoke(service, factory, lease, null, definition, profile), "Fixture start was rejected");
                result[0] = service.recording();
                if (cancelImmediately) service.stop();
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
        return result[0];
    }

    private static Object serviceField(ReadOnlyLoggingService service, String name) {
        try {
            Field field = ReadOnlyLoggingService.class.getDeclaredField(name); field.setAccessible(true);
            return field.get(service);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private void verifyBackgroundDenied() throws Exception {
        check(!getTargetContext().getSystemService(android.app.NotificationManager.class).areNotificationsEnabled(),
                "Notification-denial fixture was not configured");
        ReadOnlyLoggingService service = (ReadOnlyLoggingService) field("recordingService");
        ServiceFixture fixture = new ServiceFixture(service);
        fixture.start(false);
        try {
            waitForSamples(fixture, 4);
            runOnMainSync(() -> check((Boolean) serviceField(service, "foreground"),
                    "Notification denial incorrectly prevented foreground execution"));
            invoke("stopLiveLogger", new Class<?>[] {String.class}, "Stopped from app.");
            waitForServiceIdle(service);
            check(fixture.releases.get() == 1 && fixture.recording.completedLog() == fixture.log,
                    "In-app Stop failed with notifications denied");
        } finally { fixture.stop(); }
    }

    private void prepareBackgroundProcessDeath() throws Exception {
        ReadOnlyLoggingService service = (ReadOnlyLoggingService) field("recordingService");
        ServiceFixture fixture = new ServiceFixture(service);
        fixture.start(false);
        waitForSamples(fixture, 4);
        java.util.List<String> rows = Files.readAllLines(fixture.spool.toPath(), StandardCharsets.UTF_8);
        check(rows.size() >= 2, "Completed fixture cycle was not flushed");
        String prefix = rows.get(0) + "\n" + rows.get(1) + "\n";
        String evidence = fixture.spool.getName() + "\n"
                + java.util.Base64.getEncoder().encodeToString(prefix.getBytes(StandardCharsets.UTF_8));
        try (FileOutputStream output = new FileOutputStream(new File(getTargetContext().getFilesDir(), "automation-service-death.evidence"))) {
            output.write(evidence.getBytes(StandardCharsets.UTF_8)); output.getFD().sync();
        }
        Bundle ready = new Bundle(); ready.putString("stream", "READY for synthetic recording process-death check\n");
        sendStatus(1, ready);
        android.os.Process.killProcess(android.os.Process.myPid());
        throw new AssertionError("Synthetic recording process unexpectedly survived SIGKILL");
    }

    private void verifyBackgroundAfterDeath() throws Exception {
        verifyNotRunning();
        String evidence = new String(Files.readAllBytes(new File(getTargetContext().getFilesDir(),
                "automation-service-death.evidence").toPath()), StandardCharsets.UTF_8);
        String[] fields = evidence.split("\n");
        check(fields.length == 2 && fields[0].matches("automation-service-[A-Za-z0-9-]+\\.csv\\.part"), "Unexpected death fixture identity");
        File spool = new File(new File(getTargetContext().getFilesDir(), "recordings"), fields[0]);
        byte[] prefix = java.util.Base64.getDecoder().decode(fields[1]);
        byte[] retained = Files.readAllBytes(spool.toPath());
        check(retained.length >= prefix.length, "Process death removed a flushed recording prefix");
        for (int i = 0; i < prefix.length; i++) check(prefix[i] == retained[i], "Flushed recording prefix changed after process death");
        try (com.romraider.portable.PortableRecordingRecovery.Prepared recovery =
                com.romraider.portable.PortableRecordingRecovery.prepare(spool, getTargetContext().getCacheDir())) {
            StringWriter csv = new StringWriter(); recovery.writeTo(csv);
            check(csv.toString().startsWith("Time (msec),Engine Speed (rpm),Battery Voltage (V)\n")
                    && recovery.values() >= 2, "Post-kill spool could not export its completed records");
        }
        check(getTargetContext().getSystemService(android.app.NotificationManager.class).getActiveNotifications().length == 0,
                "A dead recording's notification reappeared");
        System.out.println("PASS: abrupt process death retains the flushed spool prefix and restart creates no recording or USB session.");
    }

    private void verifyRecordingRecovery() throws Exception {
        File folder = getTargetContext().getFilesDir();
        File source = new File(folder, "automation-recovery-source.csv.part");
        File destination = new File(folder, "automation-recovery-export.csv");
        String prefix = "500,a,Engine Speed,750,rpm\n500,b,Battery Voltage,13.25,V\n"
                + "600,a,Engine Speed,800,rpm\n600,b,Battery Voltage,13.24,V\n";
        String csv = "Time (msec),Engine Speed (rpm),Battery Voltage (V)\n0,750,13.25\n100,800,13.24\n";
        String partial = prefix + "700,c,\"unfinished";
        String sentinel = "Existing destination must survive validation and cancellation";
        try {
            Files.write(source.toPath(), prefix.getBytes(StandardCharsets.UTF_8));
            setField("archiveToExport", source);
            invoke("onActivityResult", new Class<?>[] {int.class, int.class, Intent.class}, 18, Activity.RESULT_CANCELED, null);
            check(field("archiveToExport") == null, "Cancelled document picker retained an export request");
            setField("archiveToExport", source);
            invoke("onActivityResult", new Class<?>[] {int.class, int.class, Intent.class}, 18, Activity.RESULT_OK,
                    new Intent().setData(Uri.fromFile(destination)));
            awaitArchiveExport(false);
            check(csv.equals(new String(Files.readAllBytes(destination.toPath()), StandardCharsets.UTF_8)),
                    "Clean retained export changed RomRaider CSV format");
            Files.write(source.toPath(), partial.getBytes(StandardCharsets.UTF_8));
            Files.write(destination.toPath(), sentinel.getBytes(StandardCharsets.UTF_8));
            invoke("prepareArchivedExport", new Class<?>[] {File.class, Uri.class}, source, Uri.fromFile(destination));
            awaitArchiveExport(true);
            check(sentinel.equals(new String(Files.readAllBytes(destination.toPath()), StandardCharsets.UTF_8)),
                    "Recovery opened destination before review");
            clickDialogText("Cancel");
            awaitArchiveExport(false);
            check(sentinel.equals(new String(Files.readAllBytes(destination.toPath()), StandardCharsets.UTF_8)),
                    "Cancelled recovery changed destination");
            invoke("prepareArchivedExport", new Class<?>[] {File.class, Uri.class}, source, Uri.fromFile(destination));
            awaitArchiveExport(true);
            clickDialogText("Export recovered data");
            awaitArchiveExport(false);
            check(csv.equals(new String(Files.readAllBytes(destination.toPath()), StandardCharsets.UTF_8)),
                    "Reviewed partial-tail export lost completed values");
            Files.copy(destination.toPath(), new File(folder, "automation-recovered-export.csv").toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            check(partial.equals(new String(Files.readAllBytes(source.toPath()), StandardCharsets.UTF_8)),
                    "Recovery changed original source");
            Files.write(destination.toPath(), sentinel.getBytes(StandardCharsets.UTF_8));
            Files.write(source.toPath(), (prefix + "600,c,C,bad,V\n").getBytes(StandardCharsets.UTF_8));
            invoke("prepareArchivedExport", new Class<?>[] {File.class, Uri.class}, source, Uri.fromFile(destination));
            awaitArchiveExport(false);
            check(sentinel.equals(new String(Files.readAllBytes(destination.toPath()), StandardCharsets.UTF_8)),
                    "Corrupt completed row damaged destination");
            check(field("pendingArchiveRecovery") == null, "Corrupt completed row produced a recovery review");
            Files.write(source.toPath(), partial.getBytes(StandardCharsets.UTF_8));
            invoke("prepareArchivedExport", new Class<?>[] {File.class, Uri.class}, source, Uri.fromFile(destination));
            awaitArchiveExport(true);
            com.romraider.portable.PortableRecordingRecovery.Prepared stale =
                    (com.romraider.portable.PortableRecordingRecovery.Prepared) field("pendingArchiveRecovery");
            android.app.AlertDialog staleDialog = (android.app.AlertDialog) field("archiveRecoveryDialog");
            check(staleDialog != null && staleDialog.isShowing(), "Recovery review was not displayed");
            verifyActivityRecreation();
            check(!staleDialog.isShowing(), "Recreation leaked the old recovery review window");
            check(field("pendingArchiveRecovery") == null && !(Boolean) field("archiveExportPending"),
                    "Recreation retained stale recovery authority");
            try { stale.writeTo(new StringWriter()); throw new AssertionError("Recreation leaked prepared export"); }
            catch (IOException expected) { }
            check(sentinel.equals(new String(Files.readAllBytes(destination.toPath()), StandardCharsets.UTF_8)),
                    "Recreation accepted an unreviewed export");
            ExecutorService oldWorker = (ExecutorService) field("workerExecutor");
            CountDownLatch releasePreparation = new CountDownLatch(1);
            Future<?> preparationGate = oldWorker.submit(() -> {
                if (!releasePreparation.await(15, TimeUnit.SECONDS)) throw new AssertionError("Recovery preparation gate timed out");
                return null;
            });
            try {
                invoke("prepareArchivedExport", new Class<?>[] {File.class, Uri.class}, source, Uri.fromFile(destination));
                Future<?> pending = (Future<?>) field("archivePreparation");
                check(pending != null && !pending.isDone(), "Preparation was not queued behind the test gate");
                verifyActivityRecreation();
                check(pending.isCancelled(), "Destroyed Activity did not cancel queued recovery preparation");
            } finally { releasePreparation.countDown(); }
            preparationGate.get(10, TimeUnit.SECONDS);
            check(oldWorker.awaitTermination(10, TimeUnit.SECONDS), "Old Activity worker did not finish cleanup");
            check(sentinel.equals(new String(Files.readAllBytes(destination.toPath()), StandardCharsets.UTF_8)),
                    "Late preparation wrote an unreviewed destination");
            Files.write(source.toPath(), prefix.getBytes(StandardCharsets.UTF_8));
            invoke("prepareArchivedExport", new Class<?>[] {File.class, Uri.class}, source, Uri.fromFile(folder));
            awaitArchiveExport(false);
            check(prefix.equals(new String(Files.readAllBytes(source.toPath()), StandardCharsets.UTF_8)),
                    "Failed destination damaged recovery source");
            verifyNotRunning();
            System.out.println("PASS: native recovery validates before destination writes, requires tail review, preserves originals, and rejects stale Activity authority.");
        } finally { Files.deleteIfExists(source.toPath()); Files.deleteIfExists(destination.toPath()); }
    }

    private void awaitArchiveExport(boolean review) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 15_000;
        while (SystemClock.uptimeMillis() < deadline) {
            if (review ? field("pendingArchiveRecovery") != null : !(Boolean) field("archiveExportPending")) {
                waitForIdleSync(); return;
            }
            SystemClock.sleep(30);
        }
        throw new AssertionError("Recording export/review did not finish");
    }

    private void verifyCsvImport() throws Exception {
        File folder = getTargetContext().getFilesDir();
        File large = new File(folder, "automation-large-import.csv");
        File second = new File(folder, "automation-second-import.csv");
        File invalid = new File(folder, "automation-invalid-import.csv");
        try {
            try (Writer output = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(large), StandardCharsets.UTF_8))) {
                output.write("Time (msec),RPM (rpm),Voltage (V)\n");
                for (int row = 0; row < 130_001; row++) output.write(row + ",900,13\n");
            }
            invoke("loadLogSummary", new Class<?>[] {Uri.class}, Uri.fromFile(large));
            awaitCsvImport();
            com.romraider.portable.PortableLogCsvReader.Summary summary =
                    (com.romraider.portable.PortableLogCsvReader.Summary) field("importedLogSummary");
            check(summary != null && summary.values() == 260_002 && summary.channels().size() == 2,
                    "Android large CSV import was truncated to the previous full-sample cap");
            check(summary.channels().get(0).latest().getTimestampMillis() == 130_000
                    && summary.channels().get(0).finite() == 130_001, "Large imported summary is incomplete");
            Object retainedCard = field("importedLogCard");
            Files.write(invalid.toPath(), new byte[] {'T', 'i', 'm', 'e', ',', 'A', '\n', '0', ',', (byte) 0xff, '\n'});
            invoke("loadLogSummary", new Class<?>[] {Uri.class}, Uri.fromFile(invalid));
            awaitCsvImport();
            check(field("importedLogSummary") == summary && field("importedLogCard") == retainedCard,
                    "Invalid UTF-8 replaced the previous imported summary");
            check(((String) field("logImportStatus")).contains("not imported"), "Invalid import was not reported");
            StringBuilder header = new StringBuilder("Time"), row = new StringBuilder("0");
            for (int i = 0; i < 14; i++) { header.append(",C").append(i); row.append(',').append(i); }
            Files.write(second.toPath(), (header + "\n" + row + "\n").getBytes(StandardCharsets.UTF_8));
            invoke("loadLogSummary", new Class<?>[] {Uri.class}, Uri.fromFile(second));
            awaitCsvImport();
            summary = (com.romraider.portable.PortableLogCsvReader.Summary) field("importedLogSummary");
            check(summary.channels().size() == 14 && (Integer) field("importedLogPage") == 0, "Paged summary missing");
            // Use the native button even when the summary starts outside the viewport.
            android.view.View card = (android.view.View) field("importedLogCard");
            runOnMainSync(() -> check(clickViewText(card, "NEXT CHANNELS"), "Next-channel control missing"));
            check((Integer) field("importedLogPage") == 1 && field("importedLogSummary") == summary,
                    "Paging reimported or replaced the file");
            java.util.concurrent.ThreadPoolExecutor executor = (java.util.concurrent.ThreadPoolExecutor) field("logImportExecutor");
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch entered = new CountDownLatch(1);
            Future<?> gate = executor.submit(() -> {
                entered.countDown();
                if (!release.await(15, TimeUnit.SECONDS)) throw new AssertionError("CSV gate timed out");
                return null;
            });
            try {
                // This executor has only one queue slot. A submitted gate is not
                // necessarily running yet; otherwise the import may be rejected.
                check(entered.await(5, TimeUnit.SECONDS), "CSV gate did not start");
                invoke("loadLogSummary", new Class<?>[] {Uri.class}, Uri.fromFile(large));
                Future<?> superseded = (Future<?>) field("logImportTask");
                check(superseded != null, "CSV import was not queued: " + field("logImportStatus"));
                invoke("loadLogSummary", new Class<?>[] {Uri.class}, Uri.fromFile(second));
                check(superseded.isCancelled() && executor.getQueue().size() == 1, "Superseded work was not cancelled/purged");
                invoke("showGaugesOnly", new Class<?>[0]);
                check(!(Boolean) field("logImportLoading") && executor.getQueue().isEmpty(), "Gauge switch retained queued CSV work");
            } finally { release.countDown(); }
            gate.get(10, TimeUnit.SECONDS); executor.submit(() -> { }).get(10, TimeUnit.SECONDS); waitForIdleSync();
            check((Boolean) field("gaugesVisible") && field("importedLogSummary") == summary,
                    "Late CSV work replaced the mounted dashboard or previous summary");
            invoke("leaveGaugesOnly", new Class<?>[0]);
            // Keep start/cancel in one UI turn so even a fast parser cannot apply
            // its completion between the two actions.
            runOnMainSync(() -> {
                invokeUnchecked("loadLogSummary", new Class<?>[] {Uri.class}, Uri.fromFile(large));
                invokeUnchecked("cancelLogImport", new Class<?>[] {String.class}, "CSV import cancelled; previous summary retained.");
            });
            executor.submit(() -> { }).get(15, TimeUnit.SECONDS); waitForIdleSync();
            check(field("importedLogSummary") == summary && !(Boolean) field("logImportLoading"), "Cancelled import replaced summary");
            invoke("showEditor", new Class<?>[0]);
            invoke("loadLogSummary", new Class<?>[] {Uri.class}, Uri.fromFile(large));
            check(!(Boolean) field("loggerVisible") && !(Boolean) field("logImportLoading"), "CSV import hijacked the editor");
            invoke("showLogger", new Class<?>[0]);
            check(field("importedLogSummary") == summary && field("importedLogCard") != null, "Returning to LOGGER lost imported summary");
            ServiceFixture fixture = new ServiceFixture((ReadOnlyLoggingService) field("recordingService"));
            try {
                fixture.start(false); waitForSamples(fixture, 4);
                invoke("loadLogSummary", new Class<?>[] {Uri.class}, Uri.fromFile(large));
                check(!(Boolean) field("logImportLoading") && field("importedLogSummary") == summary,
                        "CSV import changed active recording display");
                waitForSamples(fixture, 8);
                check(fixture.identifies.get() == 1, "CSV action restarted live capture");
            } finally { fixture.stop(); }
            CountDownLatch releaseOld = new CountDownLatch(1);
            CountDownLatch enteredOld = new CountDownLatch(1);
            Future<?> oldGate = executor.submit(() -> {
                enteredOld.countDown();
                if (!releaseOld.await(15, TimeUnit.SECONDS)) throw new AssertionError("CSV close gate timed out");
                return null;
            });
            try {
                check(enteredOld.await(5, TimeUnit.SECONDS), "CSV close gate did not start");
                invoke("loadLogSummary", new Class<?>[] {Uri.class}, Uri.fromFile(large));
                Future<?> pending = (Future<?>) field("logImportTask");
                check(pending != null, "Closing CSV import was not queued: " + field("logImportStatus"));
                verifyActivityRecreation();
                check(pending.isCancelled(), "Activity destruction did not cancel CSV import");
            } finally { releaseOld.countDown(); }
            try { oldGate.get(10, TimeUnit.SECONDS); } catch (ExecutionException interrupted) {
                check(interrupted.getCause() instanceof InterruptedException, "Unexpected old CSV worker failure");
            }
            check(executor.awaitTermination(10, TimeUnit.SECONDS), "Destroyed Activity CSV worker survived");
            check(field("importedLogSummary") == null && !(Boolean) field("logImportLoading"), "Old CSV result reached replacement Activity");
            verifyNotRunning();
            System.out.println("PASS: Android streamed 260,002 values, retained prior summaries on failure/cancel, paged channels, and rejected stale/active-capture imports.");
        } finally {
            Files.deleteIfExists(large.toPath()); Files.deleteIfExists(second.toPath()); Files.deleteIfExists(invalid.toPath());
        }
    }

    private boolean clickViewText(android.view.View view, String text) {
        if (view instanceof android.widget.Button && ((android.widget.Button) view).getText().toString().equals(text)) return view.performClick();
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (clickViewText(group.getChildAt(i), text)) return true;
        }
        return false;
    }

    private void awaitCsvImport() throws Exception {
        long deadline = SystemClock.uptimeMillis() + 30_000;
        while ((Boolean) field("logImportLoading") && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(30);
        check(!(Boolean) field("logImportLoading"), "CSV import did not finish");
        waitForIdleSync();
    }

    private void waitForServiceIdle(ReadOnlyLoggingService service) {
        long deadline = SystemClock.uptimeMillis() + 10_000;
        while (SystemClock.uptimeMillis() < deadline) {
            boolean[] busy = {true}; runOnMainSync(() -> busy[0] = service.busy());
            if (!busy[0]) return;
            SystemClock.sleep(25);
        }
        throw new AssertionError("Service cleanup did not finish");
    }

    private void waitForSamples(ServiceFixture fixture, int count) {
        long deadline = SystemClock.uptimeMillis() + 10_000;
        while (fixture.recording.snapshot().samples() < count && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(25);
        check(fixture.recording.snapshot().samples() >= count,
                "Service did not record: " + fixture.recording.snapshot().message() + " / " + fixture.service.failure());
    }

    private void shell(String command) throws IOException {
        try (InputStream output = new android.os.ParcelFileDescriptor.AutoCloseInputStream(getUiAutomation().executeShellCommand(command))) {
            byte[] bytes = new byte[1024]; while (output.read(bytes) >= 0) { }
        }
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
        runOnMainSync(() -> invokeUnchecked(name, types, args));
    }
    private void invokeUnchecked(String name, Class<?>[] types, Object... args) {
        try {
            Method method = MainActivity.class.getDeclaredMethod(name, types);
            method.setAccessible(true);
            method.invoke(activity, args);
        } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
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
