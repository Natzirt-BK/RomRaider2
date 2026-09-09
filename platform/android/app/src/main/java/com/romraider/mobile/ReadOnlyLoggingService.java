/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.mobile;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import com.romraider.mobile.logger.ReadOnlyRecording;
import com.romraider.mobile.usb.OpenPortUsbTransport;
import com.romraider.portable.PortableLogSession;
import com.romraider.portable.logger.definition.PortableLoggerDefinition;
import com.romraider.portable.logger.definition.PortableLoggerProfile;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Local, non-sticky owner of an explicitly requested read-only USB recording. */
public final class ReadOnlyLoggingService extends Service {
    private static final String START = "com.romraider.mobile.START_RECORDING";
    private static final String STOP = "com.romraider.mobile.STOP_RECORDING";
    private static final String TOKEN = "recording_token";
    private static final String CHANNEL = "read-only-recording";
    private static final int NOTIFICATION = 24;
    private static final long WAKE_TIMEOUT_MS = 10 * 60_000L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService cleanup = Executors.newSingleThreadExecutor();
    private final LocalBinder binder = new LocalBinder();
    private ReadOnlyRecording recording;
    private Lease lease;
    private UsbDevice device;
    private String token = "";
    private String failure = "";
    private boolean pending;
    private boolean foreground;
    private boolean closingLease;
    private boolean destroyed;
    private long notificationAt;
    private long wakeAcquiredAt;
    private long recordingStartedAt;
    private PowerManager.WakeLock wakeLock;

    public final class LocalBinder extends Binder {
        ReadOnlyLoggingService service() { return ReadOnlyLoggingService.this; }
    }

