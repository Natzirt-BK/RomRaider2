/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.mobile;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.romraider.portable.PortableLogCsvReader;
import com.romraider.portable.PortableLogSample;
import com.romraider.portable.PortableLogSession;
import com.romraider.portable.PortableRomDocument;
import com.romraider.portable.editor.PortableEcuDefinition;
import com.romraider.portable.editor.PortableEcuDefinitionReader;
import com.romraider.portable.editor.PortableRomTable;
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
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Early portable client: offline editing and log review, with no ECU writes. */
public final class MainActivity extends Activity {
    private static final int OPEN_ROM = 10;
    private static final int SAVE_ROM = 11;
    private static final int OPEN_LOG = 12;
    private static final int OPEN_LOGGER_DEFINITION = 13;
    private static final int OPEN_LOGGER_PROFILE = 14;
    private static final int SAVE_PREVIEW_LOG = 15;
    private static final int SAVE_LIVE_LOG = 16;
    private static final int OPEN_ECU_DEFINITION = 17;
    private static final String ACTION_USB_PERMISSION =
            "com.romraider.mobile.USB_PERMISSION";
    private static final int BACKGROUND = Color.rgb(15, 21, 27);
    private static final int INK = Color.rgb(228, 232, 237);
    private static final int MUTED = Color.rgb(145, 160, 174);
    private static final int PANEL = Color.rgb(24, 33, 41);
    private static final int PANEL_RAISED = Color.rgb(31, 43, 53);
    private static final int BORDER = Color.rgb(52, 67, 80);
    private static final int ACCENT = Color.rgb(217, 38, 50);
    private static final int POSITIVE = Color.rgb(36, 120, 75);
    private static final String PREF_GAUGE_THEME = "logger_gauge_theme";
    private static final int MOBILE_GAUGE_LIMIT = 8;

