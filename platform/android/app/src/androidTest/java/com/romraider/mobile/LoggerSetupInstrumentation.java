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
import com.romraider.portable.logger.definition.PortableLoggerDefinition;
import com.romraider.portable.logger.definition.PortableLoggerProfile;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.*;

/** Dependency-free framework instrumentation, restricted to the disposable automation app. */
public final class LoggerSetupInstrumentation extends Instrumentation {
    private String phase;
    private Activity activity;
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
            activity = startActivitySync(intent);
            awaitImports();
            if (phase.equals("seed")) seed();
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
                    activity = startActivitySync(new Intent().setClassName(getTargetContext(), MainActivity.class.getName())
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
        settle();
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
            boolean[] loading = {true};
            runOnMainSync(() -> loading[0] = ((LoggerImportState) fieldUnchecked("loggerImports")).isLoading());
            if (!loading[0]) { settle(); return; }
            SystemClock.sleep(50);
        }
        throw new AssertionError("Logger setup import timed out");
    }
    private void settle() throws Exception {
        waitForIdleSync();
        ((ExecutorService) field("workerExecutor")).submit(() -> { }).get(15, TimeUnit.SECONDS);
        waitForIdleSync();
        ((ExecutorService) field("LOGGER_SETUP_IO")).submit(() -> { }).get(15, TimeUnit.SECONDS);
        waitForIdleSync();
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
