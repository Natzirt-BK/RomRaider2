/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.runtime;

import static com.romraider.logger.ecu.profile.UserProfileLoader.BACKUP_PROFILE;
import static com.romraider.util.ParamChecker.isNullOrEmpty;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import org.apache.log4j.Logger;

import com.romraider.Settings;
import com.romraider.logger.api.LoggerChannel;
import com.romraider.logger.api.LoggerChannelKind;
import com.romraider.logger.api.LoggerChannelService;
import com.romraider.logger.api.LoggerChannelUnitOption;
import com.romraider.logger.api.LoggerGaugeTheme;
import com.romraider.logger.api.LoggerExternalSensor;
import com.romraider.logger.api.LoggerLiveDataBus;
import com.romraider.logger.api.LoggerMessageService;
import com.romraider.logger.api.LoggerSearchCatalog;
import com.romraider.logger.api.LoggerSessionService;
import com.romraider.logger.api.LoggerWorkspacePreferences;
import com.romraider.logger.ecu.comms.controller.LoggerController;
import com.romraider.logger.ecu.comms.controller.LoggerControllerImpl;
import com.romraider.logger.ecu.comms.query.EcuInit;
import com.romraider.logger.ecu.comms.query.EcuInitCallback;
import com.romraider.logger.ecu.comms.query.dimemod.DmInit;
import com.romraider.logger.ecu.comms.query.dimemod.DmInitCallback;
import com.romraider.logger.ecu.definition.EcuDataConvertor;
import com.romraider.logger.ecu.definition.EcuDataLoader;
import com.romraider.logger.ecu.definition.EcuDataLoaderImpl;
import com.romraider.logger.ecu.definition.EcuDefinition;
import com.romraider.logger.ecu.definition.EcuParameter;
import com.romraider.logger.ecu.definition.EcuSwitch;
import com.romraider.logger.ecu.definition.ExternalData;
import com.romraider.logger.ecu.definition.ExternalDataImpl;
import com.romraider.logger.ecu.definition.LoggerData;
import com.romraider.logger.ecu.definition.Module;
import com.romraider.logger.ecu.definition.Transport;
import com.romraider.logger.ecu.exception.ConfigurationException;
import com.romraider.logger.ecu.profile.UserProfile;
import com.romraider.logger.ecu.profile.UserProfileImpl;
import com.romraider.logger.ecu.profile.UserProfileItem;
import com.romraider.logger.ecu.profile.UserProfileItemImpl;
import com.romraider.logger.ecu.profile.UserProfileLoader;
import com.romraider.logger.ecu.profile.UserProfileLoaderImpl;
import com.romraider.logger.ecu.ui.EcuRelatedMessageListener;
import com.romraider.logger.ecu.ui.handler.file.FileLoggerControllerSwitchHandler;
import com.romraider.logger.ecu.ui.handler.file.FileLoggerControllerSwitchMonitorImpl;
import com.romraider.logger.ecu.ui.handler.file.FileLoggingConnectionMonitor;
import com.romraider.logger.ecu.ui.handler.file.FileUpdateHandlerImpl;
import com.romraider.logger.ecu.ui.spi.LoggerWorkspaceContext;
import com.romraider.logger.external.core.ExternalDataItem;
import com.romraider.logger.external.core.ExternalDataSource;
import com.romraider.logger.external.core.ExternalDataSourceLoader;
import com.romraider.logger.external.core.ExternalDataSourceLoaderImpl;
import com.romraider.platform.DimeModState;
import com.romraider.platform.PlatformContext;
import com.romraider.platform.RamTuneRuntimeMetadata;
import com.romraider.ui.RuntimeUiProfile;
import com.romraider.ui.ThemeMode;
import com.romraider.ui.ApplicationThemeService;
import com.romraider.util.SettingsManager;

/**
 * UI-neutral owner for the desktop Logger session.
 *
 * The transport, definition and CSV implementations are the established
 * RomRaider implementations. This class replaces the old hidden JFrame,
 * table models and Swing update handlers as their production owner.
 */
