/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.mobile;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.ComponentName;
import android.content.ServiceConnection;
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
import android.os.IBinder;
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
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.romraider.portable.PortableLogCsvReader;
import com.romraider.portable.PortableLogSample;
import com.romraider.portable.PortableLogSession;
import com.romraider.portable.PortableRecordingRecovery;
import com.romraider.portable.PortableRomDocument;
import com.romraider.portable.editor.PortableEcuDefinition;
import com.romraider.portable.editor.PortableEcuDefinitionReader;
import com.romraider.portable.editor.PortableRomTable;
import com.romraider.mobile.usb.OpenPortUsbTransport;
import com.romraider.mobile.logger.ReadOnlyRecording;
import com.romraider.mobile.logger.LoggerImportState;
import com.romraider.mobile.logger.LoggerSetupStore;
import com.romraider.portable.logger.definition.PortableLoggerSetup;
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
import com.romraider.portable.logger.PortableLoggerProtocol;
import com.romraider.portable.logger.definition.PortableLoggerParameter;
import com.romraider.portable.logger.definition.PortableMut2LogConfigReader;

import java.io.InputStream;
import java.io.IOException;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
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
    private static final int SAVE_ARCHIVED_LOG = 18;
    private static final int OPEN_PORTABLE_SETUP = 19;
    private static final int SAVE_PORTABLE_SETUP = 20;
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
    // Process-wide ordering keeps a closing Activity's save ahead of the next restore.
    private static final ExecutorService LOGGER_SETUP_IO = Executors.newSingleThreadExecutor();

    private LinearLayout content;
    private Button loggerTab;
    private Button editorTab;
    private Button gaugesTab;
    private ScrollView workspaceScroll;
    private LinearLayout workspacePage;
    private LinearLayout workspaceTabs;
    private LinearLayout workspaceBrand;
    private TextView workspaceFooter;
    private LinearLayout gaugesPage;
    private TextView gaugesStatus;
    private ViewGroup gaugeGridHome;
    private int gaugeGridHomeIndex;
    private boolean gaugesVisible;
    private boolean mountedFullScreen;
    private Button mountedModeButton;
    private Button mountedLayoutButton;
    private int mountedGaugeCount = 6;
    private FrameLayout mountedGaugeViewport;
    private ScrollView gaugesScroll;
    private AlertDialog mountedLayoutDialog;
    private int previousSystemUiVisibility;
    private int previousSystemBarsBehavior;
    private int previousVisibleSystemBars;
    private android.window.OnBackInvokedCallback mountedBackCallback;
    private boolean gaugeDemo;
    private boolean liveEcuIdentified;
    private final Map<String, Long> gaugeReceivedAt = new LinkedHashMap<>();
    private final Runnable gaugeMonitor = new Runnable() {
        @Override public void run() {
            refreshGaugeAvailability();
            previewHandler.postDelayed(this, 1000);
        }
    };
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
    private final Object usbLock = new Object();
    private int usbGeneration;
    private boolean activityDestroyed;
    private volatile PortableLogSession liveLog;
    private PortableLoggerProtocol loggerProtocol = PortableLoggerProtocol.SSM;
    private final LoggerImportState loggerImports = new LoggerImportState();
    private int loggerSetupRevision;
    private int setupTransferGeneration;
    private boolean setupTransferLoading;
    private byte[] setupExportBytes;
    private byte[] loggerDefinitionBytes = new byte[0];
    private ReadOnlyLoggingService recordingService;
    private boolean serviceBound;
    private boolean activityResumed;
    private ReadOnlyRecording displayedRecording;
    private ReadOnlyRecording.Snapshot displayedRecordingState;
    private boolean displayedRecordingBusy;
    private final ServiceConnection recordingConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            if (activityDestroyed) return;
            recordingService = ((ReadOnlyLoggingService.LocalBinder) binder).service();
            refreshRecording();
            if (!recordingService.busy()) prepareAttachedOpenPort(getIntent());
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            recordingService = null;
            liveEcuIdentified = false;
            if (liveLoggerView != null) liveLoggerView.setText(R.string.logger_service_unavailable);
            refreshGaugeAvailability();
        }
    };
    private final Runnable recordingTick = new Runnable() {
        @Override public void run() {
            if (!activityResumed || activityDestroyed) return;
            refreshRecording();
            previewHandler.postDelayed(this, 100);
        }
    };
    private File archiveToExport;
    private PortableRecordingRecovery.Prepared pendingArchiveRecovery;
    private AlertDialog archiveRecoveryDialog;
    private java.util.concurrent.Future<?> archivePreparation;
    private int archiveExportGeneration;
    private boolean archiveExportPending;
    private final java.util.concurrent.ThreadPoolExecutor logImportExecutor = new java.util.concurrent.ThreadPoolExecutor(
            1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS, new java.util.concurrent.ArrayBlockingQueue<>(1),
            task -> new Thread(task, "rr2-csv-import"));
    private java.util.concurrent.Future<?> logImportTask;
    private android.os.CancellationSignal logImportSignal;
    private int logImportGeneration;
    private boolean logImportLoading;
    private String logImportStatus = "No CSV imported. Imported data never becomes live gauge data.";
    private TextView logImportStatusView;
    private Button cancelLogImportButton;
    private PortableLogCsvReader.Summary importedLogSummary;
    private String importedLogName = "";
    private int importedLogPage;
    private View importedLogCard;
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
                        simulatedResponses(previewPlan, previewCycle));
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
                    if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(intent.getAction())) {
                        closeMissingOpenPort();
                        refreshUsbStatus();
                        return;
                    }
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
        mountedGaugeCount = Math.max(1, Math.min(6, getPreferences(MODE_PRIVATE).getInt("mounted_gauge_count", 6)));
        loggerProtocol = PortableLoggerProtocol.fromId(getPreferences(MODE_PRIVATE)
                .getString("logger_protocol", "SSM"));
        IntentFilter permissionFilter = new IntentFilter(ACTION_USB_PERMISSION);
        permissionFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(usbPermissionReceiver, permissionFilter,
                RECEIVER_NOT_EXPORTED);
        showWorkspace();
        showLogger();
        restoreLoggerSetup();
        restoreUnsavedWorkspace();
        serviceBound = bindService(new Intent(this, ReadOnlyLoggingService.class),
                recordingConnection, BIND_AUTO_CREATE);
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
        activityResumed = true;
        updateScreenAwake();
        if (mountedFullScreen) applyMountedSystemBars();
        closeMissingOpenPort();
        refreshUsbStatus();
        previewHandler.removeCallbacks(gaugeMonitor);
        previewHandler.post(gaugeMonitor);
        previewHandler.removeCallbacks(recordingTick);
        previewHandler.post(recordingTick);
    }

    @Override
    protected void onDestroy() {
        if (mountedLayoutDialog != null) { mountedLayoutDialog.dismiss(); mountedLayoutDialog = null; }
        setMountedFullScreen(false);
        cancelLogImport(null);
        logImportExecutor.shutdownNow();
        archiveExportGeneration++;
        if (archivePreparation != null) archivePreparation.cancel(true);
        if (archiveRecoveryDialog != null) {
            archiveRecoveryDialog.dismiss();
            archiveRecoveryDialog = null;
        }
        discardArchiveRecovery();
        setupTransferGeneration++;
        setupExportBytes = null;
        stopLoggerPreview(null);
        previewHandler.removeCallbacks(recordingTick);
        if (serviceBound) { unbindService(recordingConnection); serviceBound = false; }
        recordingService = null;
        unregisterReceiver(usbPermissionReceiver);
        final OpenPortUsbTransport transport;
        synchronized (usbLock) {
            activityDestroyed = true;
            usbGeneration++;
            transport = openPort;
            openPort = null;
        }
        if (transport != null) workerExecutor.execute(transport::close);
        workerExecutor.shutdown();
        super.onDestroy();
    }

    @Override
    protected void onStop() {
        activityResumed = false;
        updateScreenAwake();
        previewHandler.removeCallbacks(gaugeMonitor);
        previewHandler.removeCallbacks(recordingTick);
        stopLoggerPreview(null);
        scheduleWorkspaceRecovery();
        super.onStop();
    }

    private void showWorkspace() {
        LinearLayout page = column();
        workspacePage = page;
        page.setPadding(dp(20), dp(18), dp(20), dp(12));
        page.setBackgroundColor(BACKGROUND);
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            page.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(
                        (mountedFullScreen ? 0 : android.view.WindowInsets.Type.systemBars())
                                | android.view.WindowInsets.Type.displayCutout());
                view.setPadding(dp(mountedFullScreen ? 4 : 20) + bars.left,
                        dp(mountedFullScreen ? 0 : 18) + bars.top,
                        dp(mountedFullScreen ? 4 : 20) + bars.right,
                        dp(mountedFullScreen ? 0 : 12) + bars.bottom);
                return insets;
            });
        }

        LinearLayout brand = new LinearLayout(this);
        workspaceBrand = brand;
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
        workspaceTabs = tabs;
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(0, 0, 0, dp(12));
        loggerTab = button("LOGGER");
        editorTab = button("EDITOR");
        gaugesTab = button("GAUGES");
        loggerTab.setOnClickListener(view -> {
            if (gaugesVisible) leaveGaugesOnly();
            else if (!loggerVisible) showLogger();
        });
        editorTab.setOnClickListener(view -> showEditor());
        gaugesTab.setOnClickListener(view -> showGaugesOnly());
        tabs.addView(loggerTab, weighted());
        tabs.addView(gaugesTab, weighted());
        tabs.addView(editorTab, weighted());
        page.addView(tabs, matchWrap());

        ScrollView scroll = new ScrollView(this);
        workspaceScroll = scroll;
        content = column();
        scroll.addView(content, matchWrap());
        FrameLayout host = new FrameLayout(this);
        host.addView(scroll);
        gaugesPage = column();
        gaugesPage.setVisibility(View.GONE);
        host.addView(gaugesPage);
        page.addView(host, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        TextView footer = text("ECU writing unavailable · About / licenses",
                11, MUTED);
        workspaceFooter = footer;
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(9), 0, 0);
        footer.setMinHeight(dp(48));
        footer.setFocusable(true);
        footer.setContentDescription("About RomRaider2 and software licenses");
        footer.setOnClickListener(view -> showAbout());
        page.addView(footer, matchWrap());
        setContentView(page);
    }

    private void showAbout() {
        new AlertDialog.Builder(this).setTitle("RomRaider2 " + BuildConfig.VERSION_NAME)
                .setMessage("Independent community software. Not affiliated with Subaru, STI or Mitsubishi. "
                        + "Gauge markings are reference scales, not engine limits. ECU writing is unavailable.")
                .setPositiveButton("Software license", (dialog, which) -> showNotice("license.txt", "Software license"))
                .setNeutralButton("Brand notice", (dialog, which) -> showNotice("STI-wordmark-NOTICE.txt", "Brand notice"))
                .setNegativeButton("Close", null).show();
    }

    private void showNotice(String asset, String title) {
        try (InputStream input = getAssets().open("notices/" + asset)) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (bytes.size() + count > 65536) throw new IOException("Notice is too large");
                bytes.write(buffer, 0, count);
            }
            TextView noticeText = text(new String(bytes.toByteArray(), StandardCharsets.UTF_8), 14, INK);
            noticeText.setTextIsSelectable(true);
            noticeText.setPadding(dp(16), dp(12), dp(16), dp(12));
            ScrollView scroll = new ScrollView(this);
            scroll.addView(noticeText);
            new AlertDialog.Builder(this).setTitle(title).setView(scroll)
                    .setPositiveButton("Close", null).show();
        } catch (IOException failure) { notice("Unable to open bundled notice: " + failure.getMessage()); }
    }

    private void showLogger() {
        cancelLogImport(null);
        importedLogCard = null;
        if (gaugesVisible) leaveGaugesOnly();
        stopLoggerPreview(null);
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
        gaugeReceivedAt.clear();
        gaugeDemo = false;
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
        cancelLogImportButton = button("CANCEL CSV IMPORT");
        cancelLogImportButton.setOnClickListener(view -> cancelLogImport("CSV import cancelled; previous summary retained."));
        reviewCard.addView(actionRow(open, cancelLogImportButton), matchWrap());
        logImportStatusView = statusText(logImportStatus);
        reviewCard.addView(logImportStatusView, matchWrap());
        refreshLogImportStatus();
        content.addView(reviewCard, cardParams(dp(10)));

        LinearLayout setupCard = sectionCard("LOGGER SETUP",
                "Select the vehicle protocol, then load logger XML or a MUT2 "
                        + "OpenPort logcfg.txt. Choose channels here or import a profile.");
        Button protocolChoice = button("PROTOCOL: " + loggerProtocol);
        protocolChoice.setOnClickListener(view -> new AlertDialog.Builder(this)
                .setTitle("Read-only vehicle protocol")
                .setItems(new String[] {"Subaru SSM (4800 baud)", "Mitsubishi MUT-II (15625 baud)"},
                        (dialog, which) -> {
                            if (!loggerSetupEditable()) return;
                            PortableLoggerProtocol next = which == 0
                                    ? PortableLoggerProtocol.SSM : PortableLoggerProtocol.MUT2;
                            if (next == loggerProtocol) return;
                            stopLoggerPreview(null);
                            stopLiveLogger(null);
                            loggerSetupRevision++;
                            loggerImports.reset();
                            loggerProtocol = next;
                            loggerDefinition = null;
                            loggerDefinitionBytes = new byte[0];
                            loggerProfile = null;
                            loggerDefinitionName = "";
                            loggerProfileName = "";
                            loggerSetupState = "Load a " + next + " logger definition.";
                            getPreferences(MODE_PRIVATE).edit()
                                    .putString("logger_protocol", next.name()).apply();
                            scheduleLoggerSetupSave();
                            showLogger();
                        }).show());
        setupCard.addView(protocolChoice, matchWrap(dp(9)));
        Button definition = button("OPEN LOGGER DEFINITION");
        definition.setOnClickListener(view -> openLoggerDefinition());
        Button profile = button("OPEN LOGGER PROFILE");
        profile.setOnClickListener(view -> openLoggerProfile());
        setupCard.addView(actionRow(definition, profile), matchWrap(dp(9)));
        Button channels = button("CHOOSE CHANNELS");
        channels.setOnClickListener(view -> chooseLoggerChannels());
        setupCard.addView(channels, matchWrap(dp(9)));
        Button importSetup = button("IMPORT CHANNEL SETUP");
        importSetup.setOnClickListener(view -> openPortableLoggerSetup());
        Button exportSetup = button("EXPORT CHANNEL SETUP");
        exportSetup.setOnClickListener(view -> preparePortableLoggerSetupExport());
        setupCard.addView(actionRow(importSetup, exportSetup), matchWrap(dp(9)));
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
                "Load a logger definition, then choose channels or import a profile.");
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
                        + "and record CSV. Compatibility depends on the vehicle, "
                        + "protocol, definition, selected channels, and adapter.");
        liveLoggerButton = button(getString(R.string.logger_live_start));
        styleButton(liveLoggerButton, POSITIVE, POSITIVE);
        liveLoggerButton.setOnClickListener(view -> toggleLiveLogger());
        Button saveLive = button("SAVE LIVE CSV");
        saveLive.setOnClickListener(view -> saveLiveLog());
        liveCard.addView(actionRow(liveLoggerButton, saveLive),
                matchWrap(dp(9)));
        Button archive = button("RECOVER / EXPORT RECORDINGS");
        archive.setOnClickListener(view -> chooseArchivedLog());
        liveCard.addView(archive, matchWrap(dp(9)));
        liveLoggerView = statusText("READ-ONLY LOGGER\nPrepare the OpenPort and "
                + "load a matching definition and profile. Live logging reads the "
                + "vehicle; offline preview uses simulated values.");
        liveCard.addView(liveLoggerView, matchWrap());
        content.addView(liveCard, cardParams(dp(12)));
        displayedRecordingState = null;
        refreshRecording();
        showLogSummary();
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
        StringBuilder result = new StringBuilder("Protocol: ").append(loggerProtocol)
                .append('\n').append(loggerSetupState);
        if (loggerImports.isLoading()) result.append("\nImport still in progress; wait before starting logging.");
        if (loggerProtocol == PortableLoggerProtocol.MUT2) {
            result.append("\nMUT2_GENERIC confirms a response, not a calibration ID. "
                    + "Use definitions verified for your vehicle. All selected PIDs "
                    + "are polled each cycle; fewer channels means faster updates.");
        }
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
                if (selection.ready().stream().anyMatch(PortableSelectedParameter::isCalculated)) {
                    result.append("\nCalculated inputs use definition-default units or explicit [ID:units], independently of gauge display units. Hidden inputs add reads, not CSV columns.");
                }
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
        if (!loggerSetupEditable()) return;
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
        if (isLiveActive()) return;
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
        if (!loggerSetupEditable()) return;
        final int generation;
        final OpenPortUsbTransport previous;
        synchronized (usbLock) {
            if (activityDestroyed) return;
            generation = ++usbGeneration;
            previous = openPort;
            openPort = null;
        }
        usbState = "Preparing OpenPort 2.0...";
        refreshUsbStatus();
        workerExecutor.execute(() -> {
            if (previous != null) previous.close();
            try {
                UsbManager manager = (UsbManager) getSystemService(USB_SERVICE);
                OpenPortUsbTransport prepared = OpenPortUsbTransport.open(
                        manager, device);
                final boolean obsolete;
                synchronized (usbLock) {
                    obsolete = activityDestroyed || generation != usbGeneration;
                    if (!obsolete) openPort = prepared;
                }
                if (obsolete) {
                    prepared.close();
                    return;
                }
                Integer voltage = prepared.getBatteryMillivolts();
                usbState = "OpenPort ready  /  firmware "
                        + prepared.getFirmwareVersion()
                        + (voltage == null ? "  /  vehicle voltage unavailable"
                        : String.format(Locale.ROOT, "  /  %.2f V",
                                voltage / 1000.0));
            } catch (Exception ex) {
                synchronized (usbLock) {
                    if (activityDestroyed || generation != usbGeneration) return;
                    openPort = null;
                }
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
        synchronized (usbLock) {
            if (openPort != transport) return;
            usbGeneration++;
            openPort = null;
        }
        usbState = "OpenPort disconnected.";
        stopLiveLogger("Live logging stopped because the OpenPort disconnected.");
        workerExecutor.execute(transport::close);
    }

    private void refreshUsbStatus() {
        if (isDestroyed()) return;
        if (loggerVisible && content != null) showUsbDevices();
    }

    private void refreshLoggerSetupStatus() {
        if (isDestroyed()) return;
        if (loggerVisible && content != null) showLoggerSetupStatus();
    }

    private void showEditor() {
        if (isLiveActive() || previewRunning) {
            notice("Stop logging before opening the editor. LOGGER and GAUGES remain available.");
            return;
        }
        cancelLogImport(null);
        if (gaugesVisible) leaveGaugesOnly();
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
        if (!canReviewLog()) {
            notice("Stop logging and return to LOGGER before importing a CSV.");
            return;
        }
        cancelLogImport(null);
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");
        startActivityForResult(intent, OPEN_LOG);
    }

    private void openLoggerDefinition() {
        if (!loggerSetupEditable()) return;
        openXmlDocument(OPEN_LOGGER_DEFINITION);
    }

    private void openLoggerProfile() {
        if (!loggerSetupEditable()) return;
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
        if (requestCode == SAVE_PORTABLE_SETUP && (resultCode != RESULT_OK || data == null || data.getData() == null)) {
            setupExportBytes = null;
            return;
        }
        if (requestCode == SAVE_ARCHIVED_LOG && (resultCode != RESULT_OK || data == null || data.getData() == null)) {
            archiveToExport = null;
            return;
        }
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
                try {
                    boolean clean = MobileRomSave.save(saving, savedBytes,
                            () -> getContentResolver().openOutputStream(uri, "w"));
                    runOnUiThread(() -> {
                        if (activityDestroyed) return;
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
            loadLogSummary(uri);
        } else if (requestCode == OPEN_LOGGER_DEFINITION) {
            loadLoggerDefinition(uri, displayName(uri));
        } else if (requestCode == OPEN_PORTABLE_SETUP) {
            loadPortableLoggerSetup(uri);
        } else if (requestCode == SAVE_PORTABLE_SETUP) {
            savePortableLoggerSetup(uri);
        } else if (requestCode == OPEN_LOGGER_PROFILE) {
            loadLoggerProfile(uri, displayName(uri));
        } else if (requestCode == OPEN_ECU_DEFINITION) {
            loadEcuDefinition(uri, displayName(uri));
        } else if (requestCode == SAVE_PREVIEW_LOG
                && previewSession != null) {
            saveLogAsync(uri, previewSession, "Saved the offline preview log.");
        } else if (requestCode == SAVE_LIVE_LOG && liveLog != null) {
            saveLogAsync(uri, liveLog, "Saved the read-only live log.");
        } else if (requestCode == SAVE_ARCHIVED_LOG && archiveToExport != null) {
            File source = archiveToExport;
            archiveToExport = null;
            prepareArchivedExport(source, uri);
        }
    }

    private void prepareArchivedExport(File source, Uri destination) {
        if (archiveExportPending || isDestroyed()) return;
        archiveExportPending = true;
        int generation = ++archiveExportGeneration;
        notice("Validating the retained recording before export...");
        archivePreparation = workerExecutor.submit(() -> {
            try {
                PortableRecordingRecovery.Prepared prepared = PortableRecordingRecovery.prepare(source, getCacheDir());
                runOnUiThread(() -> {
                    if (isDestroyed() || generation != archiveExportGeneration) { closeArchiveRecovery(prepared); return; }
                    archivePreparation = null;
                    pendingArchiveRecovery = prepared;
                    if (prepared.omittedBytes() == 0) {
                        writePreparedArchive(destination, prepared, generation);
                        return;
                    }
                    archiveRecoveryDialog = new AlertDialog.Builder(this).setTitle("Review interrupted recording")
                            .setMessage(prepared.values() + " complete sample records can be exported. "
                                    + prepared.omittedBytes() + " bytes in the unfinished final record will be omitted.\n\n"
                                    + "The original recovery file will not change. The last cycle may have missing channels; "
                                    + "unwritten readings cannot be recovered. Export the validated data?")
                            .setPositiveButton("Export recovered data", (dialog, which) ->
                                    writePreparedArchive(destination, prepared, generation))
                            .setNegativeButton("Cancel", null)
                            .setOnDismissListener(dialog -> {
                                if (archiveRecoveryDialog == dialog) archiveRecoveryDialog = null;
                                if (pendingArchiveRecovery == prepared) {
                                    discardArchiveRecovery(); archiveExportPending = false;
                                }
                            }).show();
                });
            } catch (Exception failure) {
                runOnUiThread(() -> {
                    if (isDestroyed() || generation != archiveExportGeneration) return;
                    archivePreparation = null;
                    archiveExportPending = false;
                    notice("Recording was not exported: " + (failure.getMessage() == null
                            ? "Recovery validation failed." : failure.getMessage())
                            + " The original recovery file is unchanged.");
                });
            }
        });
    }

    private void writePreparedArchive(Uri destination, PortableRecordingRecovery.Prepared prepared, int generation) {
        if (isDestroyed() || generation != archiveExportGeneration || pendingArchiveRecovery != prepared) return;
        pendingArchiveRecovery = null; // Accepted export owns the immutable copy through destination close.
        workerExecutor.execute(() -> {
            try (PortableRecordingRecovery.Prepared recovery = prepared;
                 OutputStreamWriter writer = openCsvWriter(destination)) {
                recovery.writeTo(writer);
            } catch (Exception failure) {
                runOnUiThread(() -> {
                    if (isDestroyed() || generation != archiveExportGeneration) return;
                    archiveExportPending = false;
                    notice("Export did not finish; the destination may be incomplete. The original recovery file is unchanged.");
                });
                return;
            }
            runOnUiThread(() -> {
                if (isDestroyed() || generation != archiveExportGeneration) return;
                archiveExportPending = false;
                notice(prepared.omittedBytes() == 0 ? "Recording exported; the recovery copy is retained."
                        : "Recovered recording exported with the reviewed unfinished tail omitted. Original file retained.");
            });
        });
    }

    private void discardArchiveRecovery() {
        PortableRecordingRecovery.Prepared prepared = pendingArchiveRecovery;
        pendingArchiveRecovery = null;
        if (prepared != null) closeArchiveRecovery(prepared);
    }

    private static void closeArchiveRecovery(PortableRecordingRecovery.Prepared prepared) {
        // No writer owns this object here; close only unlinks its private temporary file.
        try { prepared.close(); }
        catch (java.io.IOException failure) { android.util.Log.w("RomRaider2", "Recovery temporary cleanup failed", failure); }
    }

    private void saveLogAsync(Uri uri, PortableLogSession session,
            String successMessage) {
        workerExecutor.execute(() -> {
            try {
                try (OutputStreamWriter writer = openCsvWriter(uri)) {
                    session.writeRomRaiderCsv(writer);
                }
                runOnUiThread(() -> notice(successMessage));
            } catch (Exception ex) {
                fileFailure(ex, "The log could not be saved.");
            }
        });
    }

    private OutputStreamWriter openCsvWriter(Uri uri) throws java.io.IOException {
        OutputStream output = getContentResolver().openOutputStream(uri, "w");
        if (output == null) throw new java.io.IOException("Export destination is unavailable");
        return new OutputStreamWriter(output, StandardCharsets.UTF_8);
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
        if (!loggerSetupEditable()) return;
        loggerSetupRevision++;
        stopLoggerPreview(null);
        stopLiveLogger(null);
        loggerDefinition = null;
        loggerDefinitionBytes = new byte[0];
        loggerDefinitionName = "";
        final int generation = loggerImports.beginDefinition();
        final PortableLoggerProtocol protocol = loggerProtocol;
        loggerSetupState = "Reading logger definition...";
        refreshLoggerSetupStatus();
        workerExecutor.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                byte[] bytes = LoggerSetupStore.readDefinition(input);
                PortableLoggerDefinition parsed = parseLoggerDefinition(bytes, protocol);
                runOnUiThread(() -> {
                    if (isDestroyed() || !loggerImports.finishDefinition(generation)) return;
                    loggerDefinition = parsed;
                    loggerDefinitionBytes = bytes;
                    loggerDefinitionName = name;
                    PortableLoggerProfile previous = loggerProfile;
                    loggerProfile = LoggerImportState.afterDefinition(previous, protocol.name());
                    if (previous != loggerProfile) loggerProfileName = "No channels selected";
                    loggerSetupState = previous == loggerProfile
                            ? "Logger definition loaded; channel selection retained."
                            : "Logger definition loaded. Import a profile or choose channels.";
                    scheduleLoggerSetupSave();
                    refreshLoggerSetupStatus();
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    if (isDestroyed() || !loggerImports.finishDefinition(generation)) return;
                    loggerSetupState = ex.getMessage() == null
                            ? "Logger definition could not be opened." : ex.getMessage();
                    scheduleLoggerSetupSave();
                    refreshLoggerSetupStatus();
                });
            }
        });
    }

    private void loadLoggerProfile(Uri uri, String name) {
        if (!loggerSetupEditable()) return;
        loggerSetupRevision++;
        stopLoggerPreview(null);
        stopLiveLogger(null);
        loggerProfile = null;
        loggerProfileName = "";
        final int generation = loggerImports.beginProfile();
        final PortableLoggerProtocol protocol = loggerProtocol;
        loggerSetupState = "Reading logger profile...";
        refreshLoggerSetupStatus();
        workerExecutor.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                PortableLoggerProfile parsed = PortableLoggerProfileReader.read(input);
                if (!parsed.getProtocol().isEmpty()
                        && PortableLoggerProtocol.fromId(parsed.getProtocol()) != protocol) {
                    throw new IllegalArgumentException("Profile protocol does not match " + protocol);
                }
                runOnUiThread(() -> {
                    if (isDestroyed() || !loggerImports.finishProfile(generation)) return;
                    loggerProfile = new PortableLoggerProfile(protocol.name(),
                            parsed.selections(), parsed.unsupported());
                    loggerProfileName = name;
                    loggerSetupState = "Logger profile loaded.";
                    scheduleLoggerSetupSave();
                    refreshLoggerSetupStatus();
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    if (isDestroyed() || !loggerImports.finishProfile(generation)) return;
                    loggerSetupState = ex.getMessage() == null
                            ? "Logger profile could not be opened." : ex.getMessage();
                    scheduleLoggerSetupSave();
                    refreshLoggerSetupStatus();
                });
            }
        });
    }

    private void chooseLoggerChannels() {
        if (!loggerSetupEditable()) return;
        if (loggerImportPending()) return;
        PortableLoggerDefinition definition = loggerDefinition;
        PortableLoggerProfile originalProfile = loggerProfile;
        if (definition == null) {
            notice("Load a logger definition first.");
            return;
        }
        List<PortableLoggerParameter> parameters = definition.parameters();
        String[] names = new String[parameters.size()];
        boolean[] checked = new boolean[names.length];
        Map<String, String> previousUnits = new HashMap<>();
        if (loggerProfile != null) for (PortableLoggerProfile.Selection selection : loggerProfile.selections()) {
            previousUnits.put(selection.getId(), selection.getUnits());
        }
        for (int index = 0; index < names.length; index++) {
            names[index] = parameters.get(index).getName();
            checked[index] = previousUnits.containsKey(parameters.get(index).getId());
        }
        new AlertDialog.Builder(this).setTitle("Channels (fewer = faster cycles)")
                .setMultiChoiceItems(names, checked, (dialog, which, selected) -> checked[which] = selected)
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Clear all", (dialog, which) -> {
                    if (!loggerSetupEditable()) return;
                    if (loggerImports.isLoading() || loggerDefinition != definition
                            || loggerProfile != originalProfile) return;
                    loggerSetupRevision++;
                    stopLoggerPreview(null);
                    stopLiveLogger(null);
                    loggerProfile = new PortableLoggerProfile(loggerProtocol.name(), Collections.emptyList(), Collections.emptyList());
                    loggerProfileName = "Custom channels";
                    scheduleLoggerSetupSave();
                    refreshLoggerSetupStatus();
                })
                .setPositiveButton("Use channels", (dialog, which) -> {
                    if (!loggerSetupEditable()) return;
                    if (loggerImports.isLoading() || loggerDefinition != definition
                            || loggerProfile != originalProfile) return;
                    loggerSetupRevision++;
                    stopLoggerPreview(null);
                    stopLiveLogger(null);
                    List<PortableLoggerProfile.Selection> selections = new ArrayList<>();
                    for (int index = 0; index < checked.length; index++) {
                        PortableLoggerParameter parameter = parameters.get(index);
                        if (checked[index] && !parameter.getConversions().isEmpty()) selections.add(
                                new PortableLoggerProfile.Selection(parameter.getId(),
                                        previousUnits.getOrDefault(parameter.getId(),
                                                parameter.getConversions().get(0).getUnits())));
                    }
                    loggerProfile = new PortableLoggerProfile(loggerProtocol.name(), selections, Collections.emptyList());
                    loggerProfileName = "Custom channels";
                    scheduleLoggerSetupSave();
                    refreshLoggerSetupStatus();
                }).show();
    }

    private boolean setupTransferAllowed() {
        if (isLiveActive() || previewRunning) {
            notice("Stop logging and wait for the recording to finish before transferring a setup.");
            return false;
        }
        if (loggerImportPending()) return false;
        if (loggerDefinition == null || loggerDefinitionBytes.length == 0) {
            notice("Load the matching logger definition first.");
            return false;
        }
        return true;
    }

    private void openPortableLoggerSetup() {
        if (!setupTransferAllowed()) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, OPEN_PORTABLE_SETUP);
    }

    private void loadPortableLoggerSetup(Uri uri) {
        if (!setupTransferAllowed()) return;
        final int generation = ++setupTransferGeneration;
        final int revision = loggerSetupRevision;
        final PortableLoggerDefinition definition = loggerDefinition;
        final byte[] definitionBytes = loggerDefinitionBytes;
        final PortableLoggerProtocol protocol = loggerProtocol;
        setupTransferLoading = true;
        workerExecutor.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                PortableLoggerSetup setup = PortableLoggerSetup.read(input);
                setup.validateAgainst(protocol, definitionBytes, definition);
                runOnUiThread(() -> {
                    if (isDestroyed() || generation != setupTransferGeneration) return;
                    setupTransferLoading = false;
                    if (!setupTransferCurrent(generation, revision, definition)) {
                        notice("Logger setup changed while importing. Select the setup file again.");
                        return;
                    }
                    StringBuilder review = new StringBuilder("Replace the selected channels with these ordered choices?\n\nProtocol: ")
                            .append(protocol.name()).append("\nDefinition: exact SHA-256 match\nChannels: ")
                            .append(setup.profile().selections().size()).append("\n");
                    for (PortableLoggerProfile.Selection choice : setup.profile().selections())
                        review.append('\n').append(choice.getId()).append(" — ").append(choice.getUnits());
                    review.append("\n\nNo connection will start. ECU-specific addresses and calculated inputs still require validation when logging starts. Gauge appearance is unchanged.");
                    new AlertDialog.Builder(this).setTitle("Review channel setup")
                            .setMessage(review.toString()).setNegativeButton("Cancel", null)
                            .setPositiveButton("Use setup", (dialog, which) -> {
                                if (!setupTransferCurrent(generation, revision, definition)) {
                                    notice("Logger setup changed. Import the file again.");
                                    return;
                                }
                                loggerSetupRevision++;
                                loggerProfile = setup.profile();
                                loggerProfileName = "Imported channel setup";
                                loggerSetupState = "Channel setup imported. Logging has not started.";
                                clearLoggerGauges();
                                scheduleLoggerSetupSave();
                                refreshLoggerSetupStatus();
                            }).show();
                });
            } catch (Exception failure) {
                runOnUiThread(() -> {
                    if (isDestroyed() || generation != setupTransferGeneration) return;
                    setupTransferLoading = false;
                    notice("Channel setup was not imported: " + transferFailure(failure));
                });
            }
        });
    }

    private boolean setupTransferCurrent(int generation, int revision, PortableLoggerDefinition definition) {
        return !isDestroyed() && generation == setupTransferGeneration && revision == loggerSetupRevision
                && definition == loggerDefinition && !loggerImports.isLoading()
                && !isLiveActive() && !previewRunning;
    }

    private void preparePortableLoggerSetupExport() {
        if (!setupTransferAllowed()) return;
        final int generation = ++setupTransferGeneration;
        final int revision = loggerSetupRevision;
        final PortableLoggerDefinition definition = loggerDefinition;
        final byte[] definitionBytes = loggerDefinitionBytes;
        final PortableLoggerProtocol protocol = loggerProtocol;
        final PortableLoggerProfile profile = loggerProfile;
        setupTransferLoading = true;
        workerExecutor.execute(() -> {
            try {
                byte[] bytes = PortableLoggerSetup.capture(protocol, definitionBytes, definition, profile).encode();
                runOnUiThread(() -> {
                    if (isDestroyed() || generation != setupTransferGeneration) return;
                    setupTransferLoading = false;
                    if (!setupTransferCurrent(generation, revision, definition)) {
                        notice("Logger setup changed. Export again.");
                        return;
                    }
                    new AlertDialog.Builder(this).setTitle("Export channel setup")
                            .setMessage("Exports the protocol, ordered channel IDs and units, and a fingerprint of the loaded definition. The receiving app must load that exact definition. No ROM, definition contents, recordings, private paths, gauge theme or connection state are included. Choose a new file; document providers may not support atomic replacement.")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Choose file", (dialog, which) -> {
                                if (!setupTransferCurrent(generation, revision, definition)) {
                                    notice("Logger setup changed. Export again.");
                                    return;
                                }
                                setupExportBytes = bytes;
                                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                                intent.addCategory(Intent.CATEGORY_OPENABLE);
                                intent.setType("application/octet-stream");
                                intent.putExtra(Intent.EXTRA_TITLE, "RomRaider2-channels.rr2logger");
                                startActivityForResult(intent, SAVE_PORTABLE_SETUP);
                            }).show();
                });
            } catch (Exception failure) {
                runOnUiThread(() -> {
                    if (isDestroyed() || generation != setupTransferGeneration) return;
                    setupTransferLoading = false;
                    notice("Channel setup was not exported: " + transferFailure(failure));
                });
            }
        });
    }

    private void savePortableLoggerSetup(Uri uri) {
        final byte[] bytes = setupExportBytes;
        setupExportBytes = null;
        if (bytes == null) {
            notice("The export snapshot expired. Start Export channel setup again.");
            return;
        }
        workerExecutor.execute(() -> {
            try (java.io.OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                if (output == null) throw new java.io.IOException("Destination is unavailable");
                output.write(bytes);
                output.flush();
            } catch (Exception failure) {
                runOnUiThread(() -> {
                    if (!isDestroyed()) notice("Setup export failed; the destination may be incomplete. Choose a new file and retry.");
                });
                return;
            }
            runOnUiThread(() -> { if (!isDestroyed()) notice("The reviewed channel setup was exported."); });
        });
    }

    private static String transferFailure(Exception failure) {
        String message = failure.getMessage();
        return message == null ? "The file is unavailable or invalid." : message.substring(0, Math.min(240, message.length()));
    }

    private static PortableLoggerDefinition parseLoggerDefinition(byte[] bytes,
            PortableLoggerProtocol protocol) throws Exception {
        java.io.ByteArrayInputStream input = new java.io.ByteArrayInputStream(bytes);
        input.mark(bytes.length);
        int first;
        do { first = input.read(); } while (first >= 0 && Character.isWhitespace(first));
        if (first == 0xEF) {
            input.read(); input.read();
            do { first = input.read(); } while (first >= 0 && Character.isWhitespace(first));
        }
        input.reset();
        PortableLoggerDefinition parsed = first == '<'
                ? PortableLoggerDefinitionReader.read(input, protocol.name())
                : protocol == PortableLoggerProtocol.MUT2 ? PortableMut2LogConfigReader.read(input) : null;
        if (parsed == null) throw new IllegalArgumentException("SSM requires a logger XML definition");
        return parsed;
    }

    private void restoreLoggerSetup() {
        final int revision = loggerSetupRevision;
        final int definitionImport = loggerImports.beginDefinition();
        final int profileImport = loggerImports.beginProfile();
        loggerSetupState = "Restoring saved logger setup...";
        refreshLoggerSetupStatus();
        workerExecutor.execute(() -> {
            try {
                LoggerSetupStore.Setup saved = LOGGER_SETUP_IO.submit(
                        () -> LoggerSetupStore.restore(getFilesDir()))
                        .get(15, java.util.concurrent.TimeUnit.SECONDS);
                byte[] bytes = saved == null ? new byte[0] : saved.definitionBytes();
                PortableLoggerDefinition definition = bytes.length == 0 ? null
                        : parseLoggerDefinition(bytes, saved.protocol);
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    loggerImports.finishDefinition(definitionImport);
                    loggerImports.finishProfile(profileImport);
                    if (revision != loggerSetupRevision) { refreshLoggerSetupStatus(); return; }
                    if (saved == null) {
                        loggerSetupState = "Open a logger definition and, optionally, an existing profile.";
                    } else {
                        loggerProtocol = saved.protocol;
                        loggerDefinition = definition;
                        loggerDefinitionBytes = bytes;
                        loggerDefinitionName = saved.definitionName;
                        loggerProfile = saved.profile;
                        loggerProfileName = saved.profileName;
                        loggerSetupState = "Saved logger setup restored. No new recording was started.";
                        getPreferences(MODE_PRIVATE).edit()
                                .putString("logger_protocol", loggerProtocol.name()).apply();
                    }
                    if (loggerVisible) showLogger();
                });
            } catch (Exception failure) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    loggerImports.finishDefinition(definitionImport);
                    loggerImports.finishProfile(profileImport);
                    if (revision == loggerSetupRevision) loggerSetupState =
                            "Saved logger setup could not be restored. Import the definition and profile again.";
                    refreshLoggerSetupStatus();
                });
            }
        });
    }

    private void scheduleLoggerSetupSave() {
        if (loggerImports.isLoading()) return;
        final int revision = loggerSetupRevision;
        LoggerSetupStore.Setup setup = new LoggerSetupStore.Setup(loggerProtocol,
                loggerDefinitionName, loggerDefinitionBytes, loggerProfileName, loggerProfile);
        // Preserve write order across Activity instances, independently of long CSV exports.
        // Never persist USB or running state. This process-wide executor survives onDestroy.
        LOGGER_SETUP_IO.execute(() -> {
            try { LoggerSetupStore.save(getFilesDir(), setup); }
            catch (Exception failure) {
                runOnUiThread(() -> {
                    if (!isDestroyed() && revision == loggerSetupRevision) {
                        notice("Logger setup could not be saved for the next launch. Current logging setup is unchanged.");
                    }
                });
            }
        });
    }

    private void chooseArchivedLog() {
        if (archiveExportPending) {
            notice("Finish the pending recording export first.");
            return;
        }
        if (isLiveActive()) {
            notice("Stop logging and wait for the recording to finish first.");
            return;
        }
        File[] files = new File(getFilesDir(), "recordings").listFiles(
                file -> file.isFile() && file.getName().endsWith(".csv.part") && file.length() > 0);
        if (files == null || files.length == 0) {
            notice("No saved recovery recordings yet.");
            return;
        }
        java.util.Arrays.sort(files, (left, right) -> Long.compare(right.lastModified(), left.lastModified()));
        String[] names = new String[files.length];
        for (int index = 0; index < files.length; index++) {
            names[index] = files[index].getName() + " (" + files[index].length() + " bytes)";
        }
        new AlertDialog.Builder(this).setTitle("Export a retained recording")
                .setItems(names, (dialog, which) -> {
                    if (isLiveActive() || archiveExportPending) {
                        notice("Stop logging and finish the pending export first.");
                        return;
                    }
                    archiveToExport = files[which];
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("text/csv");
                    intent.putExtra(Intent.EXTRA_TITLE, files[which].getName().replace(".part", ""));
                    startActivityForResult(intent, SAVE_ARCHIVED_LOG);
                }).setNegativeButton("Cancel", null).show();
    }

    private boolean canReviewLog() {
        return !isDestroyed() && loggerVisible && !gaugesVisible && !isLiveActive() && !previewRunning;
    }

    private void cancelLogImport(String message) {
        logImportGeneration++;
        if (logImportTask != null) logImportTask.cancel(true);
        logImportTask = null;
        if (logImportSignal != null) {
            try { logImportSignal.cancel(); }
            catch (RuntimeException failure) { android.util.Log.w("RomRaider2", "CSV provider cancellation failed", failure); }
        }
        logImportSignal = null;
        logImportExecutor.purge();
        if (logImportLoading) logImportStatus = message == null
                ? "CSV import cancelled; previous summary retained." : message;
        logImportLoading = false;
        refreshLogImportStatus();
    }

    private void refreshLogImportStatus() {
        if (logImportStatusView != null) logImportStatusView.setText(logImportStatus);
        if (cancelLogImportButton != null) cancelLogImportButton.setEnabled(logImportLoading);
    }

    private void loadLogSummary(Uri uri) {
        if (!canReviewLog()) { notice("Stop logging and return to LOGGER before importing a CSV."); return; }
        cancelLogImport(null);
        int generation = logImportGeneration;
        android.os.CancellationSignal signal = new android.os.CancellationSignal();
        logImportSignal = signal;
        logImportLoading = true;
        logImportStatus = "Reading the complete CSV on a worker. Previous summary retained until validation finishes.";
        refreshLogImportStatus();
        try {
            logImportTask = logImportExecutor.submit(() -> {
                try {
                    String name = csvDisplayName(uri, signal);
                    signal.throwIfCanceled();
                    PortableLogCsvReader.Summary opened;
                    try (android.content.res.AssetFileDescriptor descriptor =
                            getContentResolver().openAssetFileDescriptor(uri, "r", signal)) {
                        if (descriptor == null) throw new IOException("CSV source is unavailable");
                        try (InputStream input = descriptor.createInputStream();
                             InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8.newDecoder()
                                     .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                                     .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT))) {
                            opened = PortableLogCsvReader.summarize(reader);
                        }
                    }
                    runOnUiThread(() -> {
                        if (isDestroyed() || generation != logImportGeneration) return;
                        logImportTask = null; logImportSignal = null; logImportLoading = false;
                        if (!canReviewLog()) {
                            logImportStatus = "CSV import was not applied after the workspace changed.";
                            refreshLogImportStatus(); return;
                        }
                        importedLogSummary = opened;
                        importedLogName = name;
                        importedLogPage = 0;
                        logImportStatus = "Complete file validated. This is an imported-log summary, not live data.";
                        refreshLogImportStatus();
                        showLogSummary();
                    });
                } catch (Exception failure) {
                    runOnUiThread(() -> {
                        if (isDestroyed() || generation != logImportGeneration) return;
                        logImportTask = null; logImportSignal = null; logImportLoading = false;
                        logImportStatus = "CSV was not imported: " + (failure.getMessage() == null
                                ? "The source could not be read." : failure.getMessage()) + " Previous summary retained.";
                        refreshLogImportStatus();
                    });
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException unavailable) {
            logImportSignal = null; logImportLoading = false;
            logImportStatus = "CSV import worker is unavailable. Previous summary retained.";
            refreshLogImportStatus();
        }
    }

    private void showLogSummary() {
        if (!loggerVisible || gaugesVisible || importedLogSummary == null) return;
        if (importedLogCard != null && importedLogCard.getParent() == content) content.removeView(importedLogCard);
        List<PortableLogCsvReader.ChannelSummary> channels = importedLogSummary.channels();
        int pages = Math.max(1, (channels.size() + 11) / 12);
        importedLogPage = Math.max(0, Math.min(importedLogPage, pages - 1));
        int start = importedLogPage * 12, end = Math.min(channels.size(), start + 12);
        StringBuilder summary = new StringBuilder("IMPORTED FILE — NOT LIVE\n")
                .append("Channels ").append(channels.isEmpty() ? 0 : start + 1).append('–').append(end)
                .append(" of ").append(channels.size()).append("\n\n");
        for (int index = start; index < end; index++) {
            PortableLogCsvReader.ChannelSummary channel = channels.get(index);
            PortableLogSample latest = channel.latest();
            summary.append(latest.getChannelName()).append(" [").append(latest.getChannelId()).append("]\n")
                    .append("Latest: ").append(Double.isFinite(latest.getValue()) ? Double.toString(latest.getValue()) : "Unavailable")
                    .append(' ').append(latest.getUnits()).append(" at ").append(latest.getTimestampMillis()).append(" ms\n")
                    .append("Finite: ").append(channel.finite()).append("  /  unavailable: ").append(channel.missing())
                    .append("\nMin / max: ").append(channel.finite() == 0 ? "Unavailable"
                            : channel.minimum() + " / " + channel.maximum()).append("\n\n");
        }
        LinearLayout card = sectionCard("IMPORTED LOG SUMMARY", importedLogName + "  /  "
                + importedLogSummary.values() + " values  /  " + channels.size() + " channels");
        card.addView(statusText(summary.toString().trim()), matchWrap());
        if (pages > 1) {
            Button previous = button("PREVIOUS CHANNELS"), next = button("NEXT CHANNELS");
            previous.setEnabled(importedLogPage > 0); next.setEnabled(importedLogPage + 1 < pages);
            previous.setOnClickListener(view -> { importedLogPage--; showLogSummary(); });
            next.setOnClickListener(view -> { importedLogPage++; showLogSummary(); });
            card.addView(actionRow(previous, next), matchWrap());
        }
        importedLogCard = card;
        content.addView(card, Math.min(3, content.getChildCount()), cardParams(dp(10)));
    }

    private boolean loggerImportPending() {
        if (!loggerImports.isLoading() && !setupTransferLoading) return false;
        notice("Wait for the logger definition, profile or setup transfer to finish loading.");
        return true;
    }

    private void toggleLoggerPreview() {
        if (previewRunning) {
            stopLoggerPreview("Offline preview stopped. The recorded values can be saved as CSV.");
            return;
        }
        if (isLiveActive()) {
            notice("Stop the live logger before starting simulated data.");
            return;
        }
        if (loggerImportPending()) return;
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
            previewPlan = PortableLoggerQueryPlan.create(previewSelections, loggerProtocol);
            clearLoggerGauges();
            previewSession = new PortableLogSession();
            previewCycle = 0;
            previewStartedAt = SystemClock.elapsedRealtime();
            cancelLogImport(null);
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
        intent.putExtra(Intent.EXTRA_TITLE, "RomRaider2-android-simulated.csv");
        startActivityForResult(intent, SAVE_PREVIEW_LOG);
    }

    private boolean isLiveActive() {
        // An unbound screen cannot assume that an existing service is idle.
        return recordingService == null || recordingService.busy();
    }

    private boolean loggerSetupEditable() {
        if (!isLiveActive()) return true;
        notice(recordingService == null ? "Waiting for recording-service status."
                : "Stop logging and wait for adapter cleanup before changing setup.");
        return false;
    }

    private void toggleLiveLogger() {
        if (recordingService == null || !activityResumed) {
            notice("Wait for the recording service while this screen is visible.");
            return;
        }
        if (isLiveActive()) {
            stopLiveLogger("Stopping after the current read...");
            return;
        }
        if (loggerImportPending()) return;
        OpenPortUsbTransport transport = openPort;
        PortableLoggerDefinition definition = loggerDefinition;
        PortableLoggerProfile profile = loggerProfile;
        if (transport == null) { notice("Prepare an OpenPort 2.0 first."); return; }
        if (definition == null || profile == null) {
            notice("Open a logger definition and profile first."); return;
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED
                && !getPreferences(MODE_PRIVATE).getBoolean("recording_notification_requested", false)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("recording_notification_requested", true).apply();
            requestPermissions(new String[] {android.Manifest.permission.POST_NOTIFICATIONS}, 24);
            notice("After choosing notification permission, press Start again. Logging has not started.");
            return;
        }
        stopLoggerPreview(null);
        try {
            UsbDevice selected = findOpenPort((UsbManager) getSystemService(USB_SERVICE));
            if (!recordingService.start(transport, selected, definition, profile)) {
                notice("Adapter permission or recording state changed. Prepare the OpenPort again.");
                return;
            }
            synchronized (usbLock) {
                usbGeneration++;
                openPort = null; // Exclusive ownership transferred; Activity must never close it.
            }
            usbState = "OpenPort is owned by the read-only recording service.";
            cancelLogImport(null);
            clearLoggerGauges();
            displayedRecording = null;
            displayedRecordingState = null;
            refreshRecording();
            refreshUsbStatus();
            if (!getSystemService(android.app.NotificationManager.class).areNotificationsEnabled()) {
                notice("Notifications are disabled. Return to RomRaider2 to stop recording; Android's Active apps Stop terminates the app.");
            }
        } catch (RuntimeException failure) {
            notice(failure.getMessage() == null ? "Recording could not start." : failure.getMessage());
        }
    }

    private void stopLiveLogger(String message) {
        if (recordingService != null && recordingService.busy()) recordingService.stop();
        if (message != null && liveLoggerView != null) liveLoggerView.setText(message);
    }

    private void refreshRecording() {
        if (recordingService == null || activityDestroyed) return;
        ReadOnlyRecording current = recordingService.recording();
        if (current == null) return;
        ReadOnlyRecording.Snapshot state = current.snapshot();
        boolean busy = recordingService.busy();
        if (current == displayedRecording && state == displayedRecordingState
                && !busy && !displayedRecordingBusy) return;
        if (current != displayedRecording) {
            clearLoggerGauges();
            displayedRecording = current;
            displayedRecordingState = null;
        }
        if (state != displayedRecordingState) {
            if (!state.values().isEmpty()) {
                for (int index = 0; index < Math.min(MOBILE_GAUGE_LIMIT, state.values().size()); index++) {
                    PortableLoggerValue value = state.values().get(index);
                    PortableSelectedParameter selected = value.getSelection();
                    String id = selected.getParameter().getId();
                    MobileGaugeSnapshot gauge = loggerGaugeSnapshots.get(id);
                    if (gauge == null) {
                        gauge = new MobileGaugeSnapshot(id, selected.getParameter().getName(),
                                selected.getConversion().getUnits(), selected.getConversion().getFormat(), value.getValue());
                        loggerGaugeSnapshots.put(id, gauge);
                    }
                    gauge.minimum = state.minimum(index);
                    gauge.maximum = state.maximum(index);
                }
                updateLoggerGauges(state.values());
                for (PortableLoggerValue value : state.values()) {
                    gaugeReceivedAt.put(value.getSelection().getParameter().getId(),
                            state.receivedAtNanos() / 1_000_000L);
                }
            }
            displayedRecordingState = state;
        }
        liveEcuIdentified = busy && state.phase() == ReadOnlyRecording.Phase.RECORDING;
        updateScreenAwake();
        if (liveLoggerButton != null) liveLoggerButton.setText(busy
                ? R.string.logger_live_stop : R.string.logger_live_start);
        if (liveLoggerView != null) {
            String message = !recordingService.failure().isEmpty() ? recordingService.failure()
                    : !busy ? getString(R.string.logger_live_stopped, state.message(),
                            getResources().getQuantityString(R.plurals.logger_live_recorded, state.samples(), state.samples()))
                    : state.phase() == ReadOnlyRecording.Phase.STOPPED ? "Releasing the adapter..."
                    : state.phase() == ReadOnlyRecording.Phase.RECORDING
                            ? liveValueSummary(state.ecuId(), state.timestampMillis(), state.values(), state.samples())
                            : state.phase() == ReadOnlyRecording.Phase.CONNECTING
                                ? state.ecuId().isEmpty()
                                    ? getString(R.string.logger_live_opening, current.protocol().name())
                                    : getString(R.string.logger_live_identified, state.ecuId(), state.ready(), state.unavailable())
                                : state.message();
            if (!message.contentEquals(liveLoggerView.getText())) liveLoggerView.setText(message);
        }
        if (!busy) liveLog = current.completedLog();
        displayedRecordingBusy = busy;
        refreshGaugeAvailability();
    }

    private void saveLiveLog() {
        refreshRecording();
        if (isLiveActive()) {
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
                    .append(MobileGaugeSnapshot.summaryValue(value.getValue()))
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
                .append(previewPlan.batches().size()).append(" ").append(loggerProtocol).append(" batch");
        if (previewPlan.batches().size() != 1) summary.append("es");
        for (PortableLoggerValue value : values) {
            summary.append("\n")
                    .append(value.getSelection().getParameter().getName())
                    .append("   ")
                    .append(MobileGaugeSnapshot.summaryValue(value.getValue()))
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
        loggerGaugeGrid = new MountedGaugeGrid(this);
        loggerGaugeGrid.setColumnCount(2);
        loggerGaugeGrid.setAlignmentMode(GridLayout.ALIGN_MARGINS);
        loggerGaugeGrid.setUseDefaultMargins(false);
        loggerGaugeGrid.addOnLayoutChangeListener((view, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> layoutGaugeColumns());
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
        configureMountedGaugeLayout();
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
                        GridLayout.spec(index / grid.getColumnCount()),
                        GridLayout.spec(index % grid.getColumnCount(), 1f));
                params.width = 0;
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                params.setMargins(dp(3), dp(3), dp(3), dp(3));
                grid.addView(gauge, params);
                loggerGaugeViews.put(id, gauge);
            }
        gauge.setValue(snapshot.id, snapshot.name,
                snapshot.displayValue(), snapshot.units, snapshot.value,
                snapshot.minimum, snapshot.maximum);
        gaugeReceivedAt.put(id, SystemClock.elapsedRealtime());
        gauge.setDataState(gaugeDemo || previewRunning ? "SIMULATED" : "LIVE");
        if (loggerGaugeEmpty != null && !loggerGaugeViews.isEmpty()) {
            loggerGaugeEmpty.setVisibility(View.GONE);
        }
    }

    private void showLoggerGaugeDemo() {
        if (isLiveActive()) {
            notice("Stop the live logger before showing simulated gauges.");
            return;
        }
        cancelLogImport(null);
        stopLoggerPreview(null);
        clearLoggerGauges();
        gaugeDemo = true;
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
        if (!gaugeDemo && !previewRunning && recordingService != null
                && recordingService.recording() != null) {
            recordingService.recording().resetPeaks();
            refreshRecording();
            return;
        }
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
        refreshGaugeAvailability();
    }

    private void clearLoggerGauges() {
        gaugeDemo = false;
        liveEcuIdentified = false;
        gaugeReceivedAt.clear();
        loggerGaugeSnapshots.clear();
        loggerGaugeViews.clear();
        if (loggerGaugeGrid != null) loggerGaugeGrid.removeAllViews();
    }

    private static List<byte[]> simulatedResponses(
            PortableLoggerQueryPlan plan, int cycle) {
        Map<Integer, Byte> valuesByAddress = new HashMap<>();
        for (PortableSelectedParameter selection : plan.readParameters()) {
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

    private String csvDisplayName(Uri uri, android.os.CancellationSignal signal) {
        String name = uri.getLastPathSegment();
        try (Cursor cursor = getContentResolver().query(uri,
                new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null, signal)) {
            if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
        }
        signal.throwIfCanceled();
        if (name == null || name.isEmpty()) return "recording.csv";
        return name.length() <= 512 ? name : name.substring(0, 512) + "…";
    }

    private static String copyName(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) + "-edited" + name.substring(dot)
                : name + "-edited.bin";
    }

    private void selectTab(Button selected, Button other) {
        for (Button tab : new Button[] { loggerTab, gaugesTab, editorTab }) {
            if (tab == null) continue;
            tab.setTextColor(tab == selected ? Color.WHITE : MUTED);
            tab.setBackground(rounded(tab == selected ? ACCENT : PANEL,
                    tab == selected ? ACCENT : BORDER, 8));
            tab.setSelected(tab == selected);
            tab.setMinHeight(dp(48));
        }
    }

    /** Changes only presentation: the logger, writer, and gauge instances survive. */
    private void showGaugesOnly() {
        if (gaugesVisible) return;
        cancelLogImport(null);
        if (!loggerVisible) showLogger();
        gaugeGridHome = (ViewGroup) loggerGaugeGrid.getParent();
        gaugeGridHomeIndex = gaugeGridHome.indexOfChild(loggerGaugeGrid);
        gaugeGridHome.removeView(loggerGaugeGrid);
        gaugesPage.removeAllViews();
        gaugesStatus = statusText("");
        gaugesStatus.setMaxLines(3);
        gaugesStatus.setEllipsize(android.text.TextUtils.TruncateAt.END);
        Button stop = button("STOP");
        stop.setMinHeight(dp(48));
        stop.setOnClickListener(view -> {
            stopLoggerPreview(null);
            stopLiveLogger("Stopped from gauges dashboard.");
            gaugeDemo = false;
            refreshGaugeAvailability();
        });
        LinearLayout status = new LinearLayout(this);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.addView(gaugesStatus, weighted());
        mountedLayoutButton = button("LAYOUT");
        mountedLayoutButton.setTextSize(10);
        mountedLayoutButton.setVisibility(View.GONE);
        mountedLayoutButton.setOnClickListener(view -> chooseMountedGaugeCount());
        status.addView(mountedLayoutButton);
        mountedModeButton = button("FULL SCREEN");
        mountedModeButton.setTextSize(11);
        mountedModeButton.setMinHeight(dp(48));
        mountedModeButton.setContentDescription("Full screen gauges; keep the display awake while visible");
        mountedModeButton.setOnClickListener(view -> setMountedFullScreen(!mountedFullScreen));
        status.addView(mountedModeButton);
        status.addView(stop);
        gaugesPage.addView(status, matchWrap());
        gaugesScroll = new ScrollView(this);
        gaugesScroll.addView(loggerGaugeGrid, matchWrap());
        mountedGaugeViewport = new FrameLayout(this);
        mountedGaugeViewport.addView(gaugesScroll, new FrameLayout.LayoutParams(-1, -1));
        gaugesPage.addView(mountedGaugeViewport, new LinearLayout.LayoutParams(-1, 0, 1f));
        gaugesVisible = true;
        layoutGaugeColumns();
        workspaceScroll.setVisibility(View.GONE);
        workspaceBrand.setVisibility(View.GONE);
        workspaceFooter.setVisibility(View.GONE);
        gaugesPage.setVisibility(View.VISIBLE);
        selectTab(gaugesTab, loggerTab);
        refreshGaugeAvailability();
    }

    private void leaveGaugesOnly() {
        if (!gaugesVisible) return;
        setMountedFullScreen(false);
        ((ViewGroup) loggerGaugeGrid.getParent()).removeView(loggerGaugeGrid);
        gaugeGridHome.addView(loggerGaugeGrid, gaugeGridHomeIndex, matchWrap());
        gaugesVisible = false;
        layoutGaugeColumns();
        gaugesPage.setVisibility(View.GONE);
        workspaceScroll.setVisibility(View.VISIBLE);
        workspaceBrand.setVisibility(View.VISIBLE);
        workspaceFooter.setVisibility(View.VISIBLE);
        selectTab(loggerTab, gaugesTab);
    }

    /** Display-only mode: never starts, stops, or replaces the recording or gauge grid. */
    private void setMountedFullScreen(boolean enabled) {
        if (enabled && !gaugesVisible) return;
        if (mountedFullScreen == enabled) return;
        if (enabled) {
            previousSystemUiVisibility = getWindow().getDecorView().getSystemUiVisibility();
            if (Build.VERSION.SDK_INT >= 30) {
                android.view.WindowInsetsController controller = getWindow().getInsetsController();
                if (controller != null) previousSystemBarsBehavior = controller.getSystemBarsBehavior();
                android.view.WindowInsets insets = getWindow().getDecorView().getRootWindowInsets();
                previousVisibleSystemBars = 0;
                for (int type : new int[]{android.view.WindowInsets.Type.statusBars(),
                        android.view.WindowInsets.Type.navigationBars(), android.view.WindowInsets.Type.captionBar()}) {
                    if (insets == null || insets.isVisible(type)) previousVisibleSystemBars |= type;
                }
            }
        }
        mountedFullScreen = enabled;
        ((ViewGroup) loggerGaugeGrid.getParent()).removeView(loggerGaugeGrid);
        gaugesScroll.setVisibility(enabled ? View.GONE : View.VISIBLE);
        if (enabled) mountedGaugeViewport.addView(loggerGaugeGrid, new FrameLayout.LayoutParams(-1, -1));
        else gaugesScroll.addView(loggerGaugeGrid, matchWrap());
        configureMountedGaugeLayout();
        workspaceTabs.setVisibility(enabled ? View.GONE : View.VISIBLE);
        mountedModeButton.setText(enabled ? "EXIT" : "FULL SCREEN");
        mountedModeButton.setContentDescription(enabled ? "Exit full screen gauges"
                : "Full screen gauges; keep the display awake while visible");
        if (Build.VERSION.SDK_INT >= 33) {
            if (enabled) {
                mountedBackCallback = () -> setMountedFullScreen(false);
                getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                        android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, mountedBackCallback);
            } else if (mountedBackCallback != null) {
                getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(mountedBackCallback);
                mountedBackCallback = null;
            }
        }
        applyMountedSystemBars();
        workspacePage.setPadding(dp(enabled ? 4 : 20), dp(enabled ? 0 : 18),
                dp(enabled ? 4 : 20), dp(enabled ? 0 : 12));
        workspacePage.requestApplyInsets();
        updateScreenAwake();
        refreshGaugeAvailability();
    }

    private void chooseMountedGaugeCount() {
        if (!mountedFullScreen) return;
        if (mountedLayoutDialog != null) mountedLayoutDialog.dismiss();
        mountedLayoutDialog = new AlertDialog.Builder(this)
                .setTitle("Gauges shown — logging unchanged")
                .setSingleChoiceItems(new String[]{"1 gauge", "2 gauges", "3 gauges", "4 gauges", "5 gauges", "6 gauges"},
                        mountedGaugeCount - 1, (dialog, selected) -> {
                            setMountedGaugeCount(selected + 1);
                            dialog.dismiss();
                        }).setNegativeButton("Cancel", null).create();
        mountedLayoutDialog.setOnDismissListener(dialog -> mountedLayoutDialog = null);
        mountedLayoutDialog.show();
    }

    private void setMountedGaugeCount(int count) {
        if (count < 1 || count > 6) throw new IllegalArgumentException("Choose one to six gauges");
        mountedGaugeCount = count;
        getPreferences(MODE_PRIVATE).edit().putInt("mounted_gauge_count", count).apply();
        configureMountedGaugeLayout();
        refreshGaugeAvailability();
    }

    private void configureMountedGaugeLayout() {
        if (loggerGaugeGrid instanceof MountedGaugeGrid) ((MountedGaugeGrid) loggerGaugeGrid).configure(
                mountedFullScreen, mountedGaugeCount, loggerGaugeTheme.instrumentStyle() == null ? 320.0 / 205 : 320.0 / 250);
        if (mountedLayoutButton != null) {
            mountedLayoutButton.setVisibility(mountedFullScreen ? View.VISIBLE : View.GONE);
            mountedLayoutButton.setText(String.format(Locale.ROOT, "LAYOUT %d", mountedGaugeCount));
            mountedLayoutButton.setContentDescription(String.format(Locale.ROOT, "Display up to %d gauges; choose a one to six gauge layout", mountedGaugeCount));
        }
    }

    @SuppressWarnings("deprecation") // API 26-29 fallback; modern devices use WindowInsetsController.
    private void applyMountedSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            android.view.WindowInsetsController controller = getWindow().getInsetsController();
            if (controller == null) return;
            if (mountedFullScreen) {
                controller.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(android.view.WindowInsets.Type.systemBars());
            } else {
                controller.setSystemBarsBehavior(previousSystemBarsBehavior);
                controller.hide(android.view.WindowInsets.Type.systemBars() & ~previousVisibleSystemBars);
                controller.show(previousVisibleSystemBars);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(mountedFullScreen
                    ? previousSystemUiVisibility | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    : previousSystemUiVisibility);
        }
    }

    private void updateScreenAwake() {
        boolean keepAwake = activityResumed && ((mountedFullScreen && gaugesVisible)
                || (recordingService != null && recordingService.busy()));
        if (keepAwake) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && mountedFullScreen) applyMountedSystemBars();
    }

    @Override
    @SuppressWarnings("deprecation")
    @SuppressLint("GestureBackNavigation") // API 26-32 only; API 33+ registers OnBackInvokedCallback above.
    public void onBackPressed() {
        if (mountedFullScreen) setMountedFullScreen(false);
        else super.onBackPressed();
    }

    private void refreshGaugeAvailability() {
        String state = gaugeDemo || previewRunning ? "SIMULATED"
                : recordingService == null ? "CHECKING RECORDING"
                : isLiveActive() ? (liveEcuIdentified ? "LIVE • RECORDING" : "CONNECTING / STOPPING") : "STOPPED";
        if (!gaugeDemo && !previewRunning && displayedRecordingState != null && isLiveActive()) {
            ReadOnlyRecording.Phase phase = displayedRecordingState.phase();
            if (phase == ReadOnlyRecording.Phase.STOPPING || phase == ReadOnlyRecording.Phase.STOPPED) state = "STOPPING";
            else if (phase == ReadOnlyRecording.Phase.RECORDING
                    && SystemClock.elapsedRealtimeNanos() - displayedRecordingState.receivedAtNanos() > 3_000_000_000L)
                state = "NO RECENT ECU DATA";
            else if (phase != ReadOnlyRecording.Phase.RECORDING) state = "CONNECTING";
        }
        if (gaugesStatus != null) gaugesStatus.setText(state + (loggerGaugeViews.isEmpty()
                ? "\nSelect channels in LOGGER while parked."
                : mountedFullScreen ? "\n" + Math.min(mountedGaugeCount, loggerGaugeViews.size()) + " of " + loggerGaugeViews.size()
                : "  •  " + loggerGaugeViews.size() + " gauges"));
        long now = SystemClock.elapsedRealtime();
        for (Map.Entry<String, MobileGaugeView> entry : loggerGaugeViews.entrySet()) {
            if (gaugeDemo) entry.getValue().setDataState("SIMULATED");
            else if (!previewRunning && !isLiveActive()) entry.getValue().markUnavailable("STOPPED");
            else if (now - gaugeReceivedAt.getOrDefault(entry.getKey(), 0L) > 3000)
                entry.getValue().markUnavailable("NO RECENT DATA");
        }
    }

    private void layoutGaugeColumns() {
        if (loggerGaugeGrid == null) return;
        if (mountedFullScreen) { configureMountedGaugeLayout(); return; }
        int columns = gaugesVisible ? Math.max(1, Math.min(4,
                loggerGaugeGrid.getWidth() / dp(220))) : 2;
        if (loggerGaugeGrid.getColumnCount() == columns) return;
        loggerGaugeGrid.removeAllViews();
        loggerGaugeGrid.setColumnCount(columns);
        int index = 0;
        for (MobileGaugeView gauge : loggerGaugeViews.values()) {
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(index / columns), GridLayout.spec(index % columns, 1f));
            params.width = 0;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.setMargins(dp(3), dp(3), dp(3), dp(3));
            loggerGaugeGrid.addView(gauge, params);
            index++;
        }
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