    private final BroadcastReceiver detached = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            UsbDevice removed = Build.VERSION.SDK_INT >= 33
                    ? intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice.class)
                    : intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (device != null && removed != null && device.getDeviceId() == removed.getDeviceId()) stop();
        }
    };

    @Override @SuppressLint("InlinedApi")
    public void onCreate() {
        super.onCreate();
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(
                CHANNEL, "Vehicle logging", NotificationManager.IMPORTANCE_LOW));
        registerReceiver(detached, new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED), RECEIVER_NOT_EXPORTED);
    }

    @Override public IBinder onBind(Intent intent) { return binder; }

    ReadOnlyRecording recording() { return recording; }
    boolean busy() { return recording != null && (recording.snapshot().active()
            || pending || foreground || closingLease || !lease.released); }
    String failure() { return failure; }

    PortableLogSession recordingAwaitingSavePrompt() {
        requireMainThread();
        return busy() ? null : MobileCompletedLogs.INSTANCE.awaitingPrompt();
    }

    void acknowledgeSavePrompt(PortableLogSession completed) {
        requireMainThread();
        MobileCompletedLogs.INSTANCE.acknowledge(completed);
    }

    void prepareExport(PortableLogSession completed) {
        requireMainThread();
        if (busy()) throw new IllegalStateException("Wait for the recording to finish.");
        MobileCompletedLogs.INSTANCE.prepareExport(completed);
    }

    PortableLogSession takeExport() {
        requireMainThread();
        return MobileCompletedLogs.INSTANCE.takeExport();
    }

    /** True transfers transport ownership even if subsequent foreground promotion fails. */
    boolean start(OpenPortUsbTransport transport, UsbDevice selected,
            PortableLoggerDefinition definition, PortableLoggerProfile profile) {
        return start(transport, selected, definition, profile, false);
    }

    boolean start(OpenPortUsbTransport transport, UsbDevice selected,
            PortableLoggerDefinition definition, PortableLoggerProfile profile, boolean discoveryOnly) {
        requireMainThread();
        if (discoveryOnly && !"SSM".equalsIgnoreCase(definition.getProtocol())) return false;
        UsbManager manager = getSystemService(UsbManager.class);
        if (destroyed || busy() || transport == null || !transport.matches(selected)
                || manager == null || !manager.hasPermission(selected)
                || !manager.getDeviceList().containsKey(selected.getDeviceName())) return false;
        Lease transferred = new Lease(transport);
        File folder = new File(getFilesDir(), "recordings");
        ReadOnlyRecording.ResourceFactory factory = cancelled -> {
            if (discoveryOnly) return new ReadOnlyRecording.Resources(transport, new PortableLogSession(), transferred);
            if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("Recording folder is unavailable");
            PortableLogSession log = PortableLogSession.streaming(File.createTempFile(
                    "live-" + System.currentTimeMillis() + "-", ".csv.part", folder), 10_000);
            return new ReadOnlyRecording.Resources(transport, log, transferred);
        };
        com.romraider.portable.logger.dimemod.DimeModDiscovery.Mode mode =
                "SSM".equalsIgnoreCase(definition.getProtocol())
                ? discoveryOnly ? com.romraider.portable.logger.dimemod.DimeModDiscovery.Mode.DISCOVER_ONLY
                    : com.romraider.portable.logger.dimemod.DimeModDiscovery.Mode.DISCOVER_AND_LOG
                : com.romraider.portable.logger.dimemod.DimeModDiscovery.Mode.OFF;
        return accept(factory, transferred, selected, definition, profile, mode);
    }

    // Also exercised by isolated automation through reflection, with a fake resource factory.
    // No intent, binder command or exported API accepts a factory or bypasses the USB checks above.
    private boolean accept(ReadOnlyRecording.ResourceFactory factory, Lease transferred, UsbDevice selected,
            PortableLoggerDefinition definition, PortableLoggerProfile profile) {
        return accept(factory, transferred, selected, definition, profile,
                com.romraider.portable.logger.dimemod.DimeModDiscovery.Mode.OFF);
    }

    private boolean accept(ReadOnlyRecording.ResourceFactory factory, Lease transferred, UsbDevice selected,
            PortableLoggerDefinition definition, PortableLoggerProfile profile,
            com.romraider.portable.logger.dimemod.DimeModDiscovery.Mode mode) {
        requireMainThread();
        if (destroyed || busy()) return false;
        handler.removeCallbacks(monitor);
        ReadOnlyRecording next = new ReadOnlyRecording(factory, definition, profile, SystemClock::elapsedRealtimeNanos, mode);
        recording = next;
        lease = transferred;
        device = selected;
        token = UUID.randomUUID().toString();
        failure = "";
        pending = true;
        closingLease = false;
        try {
            startForegroundService(new Intent(this, ReadOnlyLoggingService.class).setAction(START).putExtra(TOKEN, token));
        } catch (RuntimeException denied) {
            failure = "Background recording could not start: " + detail(denied);
            pending = false;
            next.close();
            handler.post(monitor);
        }
        return true;
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String requested = intent == null ? null : intent.getStringExtra(TOKEN);
        if (intent != null && STOP.equals(intent.getAction()) && token.equals(requested)) stop();
        else if (intent != null && START.equals(intent.getAction()) && pending && token.equals(requested)
                && flags == 0 && recording.snapshot().phase() == ReadOnlyRecording.Phase.NEW) {
            pending = false; // Consume once, before promotion or any acquisition.
            try {
                Notification notification = notification();
                if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST);
                else startForeground(NOTIFICATION, notification);
                foreground = true;
                recordingStartedAt = SystemClock.elapsedRealtime();
                PowerManager power = getSystemService(PowerManager.class);
                wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RomRaider2:ReadOnlyRecording");
                wakeLock.setReferenceCounted(false);
                renewWakeLock();
                recording.start();
            } catch (RuntimeException denied) {
                failure = "Background recording could not start: " + detail(denied);
                recording.close();
            }
            handler.post(monitor);
        }
        // Unknown/replayed requests cannot start capture, including after process death.
        if (!foreground) {
            if (pending && intent != null && START.equals(intent.getAction()) && token.equals(requested)) {
                pending = false;
                recording.close();
                handler.post(monitor);
            }
            stopSelf(startId);
        }
        return START_NOT_STICKY;
    }

    void stop() {
        requireMainThread();
        if (recording == null || !busy()) return;
        pending = false;
        recording.stop();
        handler.removeCallbacks(monitor);
        handler.post(monitor);
    }

    private final Runnable monitor = new Runnable() {
        @Override public void run() {
            if (destroyed || recording == null) return;
            ReadOnlyRecording.Snapshot state = recording.snapshot();
            if (state.phase() == ReadOnlyRecording.Phase.STOPPED) {
                if (!lease.released) {
                    if (!closingLease) {
                        closingLease = true;
                        Lease closing = lease;
                        cleanup.execute(() -> {
                            try { closing.close(); }
                            catch (IOException problem) { handler.post(() -> {
                                if (!destroyed && lease == closing) failure += " USB release failed: " + detail(problem);
                            }); }
                            handler.post(this);
                        });
                    }
                    return;
                }
                closingLease = false;
                MobileCompletedLogs.INSTANCE.remember(recording.completedLog());
                finishForeground();
                stopSelf();
                return;
            }
            if (foreground) {
                long now = SystemClock.elapsedRealtime();
                try {
                    boolean progressing = state.phase() != ReadOnlyRecording.Phase.STOPPING
                            && (state.values().isEmpty() ? now - recordingStartedAt < 120_000
                            : SystemClock.elapsedRealtimeNanos() - state.receivedAtNanos() < 120_000_000_000L);
                    if (progressing && now - wakeAcquiredAt >= WAKE_TIMEOUT_MS / 2) renewWakeLock();
                    if (now - notificationAt >= 1000) {
                        getSystemService(NotificationManager.class).notify(NOTIFICATION, notification());
                        notificationAt = now;
                    }
                } catch (RuntimeException problem) {
                    failure = "Recording service could not maintain foreground execution: " + detail(problem);
                    recording.stop();
                }
            }
            handler.postDelayed(this, 100);
        }
    };

    private void renewWakeLock() {
        wakeLock.acquire(WAKE_TIMEOUT_MS);
        wakeAcquiredAt = SystemClock.elapsedRealtime();
    }

    private Notification notification() {
        String status = "Preparing vehicle logger";
        if (recording != null) {
            ReadOnlyRecording.Snapshot state = recording.snapshot();
            status = state.phase() == ReadOnlyRecording.Phase.STOPPING ? "Stopping and saving recording"
                    : state.phase() == ReadOnlyRecording.Phase.RECORDING
                    ? (SystemClock.elapsedRealtimeNanos() - state.receivedAtNanos() > 3_000_000_000L
                        ? "No recent ECU data" : "Read-only recording active")
                    : "Connecting and discovering channels";
        }
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 0,
                new Intent(this, ReadOnlyLoggingService.class).setAction(STOP)
                        .setData(Uri.parse("rr2-recording://stop/" + token)).putExtra(TOKEN, token),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("RomRaider2 logger").setContentText(status)
                .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .addAction(new Notification.Action.Builder(null, "Stop recording", stop).build()).build();
    }

    private void finishForeground() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        wakeLock = null;
        if (foreground) stopForeground(STOP_FOREGROUND_REMOVE);
        foreground = false;
    }

    @Override public void onDestroy() {
        destroyed = true;
        pending = false;
        handler.removeCallbacks(monitor);
        if (recording != null) recording.close();
        Lease remaining = lease;
        if (remaining != null) cleanup.execute(() -> {
            try { remaining.close(); }
            catch (IOException failure) { android.util.Log.w("RomRaider2", "USB release failed", failure); }
        });
        cleanup.shutdown();
        finishForeground();
        unregisterReceiver(detached);
        super.onDestroy();
    }

    private static void requireMainThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("Use the main thread for recording commands");
    }
    private static String detail(Exception failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }
    private static final class Lease implements Closeable {
        private final Closeable transport;
        volatile boolean released;
        Lease(Closeable transport) { this.transport = transport; }
        @Override public synchronized void close() throws IOException {
            if (released) return;
            try { transport.close(); }
            catch (RuntimeException failure) { throw new IOException(detail(failure), failure); }
            finally { released = true; }
        }
    }
}