public final class LoggerDesktopRuntime implements EcuRelatedMessageListener,
        AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(
            LoggerDesktopRuntime.class);
    private static final String CALLER_ID = "compose-desktop-logger";

    private final Settings settings;
    private final LoggerLiveDataBus liveData;
    private final LoggerLiveDataUpdateHandler liveDataHandler;
    private final LoggerMessageService messages = new LoggerMessageService();
    private final FileUpdateHandlerImpl fileHandler;
    private final LoggerController controller;
    private final LoggerSessionService session;
    private final LoggerChannelService channels;
    private final LoggerWorkspacePreferences preferences;
    private final Map<String, LoggerData> dataById =
            new LinkedHashMap<String, LoggerData>();
    private final Set<String> selectedIds = new LinkedHashSet<String>();
    private final List<ExternalDataSource> externalSources =
            new ArrayList<ExternalDataSource>();
    private final List<ExternalData> externalData =
            new ArrayList<ExternalData>();
    private volatile EcuInit ecuInit;
    private volatile DmInit dmInit;
    private volatile boolean closed;

    public LoggerDesktopRuntime() {
        settings = SettingsManager.getSettings();
        liveData = LoggerLiveDataBus.getInstance();
        liveData.clearSamples();
        liveData.stopped();
        liveDataHandler = new LoggerLiveDataUpdateHandler(liveData);
        fileHandler = new FileUpdateHandlerImpl(this);

        EcuInitCallback ecuCallback = new EcuInitCallback() {
            @Override
            public void callback(EcuInit next) {
                handleEcuInit(next);
            }
        };
        DmInitCallback dmCallback = new DmInitCallback() {
            @Override
            public void callback(DmInit next, boolean forceUpdate) {
                handleDimeModInit(next, forceUpdate);
            }

            @Override
            public boolean needToInit() {
                return dmInit == null;
            }

            @Override
            public DmInit getDmInit() {
                return dmInit;
            }
        };
        controller = new LoggerControllerImpl(ecuCallback, dmCallback, this,
                Runnable::run, liveDataHandler, fileHandler);
        controller.addListener(liveData);
        controller.addListener(new FileLoggingConnectionMonitor(fileHandler,
                new Runnable() {
                    @Override public void run() { }
                }));
        fileHandler.addListener(liveData);
        session = new LoggerSessionService(liveData,
                controller::start, controller::stop,
                fileHandler::start, fileHandler::stop,
                failure -> reportError("Logger command failed", failure));
        channels = new LoggerChannelService(this::setSelected,
                this::setUnitOption,
                failure -> reportError("Logger channel selection failed",
                        failure));
        preferences = createPreferences(settings);

        loadEcuDefinitions();
        loadExternalSources();
        reloadDefinitionAndChannels(true);
    }

    public LoggerWorkspaceContext getWorkspaceContext() {
        return new LoggerWorkspaceContext(liveData, session, channels,
                preferences, messages, false);
    }

    public Settings getSettings() {
        return settings;
    }

    public synchronized List<LoggerExternalSensor> getExternalSensors() {
        List<LoggerExternalSensor> result =
                new ArrayList<LoggerExternalSensor>();
        for (ExternalDataSource source : externalSources) {
            result.add(new LoggerExternalSensor(source.getId(),
                    source.getName(), source.getPort()));
        }
        return Collections.unmodifiableList(result);
    }

    public synchronized void setExternalSensorPort(String id, String port) {
        if (controller.isStarted()) {
            throw new IllegalStateException(
                    "Disconnect the Logger before changing sensor ports");
        }
        String value = port == null ? "" : port.trim();
        for (ExternalDataSource source : externalSources) {
            if (!source.getId().equals(id)) continue;
            source.setPort(value);
            Map<String, String> configured = settings.getLoggerPluginPorts();
            if (configured == null) configured = new HashMap<String, String>();
            else configured = new HashMap<String, String>(configured);
            if (value.isEmpty()) configured.remove(id);
            else configured.put(id, value);
            settings.setLoggerPluginPorts(configured);
            return;
        }
        throw new IllegalArgumentException("Unknown external sensor: " + id);
    }

    /** Applies every external-sensor port together or leaves all unchanged. */
    public synchronized void setExternalSensorPorts(Map<String, String> ports) {
        if (controller.isStarted()) {
            throw new IllegalStateException(
                    "Disconnect the Logger before changing sensor ports");
        }
        Map<String, ExternalDataSource> sources =
                new LinkedHashMap<String, ExternalDataSource>();
        Map<String, String> previousPorts = new LinkedHashMap<String, String>();
        for (ExternalDataSource source : externalSources) {
            sources.put(source.getId(), source);
            previousPorts.put(source.getId(), source.getPort());
        }
        Map<String, String> requested = ports == null
                ? Collections.<String, String>emptyMap() : ports;
        for (String id : requested.keySet()) {
            if (!sources.containsKey(id)) {
                throw new IllegalArgumentException(
                        "Unknown external sensor: " + id);
            }
        }
        Map<String, String> previousSettings = settings.getLoggerPluginPorts();
        previousSettings = previousSettings == null
                ? null : new HashMap<String, String>(previousSettings);
        try {
            Map<String, String> configured = new HashMap<String, String>();
            for (Map.Entry<String, ExternalDataSource> entry
                    : sources.entrySet()) {
                String value = requested.containsKey(entry.getKey())
                        ? requested.get(entry.getKey()) : entry.getValue().getPort();
                value = value == null ? "" : value.trim();
                entry.getValue().setPort(value);
                if (!value.isEmpty()) configured.put(entry.getKey(), value);
            }
            settings.setLoggerPluginPorts(configured);
        } catch (RuntimeException failure) {
            for (Map.Entry<String, String> entry : previousPorts.entrySet()) {
                sources.get(entry.getKey()).setPort(entry.getValue());
            }
            settings.setLoggerPluginPorts(previousSettings);
            throw failure;
        }
    }

    /** Reloads definition-backed channels after connection setup changes. */
    public synchronized void reloadConfiguration() {
        requireConfigurationEditable();
        validateLoggerConfiguration();
        loadEcuDefinitions();
        reloadDefinitionAndChannels(false);
    }

    public synchronized void requireConfigurationEditable() {
        if (controller.isStarted()) {
            throw new IllegalStateException(
                    "Disconnect the Logger before changing its configuration");
        }
    }

    private void validateLoggerConfiguration() {
        String definitionPath = settings.getLoggerDefinitionFilePath();
        if (isNullOrEmpty(definitionPath)) return;
        File definition = new File(definitionPath);
        if (!definition.isFile()) {
            throw new ConfigurationException(
                    "Logger definition file was not found: " + definition);
        }
        if (!definition.getName().toLowerCase(java.util.Locale.ROOT)
                .endsWith(".xml")) {
            throw new ConfigurationException(
                    "Logger definitions must be XML files.");
        }
        if (isNullOrEmpty(settings.getLoggerProtocol())
                || isNullOrEmpty(settings.getTransportProtocol())
                || isNullOrEmpty(settings.getTargetModule())) {
            throw new ConfigurationException(
                    "Protocol, transport, and target module are required "
                            + "when an ECU Logger definition is configured.");
        }
        EcuDataLoader loader = new EcuDataLoaderImpl();
        loader.loadConfigFromXml(definitionPath,
                settings.getLoggerProtocol(),
                settings.getFileLoggingControllerSwitchId(), ecuInit);
    }

    public EcuInit getEcuInit() {
        return ecuInit;
    }

    private synchronized void handleEcuInit(EcuInit next) {
        if (next == null) return;
        String oldId = ecuInit == null ? null : ecuInit.getEcuId();
        ecuInit = next;
        if (oldId == null || !oldId.equals(next.getEcuId())) {
            LOGGER.info("ECU ID = " + next.getEcuId());
            dmInit = null;
            PlatformContext.getInstance().setDimeModRuntime(
                    DimeModState.UNKNOWN, false);
            reloadDefinitionAndChannels(false);
        }
    }

    private synchronized void handleDimeModInit(DmInit next,
            boolean forceUpdate) {
        PlatformContext.getInstance().setDimeModRuntime(
                next == null ? DimeModState.NOT_PRESENT : DimeModState.ACTIVE,
                next != null && next.isRamTuneEnabled(),
                next != null && next.isRamTuneEnabled()
                        ? new RamTuneRuntimeMetadata(next.getDimeModVersion(),
                                next.getRamTuneSignatureAddress(),
                                next.getRamTuneLutSize())
                        : null);
        if (next != dmInit || (next != null && forceUpdate)) {
            dmInit = next;
            reloadDefinitionAndChannels(false);
        }
    }

    private void loadEcuDefinitions() {
        Map<String, EcuDefinition> definitions =
                new HashMap<String, EcuDefinition>();
        Vector<File> files = settings.getEcuDefinitionFiles();
        for (File file : files) {
            if (!file.isFile()) {
                LOGGER.warn("ECU definition file not found: " + file);
                continue;
            }
            try {
                EcuDataLoader loader = new EcuDataLoaderImpl();
                loader.loadEcuDefsFromXml(file);
                definitions.putAll(loader.getEcuDefinitionMap());
            } catch (RuntimeException failure) {
                reportError("Unable to load ECU definitions from " + file,
                        failure);
            }
        }
        settings.setLoggerEcuDefinitionMap(definitions);
        LOGGER.info(definitions.size() + " ECU definitions loaded from "
                + files.size() + " files");
    }

    private void loadExternalSources() {
        try {
            ExternalDataSourceLoader loader =
                    new ExternalDataSourceLoaderImpl();
            loader.loadExternalDataSources(settings.getLoggerPluginPorts());
            externalSources.addAll(loader.getExternalDataSources());
            for (ExternalDataSource source : externalSources) {
                try {
                    for (ExternalDataItem item : source.getDataItems()) {
                        externalData.add(new ExternalDataImpl(item, source));
                    }
                } catch (RuntimeException failure) {
                    reportError("Unable to load external sensor "
                            + source.getName(), failure);
                }
            }
        } catch (RuntimeException failure) {
            reportError("Unable to load external sensor plugins", failure);
        }
    }

    private synchronized void reloadDefinitionAndChannels(boolean startup) {
        if (closed) return;
        Set<String> restore = new LinkedHashSet<String>(selectedIds);
        clearRegistrations();

        List<EcuParameter> parameters = new ArrayList<EcuParameter>();
        List<EcuSwitch> switches = new ArrayList<EcuSwitch>();
        List<EcuSwitch> diagnosticCodes = new ArrayList<EcuSwitch>();
        String definitionPath = settings.getLoggerDefinitionFilePath();
        if (isNullOrEmpty(definitionPath)) {
            settings.setLogExternalsOnly(true);
            LOGGER.warn("Logger definition file not configured; ECU channels "
                    + "are unavailable");
        } else {
            try {
                EcuDataLoader loader = new EcuDataLoaderImpl();
                loader.loadConfigFromXml(definitionPath,
                        settings.getLoggerProtocol(),
                        settings.getFileLoggingControllerSwitchId(), ecuInit);
                parameters.addAll(loader.getEcuParameters());
                if (dmInit != null) parameters.addAll(dmInit.getEcuParams());
                switches.addAll(loader.getEcuSwitches());
                diagnosticCodes.addAll(loader.getEcuCodes());
                settings.setLoggerConnectionProperties(
                        loader.getConnectionProperties());
                configureDestination(loader);
                installFileLoggingSwitch(loader.getFileLoggingControllerSwitch());
                LOGGER.info("Loaded Logger protocol "
                        + settings.getLoggerProtocol() + ": "
                        + parameters.size() + " parameters, "
                        + switches.size() + " switches");
            } catch (ConfigurationException failure) {
                settings.setDestinationTarget(null);
                settings.setLogExternalsOnly(true);
                reportError("Unable to load Logger definition", failure);
            }
        }

        sort(parameters);
        sort(switches);
        sort(externalData);
        for (EcuParameter parameter : parameters) {
            dataById.put(parameter.getId(), parameter);
            parameter.addConvertorUpdateListener(fileHandler);
        }
        for (EcuSwitch ecuSwitch : switches) {
            dataById.put(ecuSwitch.getId(), ecuSwitch);
        }
        for (ExternalData data : externalData) {
            dataById.put(data.getId(), data);
            data.addConvertorUpdateListener(fileHandler);
        }
        LoggerSearchCatalog.publish(parameters, diagnosticCodes);

        UserProfile profile = loadProfile();
        if (profile != null) {
            for (LoggerData data : dataById.values()) {
                applyUnits(profile, data);
                if (startup && (profile.isSelectedOnLiveDataTab(data)
                        || profile.isSelectedOnGraphTab(data)
                        || profile.isSelectedOnDashTab(data))) {
                    restore.add(data.getId());
                }
            }
        }
        publishChannels();
        for (String id : restore) {
            if (dataById.containsKey(id)) select(id, true);
        }
        publishChannels();
    }

    private void installFileLoggingSwitch(EcuSwitch ecuSwitch) {
        if (ecuSwitch == null) {
            settings.setFileLoggingControllerSwitchActive(false);
            return;
        }
        controller.setFileLoggerSwitchMonitor(
                new FileLoggerControllerSwitchMonitorImpl(ecuSwitch,
                        new FileLoggerControllerSwitchHandler() {
                    private boolean previous;
                    @Override
                    public void handleSwitch(double value) {
                        boolean logging = ((int) value) == 1;
                        if (settings.isFileLoggingControllerSwitchActive()
                                && logging != previous) {
                            if (logging) fileHandler.start();
                            else fileHandler.stop();
                        }
                        previous = logging;
                    }
                }));
    }

    private void configureDestination(EcuDataLoader loader) {
        Map<Transport, Collection<Module>> transports = loader.getProtocols()
                .get(settings.getLoggerProtocol());
        if (transports == null || transports.isEmpty()) {
            settings.setDestinationTarget(null);
            settings.setLogExternalsOnly(true);
            return;
        }
        Transport selectedTransport = null;
        for (Transport transport : transports.keySet()) {
            if (transport.getId().equalsIgnoreCase(
                    settings.getTransportProtocol())) {
                selectedTransport = transport;
                break;
            }
        }
        if (selectedTransport == null) {
            selectedTransport = transports.keySet().iterator().next();
            settings.setTransportProtocol(selectedTransport.getId());
        }
        Collection<Module> modules = transports.get(selectedTransport);
        if (modules == null || modules.isEmpty()) {
            settings.setDestinationTarget(null);
            settings.setLogExternalsOnly(true);
            return;
        }
        Module selectedModule = null;
        for (Module module : modules) {
            if (module.getName().equalsIgnoreCase(settings.getTargetModule())) {
                selectedModule = module;
                break;
            }
        }
        if (selectedModule == null) selectedModule = modules.iterator().next();
        settings.setDestinationTarget(selectedModule);
        settings.setTargetModule(selectedModule.getName());
        if (!selectedModule.getFastPoll()) settings.setFastPoll(false);
        settings.setLogExternalsOnly(false);
    }

    private UserProfile loadProfile() {
        String path = settings.getLoggerProfileFilePath();
        if (isNullOrEmpty(path)) {
            path = System.getProperty("user.home") + BACKUP_PROFILE;
        }
        if (!new File(path).isFile()) return null;
        return new UserProfileLoaderImpl().loadProfile(path);
    }

    private static void applyUnits(UserProfile profile, LoggerData data) {
        if (!profile.contains(data)) return;
        try {
            EcuDataConvertor convertor = profile.getSelectedConvertor(data);
            if (convertor != null) data.selectConvertor(convertor);
        } catch (RuntimeException failure) {
            LOGGER.warn("Unable to apply saved units for " + data.getName(),
                    failure);
        }
    }

    private synchronized void setSelected(Collection<String> ids,
            boolean selected) {
        for (String id : ids) select(id, selected);
        publishChannels();
    }

    private synchronized void setUnitOption(String id, String optionId) {
        LoggerData data = dataById.get(id);
        if (data == null) return;
        final int index;
        try {
            index = Integer.parseInt(optionId);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("Invalid unit option for "
                    + data.getName(), failure);
        }
        EcuDataConvertor[] convertors = data.getConvertors();
        if (index < 0 || index >= convertors.length) {
            throw new IllegalArgumentException("Invalid unit option for "
                    + data.getName());
        }
        data.selectConvertor(convertors[index]);
        if (data instanceof EcuSwitch) {
            fileHandler.notifyConvertorUpdate(data);
        }
        liveData.remove(data);
        publishChannels();
    }

    private void select(String id, boolean selected) {
        LoggerData data = dataById.get(id);
        if (data == null || selectedIds.contains(id) == selected) return;
        data.setSelected(selected);
        if (selected) {
            liveDataHandler.registerData(data);
            fileHandler.registerData(data);
            controller.addLogger(CALLER_ID, data);
            selectedIds.add(id);
        } else {
            controller.removeLogger(CALLER_ID, data);
            fileHandler.deregisterData(data);
            liveDataHandler.deregisterData(data);
            selectedIds.remove(id);
        }
    }

    private void clearRegistrations() {
        for (String id : new ArrayList<String>(selectedIds)) {
            select(id, false);
        }
        dataById.clear();
    }

    private void publishChannels() {
        List<LoggerChannel> catalog = new ArrayList<LoggerChannel>();
        for (LoggerData data : dataById.values()) {
            EcuDataConvertor convertor = data.getSelectedConvertor();
            catalog.add(new LoggerChannel(data.getId(), data.getName(),
                    convertor == null ? "" : convertor.getUnits(),
                    kind(data), selectedIds.contains(data.getId()),
                    unitOptions(data)));
        }
        channels.replaceChannels(catalog);
    }

    private static List<LoggerChannelUnitOption> unitOptions(LoggerData data) {
        List<LoggerChannelUnitOption> result =
                new ArrayList<LoggerChannelUnitOption>();
        EcuDataConvertor selected = data.getSelectedConvertor();
        EcuDataConvertor[] convertors = data.getConvertors();
        for (int index = 0; index < convertors.length; index++) {
            EcuDataConvertor convertor = convertors[index];
            String units = convertor.getUnits();
            String label = isNullOrEmpty(units)
                    ? "Option " + (index + 1) : units;
            result.add(new LoggerChannelUnitOption(
                    Integer.toString(index), label, convertor == selected));
        }
        return result;
    }

    private static LoggerChannelKind kind(LoggerData data) {
        switch (data.getDataType()) {
        case SWITCH:
            return LoggerChannelKind.SWITCH;
        case EXTERNAL:
            return LoggerChannelKind.EXTERNAL;
        default:
            return LoggerChannelKind.PARAMETER;
        }
    }

    private synchronized UserProfile currentProfile() {
        Map<String, UserProfileItem> parameters =
                new LinkedHashMap<String, UserProfileItem>();
        Map<String, UserProfileItem> switches =
                new LinkedHashMap<String, UserProfileItem>();
        Map<String, UserProfileItem> externals =
                new LinkedHashMap<String, UserProfileItem>();
        for (LoggerData data : dataById.values()) {
            EcuDataConvertor convertor = data.getSelectedConvertor();
            boolean selected = selectedIds.contains(data.getId());
            UserProfileItem item = new UserProfileItemImpl(
                    convertor == null ? "" : convertor.getUnits(),
                    selected, selected, selected);
            switch (data.getDataType()) {
            case SWITCH:
                switches.put(data.getId(), item);
                break;
            case EXTERNAL:
                externals.put(data.getId(), item);
                break;
            default:
                parameters.put(data.getId(), item);
                break;
            }
        }
        return new UserProfileImpl(parameters, switches, externals,
                settings.getLoggerProtocol());
    }

    private void backupCurrentProfile() {
        Path target = new File(System.getProperty("user.home")
                + BACKUP_PROFILE).toPath();
        try {
            Path parent = target.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.write(target, currentProfile().getBytes());
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Backup profile saved");
            }
        } catch (IOException | RuntimeException failure) {
            LOGGER.warn("Unable to save the current Logger profile", failure);
        }
    }

    private static <T extends LoggerData> void sort(List<T> values) {
        Collections.sort(values, new Comparator<LoggerData>() {
            @Override
            public int compare(LoggerData left, LoggerData right) {
                return left.getName().compareToIgnoreCase(right.getName());
            }
        });
    }

    private static LoggerWorkspacePreferences createPreferences(
            Settings settings) {
        Boolean storedDark = settings.getLoggerWorkspaceDarkTheme();
        ThemeMode applicationTheme = ApplicationThemeService.getInstance()
                .getCurrentMode();
        boolean dark = RuntimeUiProfile.isSteamOs()
                || (storedDark != null ? storedDark.booleanValue()
                        : applicationTheme == ThemeMode.DARK
                        || applicationTheme == ThemeMode.HIGH_CONTRAST);
        return new LoggerWorkspacePreferences(
                settings.getLoggerWorkspaceView(), dark,
                RuntimeUiProfile.isSteamOs() ? LoggerGaugeTheme.HANDHELD
                        : settings.getLoggerGaugeTheme(),
                (view, nextDark) -> {
                    settings.setLoggerWorkspaceView(view);
                    settings.setLoggerWorkspaceDarkTheme(nextDark);
                }, settings::setLoggerGaugeTheme,
                settings.getLoggerGaugeLayout(),
                settings::setLoggerGaugeLayout,
                settings.getLoggerGaugeConfigurations(),
                settings::setLoggerGaugeConfiguration,
                settings.getLoggerDashboardTiles(),
                settings::setLoggerDashboardTile,
                settings.getLoggerParameterListState(),
                settings::setLoggerParameterListState);
    }

    @Override
    public void reportStats(String message) {
        messages.statistics(message);
        if (LOGGER.isDebugEnabled()) LOGGER.debug(message);
    }

    @Override
    public void reportMessage(String message) {
        messages.message(message);
        if (message != null && LOGGER.isDebugEnabled()) LOGGER.debug(message);
    }

    @Override
    public void reportMessageInTitleBar(String message) {
        messages.message(message);
        if (message != null) LOGGER.info(message);
    }

    @Override
    public void reportError(String error) {
        messages.error(error);
        LOGGER.error(error);
    }

    @Override
    public void reportError(Exception failure) {
        messages.error(failure.getMessage());
        LOGGER.error(failure.getMessage(), failure);
    }

    @Override
    public void reportError(String error, Exception failure) {
        messages.error(error + (failure.getMessage() == null ? ""
                : ": " + failure.getMessage()));
        LOGGER.error(error, failure);
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        session.close();
        controller.stop();
        backupCurrentProfile();
        clearRegistrations();
        fileHandler.cleanUp();
        for (ExternalDataSource source : externalSources) {
            try {
                source.disconnect();
            } catch (RuntimeException failure) {
                LOGGER.warn("Unable to disconnect " + source.getName(),
                        failure);
            }
        }
        liveData.clearSamples();
        liveData.stopped();
        try {
            SettingsManager.save(settings);
        } catch (RuntimeException failure) {
            LOGGER.error("Unable to save Logger settings", failure);
        }
    }
}