    private LinearLayout content;
    private Button loggerTab;
    private Button editorTab;
    private PortableRomDocument rom;
    private TextView romSummary;
    private TextView hexPreview;
    private EditText offsetInput;
    private EditText bytesInput;
    private volatile PortableEcuDefinition ecuDefinition;
    private String ecuDefinitionName = "";
    private volatile String ecuDefinitionState =
            "Open a ROM, then load a RomRaider ECU definition.";
    private PortableRomTable selectedTable;
    private TextView ecuDefinitionSummary;
    private EditText tableSearch;
    private LinearLayout tableList;
    private LinearLayout tableDetail;
    private EditText tableRowInput;
    private EditText tableColumnInput;
    private EditText tableValueInput;
    private TextView loggerSetupView;
    private TextView loggerPreviewView;
    private Button loggerPreviewButton;
    private TextView usbStatusView;
    private TextView liveLoggerView;
    private Button liveLoggerButton;
    private GridLayout loggerGaugeGrid;
    private TextView loggerGaugeEmpty;
    private final Map<String, MobileGaugeView> loggerGaugeViews =
            new LinkedHashMap<>();
    private final Map<String, MobileGaugeSnapshot> loggerGaugeSnapshots =
            new LinkedHashMap<>();
    private final Map<MobileGaugeTheme, Button> loggerGaugeThemeButtons =
            new LinkedHashMap<>();
    private MobileGaugeTheme loggerGaugeTheme = MobileGaugeTheme.RR2_CLASSIC;
    private final Handler previewHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService workerExecutor = Executors.newSingleThreadExecutor();
    private final AtomicInteger recoveryGeneration = new AtomicInteger();
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
        restoreUnsavedWorkspace();
        previewHandler.post(() -> prepareAttachedOpenPort(getIntent()));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        prepareAttachedOpenPort(intent);
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
        scheduleWorkspaceRecovery();
        super.onStop();
    }

    private void showWorkspace() {
        LinearLayout page = column();
        page.setPadding(dp(20), dp(18), dp(20), dp(12));
        page.setBackgroundColor(BACKGROUND);

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.HORIZONTAL);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark = text("RR2", 14, Color.WHITE);
        mark.setTypeface(Typeface.DEFAULT_BOLD);
        mark.setGravity(Gravity.CENTER);
        mark.setPadding(dp(10), dp(7), dp(10), dp(7));
        mark.setBackground(rounded(ACCENT, ACCENT, 7));
        brand.addView(mark);
        LinearLayout brandText = column();
        brandText.setPadding(dp(10), 0, 0, 0);
        TextView title = text("ROMRAIDER2", 17, INK);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        brandText.addView(title);
        brandText.addView(text("ANDROID  /  "
                + BuildConfig.VERSION_NAME.toUpperCase(Locale.ROOT), 10, MUTED));
        brand.addView(brandText, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView safety = text("ECU READ ONLY", 10, Color.rgb(101, 211, 151));
        safety.setTypeface(Typeface.DEFAULT_BOLD);
        safety.setPadding(dp(9), dp(6), dp(9), dp(6));
        safety.setBackground(rounded(Color.rgb(18, 57, 42),
                Color.rgb(35, 108, 73), 20));
        brand.addView(safety);
        page.addView(brand, matchWrap(dp(14)));

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(0, 0, 0, dp(12));
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
        TextView footer = text("ECU writing is unavailable in this preview.",
                11, MUTED);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(9), 0, 0);
        page.addView(footer, matchWrap());
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
        loggerGaugeGrid = null;
        loggerGaugeEmpty = null;
        loggerGaugeViews.clear();
        loggerGaugeSnapshots.clear();
        loggerGaugeThemeButtons.clear();
        selectTab(loggerTab, editorTab);
        content.removeAllViews();

        TextView heading = text("Logger", 24, INK);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        content.addView(heading);
        content.addView(text("Review logs, prepare a session, and verify the "
                + "OpenPort from one workspace.", 13, MUTED), matchWrap(dp(14)));

        content.addView(loggerDashboardCard(), cardParams(dp(10)));

        LinearLayout reviewCard = sectionCard("LOG REVIEW",
                "Open a RomRaider or RomRaider2 CSV and review the latest "
                        + "value for each channel.");
        Button open = button("OPEN CSV LOG");
        open.setOnClickListener(view -> openLog());
        reviewCard.addView(open, matchWrap());
        content.addView(reviewCard, cardParams(dp(10)));

        LinearLayout setupCard = sectionCard("LOGGER SETUP",
                "Use an existing logger definition and profile. Extended "
                        + "addresses remain unavailable until the ECU ID matches.");
        Button definition = button("OPEN LOGGER DEFINITION");
        definition.setOnClickListener(view -> openLoggerDefinition());
        Button profile = button("OPEN LOGGER PROFILE");
        profile.setOnClickListener(view -> openLoggerProfile());
        setupCard.addView(actionRow(definition, profile), matchWrap(dp(9)));
        loggerSetupView = statusText(loggerSetupSummary());
        setupCard.addView(loggerSetupView, matchWrap());
        content.addView(setupCard, cardParams(dp(10)));

        LinearLayout previewCard = sectionCard("OFFLINE PREVIEW",
                "Exercise selected parameters, conversions, display, and CSV "
                        + "recording with simulated data. No ECU is used.");
        loggerPreviewButton = button(getString(R.string.logger_preview_start));
        styleButton(loggerPreviewButton, POSITIVE, POSITIVE);
        loggerPreviewButton.setOnClickListener(view -> toggleLoggerPreview());
        Button savePreview = button("SAVE PREVIEW CSV");
        savePreview.setOnClickListener(view -> savePreviewLog());
        previewCard.addView(actionRow(loggerPreviewButton, savePreview),
                matchWrap(dp(9)));
        loggerPreviewView = statusText(
                "Load a definition and profile to run the offline preview.");
        previewCard.addView(loggerPreviewView, matchWrap());
        content.addView(previewCard, cardParams(dp(10)));

        LinearLayout usbCard = sectionCard("OPENPORT USB",
                "Prepare an OpenPort 2.0 and check adapter access and vehicle "
                        + "voltage without querying the ECU.");
        Button prepare = button("PREPARE OPENPORT");
        styleButton(prepare, PANEL_RAISED, ACCENT);
        prepare.setOnClickListener(view -> prepareOpenPort());
        Button scan = button("SCAN USB");
        scan.setOnClickListener(view -> showUsbDevices());
        usbCard.addView(actionRow(prepare, scan), matchWrap(dp(9)));
        usbStatusView = statusText(usbSummary());
        usbCard.addView(usbStatusView, matchWrap());
        content.addView(usbCard, cardParams(dp(10)));

        LinearLayout liveCard = sectionCard("READ-ONLY LIVE LOGGER",
                "Identify the ECU, resolve profile addresses, display values, "
                        + "and record CSV. Connected qualification remains "
                        + "scheduled for RC5.");
        liveLoggerButton = button(getString(R.string.logger_live_start));
        styleButton(liveLoggerButton, POSITIVE, POSITIVE);
        liveLoggerButton.setOnClickListener(view -> toggleLiveLogger());
        Button saveLive = button("SAVE LIVE CSV");
        saveLive.setOnClickListener(view -> saveLiveLog());
        liveCard.addView(actionRow(liveLoggerButton, saveLive),
                matchWrap(dp(9)));
        liveLoggerView = statusText("RC5 QUALIFICATION PENDING\nPrepare the "
                + "OpenPort and load a definition and profile before a future "
                + "connected test.");
        liveCard.addView(liveLoggerView, matchWrap());
        content.addView(liveCard, cardParams(dp(12)));
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
            requestOpenPortPermission(manager, device);
            return;
        }
        openOpenPort(device);
    }

    private void prepareAttachedOpenPort(Intent intent) {
        if (intent == null || !UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(
                intent.getAction())) return;
        UsbDevice device = Build.VERSION.SDK_INT >= 33
                ? intent.getParcelableExtra(UsbManager.EXTRA_DEVICE,
                        UsbDevice.class)
                : intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        if (!OpenPortUsbTransport.isOpenPort(device)) return;
        if (!loggerVisible) showLogger();
        UsbManager manager = (UsbManager) getSystemService(USB_SERVICE);
        if (manager == null) return;
        if (manager.hasPermission(device)) {
            usbState = "OpenPort attached. Preparing the adapter without querying the ECU...";
            refreshUsbStatus();
            openOpenPort(device);
        } else {
            requestOpenPortPermission(manager, device);
        }
    }

    private void requestOpenPortPermission(UsbManager manager,
            UsbDevice device) {
        usbState = "OpenPort attached. Waiting for USB permission; the ECU "
                + "will not be queried.";
        refreshUsbStatus();
        PendingIntent permission = PendingIntent.getBroadcast(this, 0,
                new Intent(ACTION_USB_PERMISSION).setPackage(getPackageName()),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        manager.requestPermission(device, permission);
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
        TextView heading = text("ROM Editor", 24, INK);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        content.addView(heading);
        content.addView(text("Browse and edit named calibration tables while "
                + "keeping the original ROM untouched.", 13, MUTED),
                matchWrap(dp(14)));

        LinearLayout romCard = sectionCard("ROM AND DEFINITION",
                "Open a ROM and its matching RomRaider ECU definition.");
        Button open = button("OPEN ROM");
        open.setOnClickListener(view -> openRom());
        Button definition = button("OPEN ECU DEFINITION");
        definition.setOnClickListener(view -> openEcuDefinition());
        romCard.addView(actionRow(open, definition), matchWrap(dp(9)));

        romSummary = statusText("No ROM open");
        romCard.addView(romSummary, matchWrap(dp(7)));
        ecuDefinitionSummary = statusText(ecuDefinitionState);
        romCard.addView(ecuDefinitionSummary, matchWrap());
        content.addView(romCard, cardParams(dp(10)));

        LinearLayout tablesCard = sectionCard("CALIBRATION TABLES",
                "Search by table name or category, then select a table to "
                        + "inspect and edit its scaled values.");
        tableSearch = input("Search table or category");
        tableSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start,
                    int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start,
                    int before, int count) { renderTableList(); }
            @Override public void afterTextChanged(Editable text) { }
        });
        tablesCard.addView(tableSearch, matchWrap(dp(8)));
        tableList = column();
        tablesCard.addView(tableList, matchWrap(dp(8)));
        tableDetail = column();
        tablesCard.addView(tableDetail, matchWrap());
        content.addView(tablesCard, cardParams(dp(10)));
        renderTableList();
        renderSelectedTable();

        LinearLayout hexCard = sectionCard("ADVANCED HEX EDITOR",
                "Direct byte editing remains available for definition work "
                        + "and comparison.");
        hexPreview = statusText("");
        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.addView(hexPreview);
        hexCard.addView(horizontal, matchWrap(dp(8)));

        offsetInput = input("Offset, for example 1A20");
        bytesInput = input("Hex bytes, for example FF 00 7A");
        hexCard.addView(offsetInput, matchWrap(dp(6)));
        hexCard.addView(bytesInput, matchWrap(dp(8)));

        LinearLayout actions = new LinearLayout(this);
        Button apply = button("APPLY EDIT");
        styleButton(apply, ACCENT, ACCENT);
        Button reset = button("RESET");
        Button save = button("SAVE COPY");
        styleButton(save, POSITIVE, POSITIVE);
        apply.setOnClickListener(view -> applyEdit());
        reset.setOnClickListener(view -> resetEdits());
        save.setOnClickListener(view -> saveRom());
        actions.addView(apply, weighted());
        actions.addView(reset, weighted());
        actions.addView(save, weighted());
        hexCard.addView(actions, matchWrap(dp(10)));
        TextView warning = text("CHECKSUM WARNING  /  Android does not correct ROM checksums. Save copies only for review and desktop validation. Do not flash Android-edited files.", 12, Color.rgb(255, 190, 92));
        warning.setBackground(rounded(Color.rgb(52, 39, 22),
                Color.rgb(116, 83, 34), 7));
        warning.setPadding(dp(12), dp(11), dp(12), dp(11));
        hexCard.addView(warning, matchWrap());
        content.addView(hexCard, cardParams(dp(12)));

        refreshRom();
    }

    private void openRom() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        startActivityForResult(intent, OPEN_ROM);
    }

    private void openEcuDefinition() {
        if (rom == null) {
            notice("Open a ROM first so its internal ID can be matched safely.");
            return;
        }
        openXmlDocument(OPEN_ECU_DEFINITION);
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
        if (requestCode == OPEN_ROM) {
            String name = displayName(uri);
            workerExecutor.execute(() -> {
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    PortableRomDocument opened = PortableRomDocument.read(name, input);
                    runOnUiThread(() -> {
                        rom = opened;
                        ecuDefinition = null;
                        ecuDefinitionName = "";
                        selectedTable = null;
                        ecuDefinitionState = "ROM opened. Load a matching "
                                + "RomRaider ECU definition.";
                        showEditor();
                        scheduleWorkspaceRecovery();
                    });
                } catch (Exception ex) {
                    fileFailure(ex, "The ROM could not be opened.");
                }
            });
        } else if (requestCode == SAVE_ROM && rom != null) {
            PortableRomDocument saving = rom;
            byte[] savedBytes = saving.snapshot();
            workerExecutor.execute(() -> {
                try (OutputStream output = getContentResolver()
                        .openOutputStream(uri, "w")) {
                    output.write(savedBytes);
                    boolean clean = saving.markSavedIfCurrent(savedBytes);
                    runOnUiThread(() -> {
                        if (rom == saving) refreshRom();
                        notice(clean ? "Saved a separate ROM copy."
                                : "ROM copy saved; newer edits remain unsaved.");
                        scheduleWorkspaceRecovery();
                    });
                } catch (Exception ex) {
                    fileFailure(ex, "The ROM copy could not be saved.");
                }
            });
        } else if (requestCode == OPEN_LOG) {
            String name = displayName(uri);
            workerExecutor.execute(() -> {
                try (InputStream input = getContentResolver().openInputStream(uri);
                     InputStreamReader reader = new InputStreamReader(input,
                             StandardCharsets.UTF_8)) {
                    PortableLogSession opened = PortableLogCsvReader.read(reader);
                    runOnUiThread(() -> {
                        showLogger();
                        showLogSummary(name, opened);
                    });
                } catch (Exception ex) {
                    fileFailure(ex, "The log could not be opened.");
                }
            });
        } else if (requestCode == OPEN_LOGGER_DEFINITION) {
            loadLoggerDefinition(uri, displayName(uri));
        } else if (requestCode == OPEN_LOGGER_PROFILE) {
            loadLoggerProfile(uri, displayName(uri));
        } else if (requestCode == OPEN_ECU_DEFINITION) {
            loadEcuDefinition(uri, displayName(uri));
        } else if (requestCode == SAVE_PREVIEW_LOG
                && previewSession != null) {
            saveLogAsync(uri, previewSession, "Saved the offline preview log.");
        } else if (requestCode == SAVE_LIVE_LOG && liveLog != null) {
            saveLogAsync(uri, liveLog, "Saved the read-only live log.");
        }
    }

    private void saveLogAsync(Uri uri, PortableLogSession session,
            String successMessage) {
        workerExecutor.execute(() -> {
            try (OutputStream output = getContentResolver()
                         .openOutputStream(uri, "w");
                 OutputStreamWriter writer = new OutputStreamWriter(
                         output, StandardCharsets.UTF_8)) {
                session.writeLongFormCsv(writer);
                runOnUiThread(() -> notice(successMessage));
            } catch (Exception ex) {
                fileFailure(ex, "The log could not be saved.");
            }
        });
    }

    private void fileFailure(Exception failure, String fallback) {
        String message = failure.getMessage() == null
                ? fallback : failure.getMessage();
        runOnUiThread(() -> notice(message));
    }

    private void loadEcuDefinition(Uri uri, String name) {
        PortableRomDocument targetRom = rom;
        if (targetRom == null) {
            notice("Open a ROM first.");
            return;
        }
        ecuDefinitionState = "Reading ECU definition and matching the ROM...";
        refreshEcuDefinitionStatus();
        workerExecutor.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                PortableEcuDefinition parsed = PortableEcuDefinitionReader.read(
                        input, targetRom);
                if (rom == targetRom) {
                    ecuDefinition = parsed;
                    ecuDefinitionName = name;
                    selectedTable = null;
                    ecuDefinitionState = "Exact ROM match. Definition-backed editing is ready.";
                }
            } catch (Exception ex) {
                if (rom == targetRom) {
                    ecuDefinition = null;
                    ecuDefinitionName = "";
                    selectedTable = null;
                    ecuDefinitionState = ex.getMessage() == null
                            ? "The ECU definition could not be opened."
                            : ex.getMessage();
                }
            }
            runOnUiThread(() -> {
                if (rom == targetRom) showEditor();
            });
        });
    }

    private void refreshEcuDefinitionStatus() {
        if (ecuDefinitionSummary == null) return;
        PortableEcuDefinition definition = ecuDefinition;
        StringBuilder summary = new StringBuilder(ecuDefinitionState);
        if (definition != null) {
            summary.append("\nDefinition: ").append(ecuDefinitionName)
                    .append("\nECU: ").append(definition.getXmlId())
                    .append("  /  ").append(definition.vehicleName())
                    .append("\nEditable numeric tables: ")
                    .append(definition.getTables().size());
        }
        ecuDefinitionSummary.setText(summary.toString());
    }

    private void renderTableList() {
        if (tableList == null) return;
        tableList.removeAllViews();
        PortableEcuDefinition definition = ecuDefinition;
        if (definition == null) {
            tableList.addView(text("No matched ECU definition loaded.", 13, MUTED),
                    matchWrap(dp(6)));
            return;
        }
        String query = tableSearch == null ? "" : tableSearch.getText()
                .toString().trim().toLowerCase(Locale.ROOT);
        int matches = 0;
        int shown = 0;
        for (PortableRomTable table : definition.getTables()) {
            String searchable = (table.getName() + " " + table.getCategory())
                    .toLowerCase(Locale.ROOT);
            if (!query.isEmpty() && !searchable.contains(query)) continue;
            matches++;
            if (shown >= 24) continue;
            Button item = button(table.getName() + "\n" + table.getCategory()
                    + "  /  " + table.getRows() + " × " + table.getColumns());
            item.setAllCaps(false);
            item.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            item.setTextColor(table == selectedTable ? Color.WHITE : ACCENT);
            item.setBackground(rounded(
                    table == selectedTable ? ACCENT : PANEL_RAISED,
                    table == selectedTable ? ACCENT : BORDER, 8));
            item.setOnClickListener(view -> {
                selectedTable = table;
                renderTableList();
                renderSelectedTable();
            });
            tableList.addView(item, matchWrap(dp(4)));
            shown++;
        }
        String result = matches == 0 ? "No tables match this search."
                : matches > shown ? "Showing " + shown + " of " + matches
                        + " matches. Refine the search to narrow the list."
                : matches + (matches == 1 ? " table" : " tables");
        tableList.addView(text(result, 12, MUTED), matchWrap(dp(6)));
    }

    private void renderSelectedTable() {
        if (tableDetail == null) return;
        tableDetail.removeAllViews();
        PortableRomTable table = selectedTable;
        if (table == null || rom == null) {
            tableDetail.addView(text("Choose a table to inspect its current values.",
                    13, MUTED), matchWrap(dp(6)));
            return;
        }
        TextView title = text(table.getName(), 20, ACCENT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        tableDetail.addView(title, matchWrap(dp(4)));
        tableDetail.addView(text(table.getCategory() + "  /  "
                + table.getRows() + " × " + table.getColumns() + "  /  "
                + table.getUnits() + "  /  ROM 0x"
                + String.format(Locale.ROOT, "%X", table.getAddress()),
                12, MUTED), matchWrap(dp(6)));
        if (table.getDescription() != null
                && !table.getDescription().trim().isEmpty()) {
            tableDetail.addView(text(table.getDescription().trim(), 13, INK),
                    matchWrap(dp(8)));
        }

        TextView values = text(tablePreview(table), 12, INK);
        values.setTypeface(Typeface.MONOSPACE);
        values.setBackground(rounded(BACKGROUND, BORDER, 7));
        values.setPadding(dp(14), dp(14), dp(14), dp(14));
        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.addView(values);
        tableDetail.addView(horizontal, matchWrap(dp(8)));

        tableRowInput = input("Row (1 to " + table.getRows() + ")");
        tableColumnInput = input("Column (1 to " + table.getColumns() + ")");
        tableValueInput = input("New value in " + table.getUnits());
        tableRowInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        tableColumnInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        tableValueInput.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);
        tableRowInput.setText("1");
        tableColumnInput.setText("1");
        tableValueInput.setText(table.formattedValueAt(rom, 0, 0));
        tableDetail.addView(tableRowInput, matchWrap(dp(5)));
        tableDetail.addView(tableColumnInput, matchWrap(dp(5)));
        tableDetail.addView(tableValueInput, matchWrap(dp(6)));
        LinearLayout actions = new LinearLayout(this);
        Button load = button("LOAD CELL");
        Button apply = button("APPLY VALUE");
        styleButton(apply, ACCENT, ACCENT);
        load.setOnClickListener(view -> loadTableCell());
        apply.setOnClickListener(view -> applyTableValue());
        actions.addView(load, weighted());
        actions.addView(apply, weighted());
        tableDetail.addView(actions, matchWrap(dp(8)));
    }

    private String tablePreview(PortableRomTable table) {
        int rows = Math.min(table.getRows(), 12);
        int columns = Math.min(table.getColumns(), 8);
        StringBuilder preview = new StringBuilder();
        preview.append("CURRENT VALUES  [").append(table.getUnits()).append("]\n");
        for (int row = 0; row < rows; row++) {
            preview.append(String.format(Locale.ROOT, "R%-3d", row + 1));
            for (int column = 0; column < columns; column++) {
                preview.append(String.format(Locale.ROOT, "%12s",
                        table.formattedValueAt(rom, row, column)));
            }
            preview.append('\n');
        }
        if (rows < table.getRows() || columns < table.getColumns()) {
            preview.append("Preview limited to ").append(rows).append(" rows × ")
                    .append(columns).append(" columns. Any cell can be edited below.\n");
        }
        return preview.toString().trim();
    }

    private int selectedCell(EditText input, int maximum, String label) {
        int value = Integer.parseInt(input.getText().toString().trim());
        if (value < 1 || value > maximum) {
            throw new IllegalArgumentException(label + " must be from 1 to " + maximum);
        }
        return value - 1;
    }

    private void loadTableCell() {
        PortableRomTable table = selectedTable;
        if (table == null || rom == null) return;
        try {
            int row = selectedCell(tableRowInput, table.getRows(), "Row");
            int column = selectedCell(tableColumnInput, table.getColumns(), "Column");
            tableValueInput.setText(table.formattedValueAt(rom, row, column));
        } catch (RuntimeException ex) {
            notice(ex.getMessage() == null ? "That table cell is not valid."
                    : ex.getMessage());
        }
    }

    private void applyTableValue() {
        PortableRomTable table = selectedTable;
        if (table == null || rom == null) return;
        try {
            int row = selectedCell(tableRowInput, table.getRows(), "Row");
            int column = selectedCell(tableColumnInput, table.getColumns(), "Column");
            double value = Double.parseDouble(
                    tableValueInput.getText().toString().trim());
            table.replaceValue(rom, row, column, value);
            refreshRom();
            renderSelectedTable();
            scheduleWorkspaceRecovery();
            notice("Table value applied to the working copy.");
        } catch (RuntimeException ex) {
            notice(ex.getMessage() == null ? "That table value is not valid."
                    : ex.getMessage());
        }
    }

    private void loadLoggerDefinition(Uri uri, String name) {
        stopLoggerPreview(null);
        stopLiveLogger(null);
        loggerDefinition = null;
        loggerDefinitionName = "";
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
        loggerProfile = null;
        loggerProfileName = "";
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
        LinearLayout card = sectionCard("OPEN LOG", name + "  /  "
                + session.size() + " values  /  " + latest.size()
                + " channels");
        TextView values = statusText(summary.toString().trim());
        card.addView(values, matchWrap());
        content.addView(card, 3, cardParams(dp(10)));
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

        PortableLogSession previousLog = liveLog;
        liveLog = null;
        if (previousLog != null) {
            try {
                previousLog.discard();
            } catch (Exception ignored) {
                // The cache directory will remove abandoned preview files.
            }
        }
        if (liveLoggerButton != null) {
            liveLoggerButton.setText(R.string.logger_live_stop);
        }
        if (liveLoggerView != null) {
            liveLoggerView.setText("READ-ONLY LOGGER\nOpening SSM K-Line and identifying the ECU...");
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        final PortableLogSession recording;
        try {
            recording = PortableLogSession.streaming(new File(getCacheDir(),
                    "romraider2-live-log.csv.part"), 10_000);
        } catch (Exception failure) {
            notice(failure.getMessage() == null
                    ? "Live-log storage could not be prepared."
                    : failure.getMessage());
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            return;
        }
        ReadOnlyLoggerSession session = new ReadOnlyLoggerSession(transport,
                definition, profile, recording,
                new ReadOnlyLoggerSession.Listener() {
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
                            updateLoggerGauges(values);
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
        updateLoggerGauges(values);
    }

    private LinearLayout loggerDashboardCard() {
        LinearLayout card = sectionCard("MOBILE DASHBOARD",
                "Glanceable fixed-scale gauges for simulated and read-only "
                        + "live data. Theme choice is saved on this device.");
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        loggerGaugeTheme = MobileGaugeTheme.fromName(preferences.getString(
                PREF_GAUGE_THEME, MobileGaugeTheme.RR2_CLASSIC.name()));

        HorizontalScrollView themeScroll = new HorizontalScrollView(this);
        themeScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout themes = new LinearLayout(this);
        themes.setOrientation(LinearLayout.HORIZONTAL);
        for (MobileGaugeTheme theme : MobileGaugeTheme.values()) {
            Button choice = button(theme.displayName);
            choice.setMinWidth(dp(126));
            choice.setContentDescription("Use " + theme.displayName
                    + " dashboard gauges");
            choice.setOnClickListener(view -> setLoggerGaugeTheme(theme));
            loggerGaugeThemeButtons.put(theme, choice);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, dp(7), 0);
            themes.addView(choice, params);
        }
        themeScroll.addView(themes, matchWrap());
        card.addView(themeScroll, matchWrap(dp(10)));

        Button demo = button("SHOW GAUGE DEMO");
        styleButton(demo, POSITIVE, POSITIVE);
        demo.setContentDescription("Show simulated values for visual review");
        demo.setOnClickListener(view -> showLoggerGaugeDemo());
        Button resetPeaks = button("RESET PEAKS");
        resetPeaks.setOnClickListener(view -> resetLoggerGaugePeaks());
        card.addView(actionRow(demo, resetPeaks), matchWrap(dp(10)));

        loggerGaugeEmpty = statusText("Run the offline preview or the "
                + "read-only logger to populate this dashboard, or show the "
                + "simulated demo for a visual check.");
        card.addView(loggerGaugeEmpty, matchWrap(dp(8)));
        loggerGaugeGrid = new GridLayout(this);
        loggerGaugeGrid.setColumnCount(2);
        loggerGaugeGrid.setAlignmentMode(GridLayout.ALIGN_MARGINS);
        loggerGaugeGrid.setUseDefaultMargins(false);
        card.addView(loggerGaugeGrid, matchWrap());
        styleLoggerGaugeThemeButtons();
        return card;
    }

    private void setLoggerGaugeTheme(MobileGaugeTheme theme) {
        loggerGaugeTheme = theme;
        getPreferences(MODE_PRIVATE).edit().putString(
                PREF_GAUGE_THEME, theme.name()).apply();
        for (MobileGaugeView gauge : loggerGaugeViews.values()) {
            gauge.setTheme(theme);
        }
        styleLoggerGaugeThemeButtons();
    }

    private void styleLoggerGaugeThemeButtons() {
        for (Map.Entry<MobileGaugeTheme, Button> entry
                : loggerGaugeThemeButtons.entrySet()) {
            boolean selected = entry.getKey() == loggerGaugeTheme;
            styleButton(entry.getValue(),
                    selected ? ACCENT : PANEL_RAISED,
                    selected ? ACCENT : BORDER);
            entry.getValue().setSelected(selected);
        }
    }

    private void updateLoggerGauges(List<PortableLoggerValue> values) {
        GridLayout grid = loggerGaugeGrid;
        if (grid == null || values == null) return;
        for (PortableLoggerValue value : values) {
            PortableSelectedParameter selection = value.getSelection();
            updateLoggerGauge(selection.getParameter().getId(),
                    selection.getParameter().getName(),
                    selection.getConversion().getUnits(),
                    selection.getConversion().getFormat(), value.getValue());
        }
    }

    private void updateLoggerGauge(String id, String name, String units,
            String format, double value) {
        GridLayout grid = loggerGaugeGrid;
        if (grid == null) return;
        MobileGaugeSnapshot snapshot = loggerGaugeSnapshots.get(id);
        if (snapshot == null) {
            if (loggerGaugeSnapshots.size() >= MOBILE_GAUGE_LIMIT) return;
            snapshot = new MobileGaugeSnapshot(id, name, units, format, value);
            loggerGaugeSnapshots.put(id, snapshot);
        } else {
            snapshot.accept(value);
        }
        MobileGaugeView gauge = loggerGaugeViews.get(id);
            if (gauge == null) {
                gauge = new MobileGaugeView(this);
                gauge.setTheme(loggerGaugeTheme);
                int index = loggerGaugeViews.size();
                GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                        GridLayout.spec(index / 2),
                        GridLayout.spec(index % 2, 1f));
                params.width = 0;
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                params.setMargins(dp(3), dp(3), dp(3), dp(3));
                grid.addView(gauge, params);
                loggerGaugeViews.put(id, gauge);
            }
        gauge.setValue(snapshot.id, snapshot.name,
                snapshot.displayValue(), snapshot.units, snapshot.value,
                snapshot.minimum, snapshot.maximum);
        if (loggerGaugeEmpty != null && !loggerGaugeViews.isEmpty()) {
            loggerGaugeEmpty.setVisibility(View.GONE);
        }
    }

    private void showLoggerGaugeDemo() {
        clearLoggerGauges();
        demoGauge("P-RPM", "Engine Speed", "rpm", "0", 720, 6650, 4210);
        demoGauge("P-BOOST", "Boost Pressure", "psi", "0.0", -8.6, 18.4, 12.7);
        demoGauge("P-COOLANT", "Coolant Temperature", "°F", "0", 154, 207, 196);
        demoGauge("P-AFR", "Air/Fuel Ratio", "AFR", "0.0", 10.9, 14.7, 12.1);
        demoGauge("P-VOLTAGE", "Battery Voltage", "V", "0.0", 11.8, 14.4, 13.9);
        demoGauge("P-THROTTLE", "Throttle Opening", "%", "0.0", 4, 100, 72);
        demoGauge("P-IGNITION", "Ignition Timing", "°", "0.0", -2, 36, 24);
        demoGauge("P-KNOCK", "Knock Correction", "°", "0.00", -4.2, 0, -1.4);
        if (loggerGaugeEmpty != null) {
            loggerGaugeEmpty.setText(R.string.logger_simulated_gauge_demo);
            loggerGaugeEmpty.setVisibility(View.VISIBLE);
        }
    }

    private void demoGauge(String id, String name, String units, String format,
            double minimum, double maximum, double current) {
        updateLoggerGauge(id, name, units, format, minimum);
        updateLoggerGauge(id, name, units, format, maximum);
        updateLoggerGauge(id, name, units, format, current);
    }

    private void resetLoggerGaugePeaks() {
        for (MobileGaugeSnapshot snapshot : loggerGaugeSnapshots.values()) {
            snapshot.resetPeaks();
            MobileGaugeView gauge = loggerGaugeViews.get(snapshot.id);
            if (gauge != null) {
                gauge.setValue(snapshot.id, snapshot.name,
                        snapshot.displayValue(), snapshot.units, snapshot.value,
                        snapshot.minimum, snapshot.maximum);
            }
        }
        if (loggerGaugeSnapshots.isEmpty()) {
            notice("There are no dashboard peaks to reset yet.");
        }
    }

    private void clearLoggerGauges() {
        loggerGaugeSnapshots.clear();
        loggerGaugeViews.clear();
        if (loggerGaugeGrid != null) loggerGaugeGrid.removeAllViews();
    }

    private static final class MobileGaugeSnapshot {
        private final String id;
        private final String name;
        private final String units;
        private final String format;
        private double value;
        private double minimum;
        private double maximum;

        private MobileGaugeSnapshot(String id, String name, String units,
                String format, double value) {
            this.id = id;
            this.name = name;
            this.units = units;
            this.format = format;
            this.value = value;
            minimum = value;
            maximum = value;
        }

        private void accept(double next) {
            value = next;
            minimum = Math.min(minimum, next);
            maximum = Math.max(maximum, next);
        }

        private void resetPeaks() {
            minimum = value;
            maximum = value;
        }

        private String displayValue() {
            try {
                DecimalFormat formatter = new DecimalFormat(format,
                        DecimalFormatSymbols.getInstance(Locale.ROOT));
                formatter.setGroupingUsed(false);
                return formatter.format(value);
            } catch (IllegalArgumentException exception) {
                return String.format(Locale.ROOT, "%.2f", value);
            }
        }
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
            renderSelectedTable();
            scheduleWorkspaceRecovery();
        } catch (RuntimeException ex) {
            notice(ex.getMessage() == null ? "That edit is not valid." : ex.getMessage());
        }
    }

    private void resetEdits() {
        if (rom != null) {
            rom.reset();
            refreshRom();
            renderSelectedTable();
            scheduleWorkspaceRecovery();
        }
    }

    private void restoreUnsavedWorkspace() {
        workerExecutor.execute(() -> {
            try {
                PortableRomDocument recovered =
                        MobileRomRecoveryStore.restore(getFilesDir());
                if (recovered == null) return;
                runOnUiThread(() -> {
                    if (rom != null) return;
                    rom = recovered;
                    ecuDefinition = null;
                    ecuDefinitionName = "";
                    selectedTable = null;
                    ecuDefinitionState = "Recovered unsaved ROM work. Load "
                            + "the matching ECU definition to continue editing.";
                    showEditor();
                    notice("Recovered unsaved ROM work from the previous session.");
                });
            } catch (Exception failure) {
                try {
                    MobileRomRecoveryStore.save(getFilesDir(), null);
                } catch (Exception ignored) {
                    // A later launch can retry app-private cache cleanup.
                }
                fileFailure(failure, "Unsaved ROM recovery could not be opened.");
            }
        });
    }

    private void scheduleWorkspaceRecovery() {
        PortableRomDocument document = rom;
        int generation = recoveryGeneration.incrementAndGet();
        workerExecutor.execute(() -> {
            if (generation != recoveryGeneration.get()) return;
            try {
                MobileRomRecoveryStore.save(getFilesDir(), document);
            } catch (Exception failure) {
                fileFailure(failure, "Unsaved ROM recovery could not be updated.");
            }
        });
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
        selected.setBackground(rounded(ACCENT, ACCENT, 8));
        other.setTextColor(MUTED);
        other.setBackground(rounded(PANEL, BORDER, 8));
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
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
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackground(rounded(PANEL_RAISED, BORDER, 8));
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
        input.setBackground(rounded(PANEL_RAISED, BORDER, 8));
        return input;
    }

    private LinearLayout sectionCard(String title, String detail) {
        LinearLayout card = column();
        card.setPadding(dp(14), dp(13), dp(14), dp(14));
        card.setBackground(rounded(PANEL, BORDER, 10));
        TextView heading = text(title, 12, ACCENT);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(heading, matchWrap(dp(5)));
        card.addView(text(detail, 13, INK), matchWrap(dp(11)));
        return card;
    }

    private TextView statusText(String value) {
        TextView status = text(value, 12, MUTED);
        status.setTypeface(Typeface.MONOSPACE);
        status.setPadding(dp(12), dp(11), dp(12), dp(11));
        status.setBackground(rounded(BACKGROUND, BORDER, 7));
        return status;
    }

    private LinearLayout actionRow(Button first, Button second) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(first, weighted());
        row.addView(second, weighted());
        return row;
    }

    private void styleButton(Button button, int fill, int stroke) {
        button.setTextColor(Color.WHITE);
        button.setBackground(rounded(fill, stroke, 8));
    }

    private GradientDrawable rounded(int fill, int stroke, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(radius));
        shape.setStroke(dp(1), stroke);
        return shape;
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

    private LinearLayout.LayoutParams cardParams(int bottom) {
        LinearLayout.LayoutParams params = matchWrap(bottom);
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
