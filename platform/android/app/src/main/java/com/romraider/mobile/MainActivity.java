/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.mobile;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.romraider.portable.PortableLogCsvReader;
import com.romraider.portable.PortableLogSample;
import com.romraider.portable.PortableLogSession;
import com.romraider.portable.PortableRomDocument;
import com.romraider.mobile.usb.OpenPortUsbTransport;
import com.romraider.mobile.logger.ReadOnlyLoggerSession;
import com.romraider.portable.logger.definition.PortableLoggerDefinition;
import com.romraider.portable.logger.definition.PortableLoggerDefinitionReader;
import com.romraider.portable.logger.definition.PortableLoggerProfile;
import com.romraider.portable.logger.definition.PortableLoggerProfileReader;
import com.romraider.portable.logger.definition.PortableLoggerSelection;
import com.romraider.portable.logger.definition.PortableLoggerSelectionService;
import com.romraider.portable.logger.definition.PortableSelectedParameter;
import com.romraider.portable.logger.PortableLoggerQueryBatch;
import com.romraider.portable.logger.PortableLoggerQueryPlan;
import com.romraider.portable.logger.PortableLoggerValue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Early portable client: offline editing and log review, with no ECU writes. */
public final class MainActivity extends Activity {
    private static final int OPEN_ROM = 10;
    private static final int SAVE_ROM = 11;
    private static final int OPEN_LOG = 12;
    private static final int OPEN_LOGGER_DEFINITION = 13;
    private static final int OPEN_LOGGER_PROFILE = 14;
    private static final int SAVE_PREVIEW_LOG = 15;
    private static final int SAVE_LIVE_LOG = 16;
    private static final String ACTION_USB_PERMISSION =
            "com.romraider.mobile.USB_PERMISSION";
    private static final int INK = Color.rgb(229, 239, 246);
    private static final int MUTED = Color.rgb(147, 169, 184);
    private static final int PANEL = Color.rgb(18, 39, 54);
    private static final int ACCENT = Color.rgb(0, 167, 196);

    private LinearLayout content;
    private Button loggerTab;
    private Button editorTab;
    private PortableRomDocument rom;
    private TextView romSummary;
    private TextView hexPreview;
    private EditText offsetInput;
    private EditText bytesInput;
    private TextView loggerSetupView;
    private TextView loggerPreviewView;
    private Button loggerPreviewButton;
    private TextView usbStatusView;
    private TextView liveLoggerView;
    private Button liveLoggerButton;
    private final Handler previewHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService workerExecutor = Executors.newSingleThreadExecutor();
    private volatile OpenPortUsbTransport openPort;
    private volatile ReadOnlyLoggerSession liveLogger;
    private volatile PortableLogSession liveLog;
    private boolean loggerVisible;
    private volatile String usbState = "OpenPort not prepared.";
    private volatile PortableLoggerDefinition loggerDefinition;
    private volatile PortableLoggerProfile loggerProfile;
    private String loggerDefinitionName = "";
    private String loggerProfileName = "";
    private volatile String loggerSetupState =
            "Open a logger definition and, optionally, an existing profile.";
    private PortableLoggerQueryPlan previewPlan;
    private List<PortableSelectedParameter> previewSelections =
            java.util.Collections.emptyList();
    private PortableLogSession previewSession;
    private boolean previewRunning;
    private int previewCycle;
    private long previewStartedAt;
    private final Runnable previewTick = new Runnable() {
        @Override
        public void run() {
            if (!previewRunning) return;
            try {
                List<PortableLoggerValue> values = previewPlan.decode(
                        simulatedResponses(previewPlan, previewSelections,
                                previewCycle));
                long timestamp = SystemClock.elapsedRealtime() - previewStartedAt;
                for (PortableLoggerValue value : values) {
                    PortableSelectedParameter selected = value.getSelection();
                    previewSession.append(new PortableLogSample(timestamp,
                            selected.getParameter().getId(),
                            selected.getParameter().getName(), value.getValue(),
                            selected.getConversion().getUnits()));
                }
                previewCycle++;
                showPreviewValues(values, timestamp);
                previewHandler.postDelayed(this, 250);
            } catch (RuntimeException ex) {
                stopLoggerPreview(ex.getMessage() == null
                        ? "Offline logger preview stopped." : ex.getMessage());
            }
        }
    };
    private final BroadcastReceiver usbPermissionReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (!ACTION_USB_PERMISSION.equals(intent.getAction())) return;
                    UsbDevice device = Build.VERSION.SDK_INT >= 33
                            ? intent.getParcelableExtra(UsbManager.EXTRA_DEVICE,
                                    UsbDevice.class)
                            : intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(
                            UsbManager.EXTRA_PERMISSION_GRANTED, false)
                            && device != null) {
                        openOpenPort(device);
                    } else {
                        usbState = "OpenPort USB permission was not granted.";
                        refreshUsbStatus();
                    }
                }
            };

    @Override
    @SuppressLint("InlinedApi") // The receiver flag is inlined and safe on API 26-32.
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        IntentFilter permissionFilter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(usbPermissionReceiver, permissionFilter,
                RECEIVER_NOT_EXPORTED);
        showWorkspace();
        showLogger();
    }

    @Override
    protected void onResume() {
        super.onResume();
        closeMissingOpenPort();
        refreshUsbStatus();
    }

    @Override
    protected void onDestroy() {
        stopLoggerPreview(null);
        stopLiveLogger(null);
        unregisterReceiver(usbPermissionReceiver);
        OpenPortUsbTransport transport = openPort;
        openPort = null;
        if (transport != null) workerExecutor.execute(transport::close);
        workerExecutor.shutdown();
        super.onDestroy();
    }

    @Override
    protected void onStop() {
        stopLiveLogger("Live logging stopped when RomRaider2 left the foreground.");
        super.onStop();
    }

    private void showWorkspace() {
        LinearLayout page = column();
        page.setPadding(dp(20), dp(18), dp(20), dp(12));
        page.setBackgroundColor(Color.rgb(10, 22, 33));

        TextView eyebrow = text("ROMRAIDER2  /  "
                + BuildConfig.VERSION_NAME.toUpperCase(Locale.ROOT), 12, ACCENT);
        eyebrow.setTypeface(Typeface.DEFAULT_BOLD);
        page.addView(eyebrow);
        TextView title = text("ECU Studio", 28, INK);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        page.addView(title, matchWrap());
        page.addView(text("Editing, log review, and USB checks for Android", 14, MUTED), matchWrap());

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(0, dp(18), 0, dp(12));
        loggerTab = button("LOGGER");
        editorTab = button("EDITOR");
        loggerTab.setOnClickListener(view -> showLogger());
        editorTab.setOnClickListener(view -> showEditor());
        tabs.addView(loggerTab, weighted());
        tabs.addView(editorTab, weighted());
        page.addView(tabs, matchWrap());

        ScrollView scroll = new ScrollView(this);
        content = column();
        scroll.addView(content, matchWrap());
        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        page.addView(text("ECU writing is unavailable in this preview.", 12, MUTED), matchWrap());
        setContentView(page);
    }

    private void showLogger() {
        stopLoggerPreview(null);
        stopLiveLogger(null);
        loggerVisible = true;
        loggerSetupView = null;
        loggerPreviewView = null;
        loggerPreviewButton = null;
        usbStatusView = null;
        liveLoggerView = null;
        liveLoggerButton = null;
        selectTab(loggerTab, editorTab);
        content.removeAllViews();
        content.addView(sectionTitle("LOG REVIEW"));
        content.addView(text("Open a RomRaider or RomRaider2 CSV and review the latest value for each channel.", 14, INK));
        Button open = button("OPEN CSV LOG");
        open.setOnClickListener(view -> openLog());
        content.addView(open, matchWrap(dp(12)));

        content.addView(sectionTitle("LOGGER SETUP"));
        content.addView(text("Use your existing RomRaider logger definition and profile. Extended addresses stay unavailable until the ECU ID matches.", 14, INK));
        Button definition = button("OPEN LOGGER DEFINITION");
        definition.setOnClickListener(view -> openLoggerDefinition());
        content.addView(definition, matchWrap(dp(6)));
        Button profile = button("OPEN LOGGER PROFILE");
        profile.setOnClickListener(view -> openLoggerProfile());
        content.addView(profile, matchWrap(dp(8)));
        showLoggerSetupStatus();

        content.addView(sectionTitle("OFFLINE LOGGER PREVIEW"));
        content.addView(text("Exercise the selected parameters, conversions, live display, and CSV recording with simulated data. No ECU connection is used.", 14, INK));
        loggerPreviewButton = button(getString(R.string.logger_preview_start));
        loggerPreviewButton.setOnClickListener(view -> toggleLoggerPreview());
        content.addView(loggerPreviewButton, matchWrap(dp(6)));
        Button savePreview = button("SAVE PREVIEW CSV");
        savePreview.setOnClickListener(view -> savePreviewLog());
        content.addView(savePreview, matchWrap(dp(8)));
        loggerPreviewView = text("Load a definition and profile to run the offline logger preview.", 13, MUTED);
        loggerPreviewView.setTypeface(Typeface.MONOSPACE);
        loggerPreviewView.setBackgroundColor(PANEL);
        loggerPreviewView.setPadding(dp(14), dp(14), dp(14), dp(14));
        content.addView(loggerPreviewView, matchWrap(dp(12)));

        content.addView(sectionTitle("USB DEVICES"));
        content.addView(text("Prepare an OpenPort 2.0 through Android USB. This checks the adapter and vehicle voltage without querying the ECU.", 14, INK));
        Button prepare = button("PREPARE OPENPORT");
        prepare.setOnClickListener(view -> prepareOpenPort());
        content.addView(prepare, matchWrap(dp(6)));
        Button scan = button("SCAN USB");
        scan.setOnClickListener(view -> showUsbDevices());
        content.addView(scan, matchWrap(dp(12)));
        showUsbDevices();

        content.addView(sectionTitle("READ-ONLY LIVE LOGGER"));
        content.addView(text("The live OpenPort K-Line path is wired end to end, but in-car qualification is deferred to RC5. It can identify the ECU, resolve exact profile addresses, display values, and record CSV. It cannot write to the ECU.", 14, INK));
        liveLoggerButton = button(getString(R.string.logger_live_start));
        liveLoggerButton.setOnClickListener(view -> toggleLiveLogger());
        content.addView(liveLoggerButton, matchWrap(dp(6)));
        Button saveLive = button("SAVE LIVE CSV");
        saveLive.setOnClickListener(view -> saveLiveLog());
        content.addView(saveLive, matchWrap(dp(8)));
        liveLoggerView = text("RC5 QUALIFICATION PENDING\nPrepare the OpenPort and load a definition and profile before a future connected test.", 13, MUTED);
        liveLoggerView.setTypeface(Typeface.MONOSPACE);
        liveLoggerView.setBackgroundColor(PANEL);
        liveLoggerView.setPadding(dp(14), dp(14), dp(14), dp(14));
        content.addView(liveLoggerView, matchWrap(dp(12)));
    }

    private void showUsbDevices() {
        if (usbStatusView != null) {
            usbStatusView.setText(usbSummary());
            return;
        }
        usbStatusView = text(usbSummary(), 13, MUTED);
        usbStatusView.setTypeface(Typeface.MONOSPACE);
        content.addView(usbStatusView, matchWrap(dp(8)));
    }

    private void showLoggerSetupStatus() {
        if (loggerSetupView != null) {
            loggerSetupView.setText(loggerSetupSummary());
            return;
        }
        loggerSetupView = text(loggerSetupSummary(), 13, MUTED);
        loggerSetupView.setTypeface(Typeface.MONOSPACE);
        content.addView(loggerSetupView, matchWrap(dp(8)));
    }

    private String loggerSetupSummary() {
        PortableLoggerDefinition definition = loggerDefinition;
        PortableLoggerProfile profile = loggerProfile;
        StringBuilder result = new StringBuilder(loggerSetupState);
        if (definition != null) {
            result.append("\nDefinition: ").append(loggerDefinitionName)
                    .append("  /  v").append(definition.getVersion())
                    .append("  /  ").append(definition.size()).append(" entries");
        }
        if (profile != null) {
            result.append("\nProfile: ").append(loggerProfileName)
                    .append("  /  ").append(profile.size()).append(" selected");
        }
        if (definition != null && profile != null) {
            try {
                PortableLoggerSelection selection =
                        PortableLoggerSelectionService.resolve(
                                definition, profile, null);
                result.append("\nReady before ECU ID: ")
                        .append(selection.ready().size())
                        .append("  /  waiting or unavailable: ")
                        .append(selection.unavailable().size());
                for (PortableSelectedParameter selected : selection.ready()) {
                    result.append("\n  READY  ")
                            .append(selected.getParameter().getName())
                            .append("  [")
                            .append(selected.getConversion().getUnits())
                            .append(']');
                }
                for (String unavailable : selection.unavailable()) {
                    result.append("\n  CHECK  ").append(unavailable);
                }
            } catch (RuntimeException ex) {
                result.append("\n").append(ex.getMessage());
            }
        }
        return result.toString();
    }

    private String usbSummary() {
        UsbManager manager = (UsbManager) getSystemService(USB_SERVICE);
        if (manager == null || manager.getDeviceList().isEmpty()) {
            return usbState + "\nNo USB devices found.";
        }
        StringBuilder result = new StringBuilder(usbState);
        for (UsbDevice device : manager.getDeviceList().values()) {
            result.append('\n');
            String name = device.getProductName();
            result.append(name == null ? device.getDeviceName() : name)
                    .append("  ")
                    .append(String.format(Locale.ROOT, "%04X:%04X",
                            device.getVendorId(), device.getProductId()))
                    .append(manager.hasPermission(device) ? "  PERMISSION GRANTED"
                            : "  PERMISSION NEEDED");
        }
        return result.toString();
    }

    private void prepareOpenPort() {
        UsbManager manager = (UsbManager) getSystemService(USB_SERVICE);
        UsbDevice device = findOpenPort(manager);
        if (manager == null || device == null) {
            usbState = "No OpenPort 2.0 found. Connect it through a USB-C adapter.";
            refreshUsbStatus();
            return;
        }
        if (!manager.hasPermission(device)) {
            usbState = "Waiting for OpenPort USB permission.";
            refreshUsbStatus();
            PendingIntent permission = PendingIntent.getBroadcast(this, 0,
                    new Intent(ACTION_USB_PERMISSION).setPackage(getPackageName()),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            manager.requestPermission(device, permission);
            return;
        }
        openOpenPort(device);
    }

    private void openOpenPort(UsbDevice device) {
        stopLiveLogger(null);
        usbState = "Preparing OpenPort 2.0...";
        refreshUsbStatus();
        workerExecutor.execute(() -> {
            OpenPortUsbTransport previous = openPort;
            if (previous != null) previous.close();
            try {
                UsbManager manager = (UsbManager) getSystemService(USB_SERVICE);
                OpenPortUsbTransport prepared = OpenPortUsbTransport.open(
                        manager, device);
                openPort = prepared;
                Integer voltage = prepared.getBatteryMillivolts();
                usbState = "OpenPort ready  /  firmware "
                        + prepared.getFirmwareVersion()
                        + (voltage == null ? "  /  vehicle voltage unavailable"
                        : String.format(Locale.ROOT, "  /  %.2f V",
                                voltage / 1000.0));
            } catch (Exception ex) {
                openPort = null;
                usbState = ex.getMessage() == null
                        ? "OpenPort preparation failed." : ex.getMessage();
            }
            runOnUiThread(this::refreshUsbStatus);
        });
    }

    private UsbDevice findOpenPort(UsbManager manager) {
        if (manager == null) return null;
        for (UsbDevice device : manager.getDeviceList().values()) {
            if (OpenPortUsbTransport.isOpenPort(device)) return device;
        }
        return null;
    }

    private void closeMissingOpenPort() {
        OpenPortUsbTransport transport = openPort;
        if (transport == null) return;
        UsbDevice attached = findOpenPort(
                (UsbManager) getSystemService(USB_SERVICE));
        if (transport.matches(attached)) return;
        openPort = null;
        usbState = "OpenPort disconnected.";
        stopLiveLogger("Live logging stopped because the OpenPort disconnected.");
        workerExecutor.execute(transport::close);
    }

    private void refreshUsbStatus() {
        if (loggerVisible && content != null) showUsbDevices();
    }

    private void refreshLoggerSetupStatus() {
        if (loggerVisible && content != null) showLoggerSetupStatus();
    }

    private void showEditor() {
        stopLoggerPreview(null);
        stopLiveLogger(null);
        loggerVisible = false;
        loggerSetupView = null;
        loggerPreviewView = null;
        loggerPreviewButton = null;
        usbStatusView = null;
        liveLoggerView = null;
        liveLoggerButton = null;
        selectTab(editorTab, loggerTab);
        content.removeAllViews();
        content.addView(sectionTitle("ROM EDITOR"));
        content.addView(text("Open a ROM, make a bounded hexadecimal change, and save a separate copy.", 14, INK));
        Button open = button("OPEN ROM");
        open.setOnClickListener(view -> openRom());
        content.addView(open, matchWrap(dp(12)));

        romSummary = text("No ROM open", 14, MUTED);
        content.addView(romSummary, matchWrap(dp(8)));
        hexPreview = text("", 12, INK);
        hexPreview.setTypeface(Typeface.MONOSPACE);
        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.addView(hexPreview);
        content.addView(horizontal, matchWrap(dp(8)));

        offsetInput = input("Offset, for example 1A20");
        bytesInput = input("Hex bytes, for example FF 00 7A");
        content.addView(offsetInput, matchWrap(dp(6)));
        content.addView(bytesInput, matchWrap(dp(6)));

        LinearLayout actions = new LinearLayout(this);
        Button apply = button("APPLY EDIT");
        Button reset = button("RESET");
        Button save = button("SAVE COPY");
        apply.setOnClickListener(view -> applyEdit());
        reset.setOnClickListener(view -> resetEdits());
        save.setOnClickListener(view -> saveRom());
        actions.addView(apply, weighted());
        actions.addView(reset, weighted());
        actions.addView(save, weighted());
        content.addView(actions, matchWrap(dp(12)));
    }

    private void openRom() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        startActivityForResult(intent, OPEN_ROM);
    }

    private void saveRom() {
        if (rom == null) {
            notice("Open a ROM first.");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, copyName(rom.getName()));
        startActivityForResult(intent, SAVE_ROM);
    }

    private void openLog() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");
        startActivityForResult(intent, OPEN_LOG);
    }

    private void openLoggerDefinition() {
        openXmlDocument(OPEN_LOGGER_DEFINITION);
    }

    private void openLoggerProfile() {
        openXmlDocument(OPEN_LOGGER_PROFILE);
    }

    private void openXmlDocument(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/xml");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[] {"application/xml", "text/xml", "text/plain"});
        startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (requestCode == OPEN_ROM) {
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    rom = PortableRomDocument.read(displayName(uri), input);
                }
                showEditor();
                refreshRom();
            } else if (requestCode == SAVE_ROM && rom != null) {
                try (OutputStream output = getContentResolver().openOutputStream(uri, "w")) {
                    rom.write(output);
                }
                rom.markSaved();
                refreshRom();
                notice("Saved a separate ROM copy.");
            } else if (requestCode == OPEN_LOG) {
                PortableLogSession session;
                try (InputStream input = getContentResolver().openInputStream(uri);
                     InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                    session = PortableLogCsvReader.read(reader);
                }
                showLogger();
                showLogSummary(displayName(uri), session);
            } else if (requestCode == OPEN_LOGGER_DEFINITION) {
                loadLoggerDefinition(uri, displayName(uri));
            } else if (requestCode == OPEN_LOGGER_PROFILE) {
                loadLoggerProfile(uri, displayName(uri));
            } else if (requestCode == SAVE_PREVIEW_LOG
                    && previewSession != null) {
                try (OutputStream output = getContentResolver()
                             .openOutputStream(uri, "w");
                     OutputStreamWriter writer = new OutputStreamWriter(
                             output, StandardCharsets.UTF_8)) {
                    previewSession.writeLongFormCsv(writer);
                }
                notice("Saved the offline preview log.");
            } else if (requestCode == SAVE_LIVE_LOG && liveLog != null) {
                try (OutputStream output = getContentResolver()
                             .openOutputStream(uri, "w");
                     OutputStreamWriter writer = new OutputStreamWriter(
                             output, StandardCharsets.UTF_8)) {
                    liveLog.writeLongFormCsv(writer);
                }
                notice("Saved the read-only live log.");
            }
        } catch (Exception ex) {
            notice(ex.getMessage() == null ? "The file could not be opened." : ex.getMessage());
        }
    }

    private void loadLoggerDefinition(Uri uri, String name) {
        stopLoggerPreview(null);
        stopLiveLogger(null);
        loggerSetupState = "Reading logger definition...";
        refreshLoggerSetupStatus();
        workerExecutor.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                PortableLoggerDefinition parsed =
                        PortableLoggerDefinitionReader.read(input, "SSM");
                loggerDefinition = parsed;
                loggerDefinitionName = name;
                loggerSetupState = "Logger definition loaded.";
            } catch (Exception ex) {
                loggerSetupState = ex.getMessage() == null
                        ? "Logger definition could not be opened." : ex.getMessage();
            }
            runOnUiThread(this::refreshLoggerSetupStatus);
        });
    }

    private void loadLoggerProfile(Uri uri, String name) {
        stopLoggerPreview(null);
        stopLiveLogger(null);
        loggerSetupState = "Reading logger profile...";
        refreshLoggerSetupStatus();
        workerExecutor.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                PortableLoggerProfile parsed = PortableLoggerProfileReader.read(input);
                loggerProfile = parsed;
                loggerProfileName = name;
                loggerSetupState = "Logger profile loaded.";
            } catch (Exception ex) {
                loggerSetupState = ex.getMessage() == null
                        ? "Logger profile could not be opened." : ex.getMessage();
            }
            runOnUiThread(this::refreshLoggerSetupStatus);
        });
    }

    private void showLogSummary(String name, PortableLogSession session) {
        Map<String, PortableLogSample> latest = new LinkedHashMap<>();
        for (PortableLogSample sample : session.snapshot()) latest.put(sample.getChannelId(), sample);
        StringBuilder summary = new StringBuilder(name).append("\n")
                .append(session.size()).append(" channel values  /  ")
                .append(latest.size()).append(" channels\n\n");
        for (PortableLogSample sample : latest.values()) {
            summary.append(sample.getChannelName()).append("   ")
                    .append(sample.getValue()).append(' ')
                    .append(sample.getUnits()).append('\n');
        }
        TextView card = text(summary.toString().trim(), 14, INK);
        card.setBackgroundColor(PANEL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        content.addView(card, 3, matchWrap(dp(12)));
    }

    private void toggleLoggerPreview() {
        if (previewRunning) {
            stopLoggerPreview("Offline preview stopped. The recorded values can be saved as CSV.");
            return;
        }
        PortableLoggerDefinition definition = loggerDefinition;
        PortableLoggerProfile profile = loggerProfile;
        if (definition == null || profile == null) {
            notice("Open a logger definition and profile first.");
            return;
        }
        try {
            PortableLoggerSelection selection = PortableLoggerSelectionService
                    .resolve(definition, profile, null);
            if (selection.ready().isEmpty()) {
                throw new IllegalArgumentException(
                        "No selected parameters are available before ECU identification.");
            }
            previewSelections = selection.ready();
            previewPlan = PortableLoggerQueryPlan.create(previewSelections);
            previewSession = new PortableLogSession();
            previewCycle = 0;
            previewStartedAt = SystemClock.elapsedRealtime();
            previewRunning = true;
            loggerPreviewButton.setText(R.string.logger_preview_stop);
            loggerPreviewView.setText("SIMULATED DATA\nStarting offline logger preview...");
            previewHandler.post(previewTick);
        } catch (RuntimeException ex) {
            notice(ex.getMessage() == null
                    ? "The offline logger preview could not start." : ex.getMessage());
        }
    }

    private void stopLoggerPreview(String message) {
        previewRunning = false;
        previewHandler.removeCallbacks(previewTick);
        if (loggerPreviewButton != null) {
            loggerPreviewButton.setText(R.string.logger_preview_start);
        }
        if (message != null && loggerPreviewView != null) {
            loggerPreviewView.setText(message);
        }
    }

    private void savePreviewLog() {
        if (previewSession == null || previewSession.size() == 0) {
            notice("Run the offline logger preview first.");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, "RomRaider2-android-preview.csv");
        startActivityForResult(intent, SAVE_PREVIEW_LOG);
    }

    private void toggleLiveLogger() {
        ReadOnlyLoggerSession current = liveLogger;
        if (current != null) {
            stopLiveLogger("Stopping after the current read...");
            return;
        }
        OpenPortUsbTransport transport = openPort;
        PortableLoggerDefinition definition = loggerDefinition;
        PortableLoggerProfile profile = loggerProfile;
        if (transport == null) {
            notice("Prepare an OpenPort 2.0 first.");
            return;
        }
        if (definition == null || profile == null) {
            notice("Open a logger definition and profile first.");
            return;
        }

        liveLog = null;
        if (liveLoggerButton != null) {
            liveLoggerButton.setText(R.string.logger_live_stop);
        }
        if (liveLoggerView != null) {
            liveLoggerView.setText("READ-ONLY LOGGER\nOpening SSM K-Line and identifying the ECU...");
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        ReadOnlyLoggerSession session = new ReadOnlyLoggerSession(transport,
                definition, profile, new ReadOnlyLoggerSession.Listener() {
                    @Override
                    public void onIdentified(String ecuId, int ready,
                            int unavailable) {
                        runOnUiThread(() -> {
                            if (liveLoggerView != null) {
                                liveLoggerView.setText(getString(
                                        R.string.logger_live_identified,
                                        ecuId, ready, unavailable));
                            }
                        });
                    }

                    @Override
                    public void onValues(String ecuId, long timestamp,
                            List<PortableLoggerValue> values, int samples) {
                        String display = liveValueSummary(ecuId, timestamp,
                                values, samples);
                        runOnUiThread(() -> {
                            if (liveLoggerView != null) {
                                liveLoggerView.setText(display);
                            }
                        });
                    }

                    @Override
                    public void onStopped(String message) {
                        liveLog = sessionLog();
                        liveLogger = null;
                        runOnUiThread(() -> {
                            getWindow().clearFlags(
                                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                            if (liveLoggerButton != null) {
                                liveLoggerButton.setText(
                                        R.string.logger_live_start);
                            }
                            if (liveLoggerView != null) {
                                int recorded = liveLog.size();
                                String recordedSummary = getResources()
                                        .getQuantityString(
                                                R.plurals.logger_live_recorded,
                                                recorded, recorded);
                                liveLoggerView.setText(getString(
                                        R.string.logger_live_stopped,
                                        message, recordedSummary));
                            }
                        });
                    }

                    private PortableLogSession sessionLog() {
                        ReadOnlyLoggerSession active = liveLogger;
                        return active == null
                                ? new PortableLogSession() : active.getLog();
                    }
                });
        liveLogger = session;
        workerExecutor.execute(session::run);
    }

    private void stopLiveLogger(String message) {
        ReadOnlyLoggerSession session = liveLogger;
        if (session == null) return;
        session.stop();
        if (message != null && liveLoggerView != null) {
            liveLoggerView.setText(message);
        }
    }

    private void saveLiveLog() {
        if (liveLogger != null) {
            notice("Stop the live logger and wait for the current read before saving.");
            return;
        }
        PortableLogSession session = liveLog;
        if (session == null || session.size() == 0) {
            notice("There is no live log to save yet.");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, "RomRaider2-android-live.csv");
        startActivityForResult(intent, SAVE_LIVE_LOG);
    }

    private static String liveValueSummary(String ecuId, long timestamp,
            List<PortableLoggerValue> values, int samples) {
        StringBuilder summary = new StringBuilder("READ-ONLY LIVE DATA  /  ECU ")
                .append(ecuId).append("\n")
                .append(timestamp).append(" ms  /  ")
                .append(samples).append(" values recorded");
        for (PortableLoggerValue value : values) {
            summary.append('\n')
                    .append(value.getSelection().getParameter().getName())
                    .append("   ")
                    .append(String.format(Locale.ROOT, "%.3f",
                            value.getValue()))
                    .append(' ')
                    .append(value.getSelection().getConversion().getUnits());
        }
        return summary.toString();
    }

    private void showPreviewValues(List<PortableLoggerValue> values,
            long timestamp) {
        if (loggerPreviewView == null) return;
        StringBuilder summary = new StringBuilder("SIMULATED DATA  /  ")
                .append(timestamp).append(" ms  /  ")
                .append(previewPlan.batches().size()).append(" SSM batch");
        if (previewPlan.batches().size() != 1) summary.append("es");
        for (PortableLoggerValue value : values) {
            summary.append("\n")
                    .append(value.getSelection().getParameter().getName())
                    .append("   ")
                    .append(String.format(Locale.ROOT, "%.3f",
                            value.getValue()))
                    .append(' ')
                    .append(value.getSelection().getConversion().getUnits());
        }
        loggerPreviewView.setText(summary.toString());
    }

    private static List<byte[]> simulatedResponses(
            PortableLoggerQueryPlan plan,
            List<PortableSelectedParameter> selections, int cycle) {
        Map<Integer, Byte> valuesByAddress = new HashMap<>();
        for (PortableSelectedParameter selection : selections) {
            byte[] raw = simulatedRawValue(selection, cycle);
            int[] addresses = selection.getAddresses();
            for (int index = 0; index < addresses.length; index++) {
                valuesByAddress.put(addresses[index], raw[index]);
            }
        }
        List<byte[]> result = new ArrayList<>();
        for (PortableLoggerQueryBatch batch : plan.batches()) {
            int[] addresses = batch.getAddresses();
            byte[] values = new byte[addresses.length];
            for (int index = 0; index < addresses.length; index++) {
                Byte value = valuesByAddress.get(addresses[index]);
                values[index] = value == null ? 0 : value;
            }
            result.add(values);
        }
        return result;
    }

    private static byte[] simulatedRawValue(
            PortableSelectedParameter selection, int cycle) {
        int length = selection.getAddresses().length;
        String storage = selection.getConversion().getStorageType();
        boolean little = "little".equalsIgnoreCase(
                selection.getConversion().getEndian());
        ByteBuffer buffer = ByteBuffer.allocate(length);
        if (little) buffer.order(ByteOrder.LITTLE_ENDIAN);
        if ("float".equalsIgnoreCase(storage)) {
            if (length != 4) {
                throw new IllegalArgumentException(
                        selection.getParameter().getId()
                        + ": floating-point value is not 4 bytes");
            }
            buffer.putFloat(25.0f + cycle * 0.25f);
        } else {
            long seed = Math.abs(selection.getParameter().getId().hashCode());
            if (length == 1) buffer.put((byte) (80 + (seed + cycle) % 120));
            else if (length == 2) buffer.putShort((short)
                    (1000 + (seed + cycle * 17) % 12000));
            else if (length == 4) buffer.putInt((int)
                    (100000 + (seed + cycle * 257) % 1000000));
            else throw new IllegalArgumentException(
                    selection.getParameter().getId()
                    + ": preview supports 1, 2, or 4-byte values");
        }
        return buffer.array();
    }

    private void applyEdit() {
        if (rom == null) {
            notice("Open a ROM first.");
            return;
        }
        try {
            int offset = Integer.parseInt(offsetInput.getText().toString().trim(), 16);
            String compact = bytesInput.getText().toString().replaceAll("[^0-9A-Fa-f]", "");
            if (compact.isEmpty() || (compact.length() & 1) != 0) {
                throw new IllegalArgumentException("Enter complete hexadecimal bytes.");
            }
            byte[] replacement = new byte[compact.length() / 2];
            for (int i = 0; i < replacement.length; i++) {
                replacement[i] = (byte) Integer.parseInt(compact.substring(i * 2, i * 2 + 2), 16);
            }
            rom.replace(offset, replacement);
            refreshRom();
        } catch (RuntimeException ex) {
            notice(ex.getMessage() == null ? "That edit is not valid." : ex.getMessage());
        }
    }

    private void resetEdits() {
        if (rom != null) {
            rom.reset();
            refreshRom();
        }
    }

    private void refreshRom() {
        if (romSummary == null || hexPreview == null || rom == null) return;
        int changedRanges = rom.changes().size();
        String changeSummary = rom.hasChanges()
                ? getResources().getQuantityString(R.plurals.rom_changed_ranges,
                        changedRanges, changedRanges)
                : getString(R.string.rom_unchanged);
        String sizeSummary = getResources().getQuantityString(
                R.plurals.rom_bytes, rom.size(), rom.size());
        romSummary.setText(getString(R.string.rom_summary,
                rom.getName(), sizeSummary, changeSummary));
        byte[] bytes = rom.snapshot();
        StringBuilder preview = new StringBuilder();
        for (int start = 0; start < Math.min(bytes.length, 256); start += 16) {
            preview.append(String.format(Locale.ROOT, "%06X  ", start));
            for (int index = start; index < Math.min(start + 16, bytes.length); index++) {
                preview.append(String.format(Locale.ROOT, "%02X ", bytes[index] & 0xFF));
            }
            preview.append('\n');
        }
        hexPreview.setText(preview.toString());
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        }
        return "document.bin";
    }

    private static String copyName(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) + "-edited" + name.substring(dot)
                : name + "-edited.bin";
    }

    private void selectTab(Button selected, Button other) {
        selected.setTextColor(Color.WHITE);
        selected.setBackgroundColor(ACCENT);
        other.setTextColor(MUTED);
        other.setBackgroundColor(PANEL);
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView sectionTitle(String value) {
        TextView title = text(value, 12, ACCENT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, dp(16), 0, dp(8));
        return title;
    }

    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.15f);
        return view;
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(12);
        button.setTextColor(INK);
        button.setBackgroundColor(PANEL);
        button.setMinHeight(dp(48));
        return button;
    }

    private EditText input(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setHintTextColor(MUTED);
        input.setTextColor(INK);
        input.setSingleLine(true);
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        input.setBackgroundColor(PANEL);
        return input;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return matchWrap(0);
    }

    private LinearLayout.LayoutParams matchWrap(int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, bottom);
        return params;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(dp(3), 0, dp(3), 0);
        return params;
    }

    private void notice(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
