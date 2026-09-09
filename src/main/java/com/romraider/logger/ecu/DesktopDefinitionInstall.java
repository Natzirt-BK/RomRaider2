/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider.logger.ecu;

import com.romraider.Settings;
import com.romraider.logger.ecu.definition.EcuDataLoaderImpl;
import com.romraider.logger.ecu.definition.LoggerDefinitionInstaller;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;

/** One Swing owner's definition request. Preparation never reads live settings. */
public final class DesktopDefinitionInstall {
    final SwingLoggerInitialization.Reload reload;
    private final Supplier<Settings> settingsSource;
    private final Supplier<Path> directorySource;
    private final BooleanSupplier stopped;
    private final Runnable save;
    private final Settings settings;
    private final Path directory;
    private final String previousPath;
    private final String protocol;
    private final String controlSwitch;
    private final List<Object> configuration;
    private boolean attempted;
    private String installedPath;

    DesktopDefinitionInstall(SwingLoggerInitialization.Reload reload,
            Supplier<Settings> settingsSource, Supplier<Path> directorySource,
            BooleanSupplier stopped, Runnable save) {
        requireEdt();
        this.reload = java.util.Objects.requireNonNull(reload, "reload");
        this.settingsSource = settingsSource;
        this.directorySource = directorySource;
        this.stopped = stopped;
        this.save = save;
        settings = settingsSource.get();
        directory = directorySource.get();
        previousPath = settings.getLoggerDefinitionFilePath();
        protocol = settings.getLoggerProtocol();
        controlSwitch = settings.getFileLoggingControllerSwitchId();
        configuration = configuration(settings);
    }

    private static List<Object> configuration(Settings s) {
        return Arrays.asList(s.getLoggerProtocol(), s.getTransportProtocol(),
                String.valueOf(s.getTargetModule()).toUpperCase(java.util.Locale.ROOT),
                s.getLoggerPort(), s.getJ2534Device(),
                s.getLoggerProfileFilePath(), s.getFileLoggingControllerSwitchId(),
                s.getVehiclePlatform(), s.getVehicleModule(), s.getAutoConnectOnStartup());
    }

    private boolean configurationCurrent(String path) {
        return settingsSource.get() == settings
                && directory.equals(directorySource.get())
                && java.util.Objects.equals(path, settings.getLoggerDefinitionFilePath())
                && configuration.equals(configuration(settings));
    }

    public boolean isCurrent() {
        requireEdt();
        return !attempted && stopped.getAsBoolean() && reload.isCurrent()
                && configurationCurrent(previousPath);
    }

    /** May run on a worker. The parser uses exactly the captured inputs and frozen bytes. */
    public Candidate prepare(Path source) throws Exception {
        LoggerDefinitionInstaller.PreparedDefinition definition =
                new LoggerDefinitionInstaller().prepare(source);
        EcuDataLoaderImpl loaded = new EcuDataLoaderImpl();
        loaded.loadConfigForDesktop(definition.sourcePath().toString(), definition.snapshot(),
                protocol, controlSwitch, reload.state.ecu);
        return new Candidate(this, definition, loaded);
    }

    /** No dialogs/catalog callbacks here: commit and recovery finish before returning to UI. */
    public LoggerDefinitionInstaller.Installation commit(Candidate candidate) throws Exception {
        requireEdt();
        if (candidate.owner != this) throw new IllegalArgumentException("Definition belongs to another request");
        if (!isCurrent()) return null;
        attempted = true;
        LoggerDefinitionInstaller.Installation[] result = {null};
        // startLogging invalidates the token before starting the controller.
        // Never acquire the controller monitor while holding the owner monitor.
        boolean committed = reload.commit(() -> configurationCurrent(previousPath), () -> {
            LoggerDefinitionInstaller.Installation installation = candidate.definition.install(directory);
            installedPath = installation.installedFile().toAbsolutePath().toString();
            settings.setLoggerDefinitionFilePath(installedPath);
            try {
                save.run();
            } catch (Exception failure) {
                try {
                    installation.rollback();
                    settings.setLoggerDefinitionFilePath(previousPath);
                    save.run();
                } catch (Exception recoveryFailure) {
                    failure.addSuppressed(recoveryFailure);
                }
                installedPath = null;
                throw failure;
            }
            result[0] = installation;
        });
        return committed ? result[0] : null;
    }

    public boolean canActivate(Candidate candidate) {
        requireEdt();
        return candidate.owner == this && installedPath != null && stopped.getAsBoolean() && reload.isCurrent()
                && configurationCurrent(installedPath);
    }

    public static final class Candidate {
        private final DesktopDefinitionInstall owner;
        private final LoggerDefinitionInstaller.PreparedDefinition definition;
        final EcuDataLoaderImpl loaded;

        private Candidate(DesktopDefinitionInstall owner,
                LoggerDefinitionInstaller.PreparedDefinition definition, EcuDataLoaderImpl loaded) {
            this.owner = owner;
            this.definition = definition;
            this.loaded = loaded;
        }
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread())
            throw new IllegalStateException("Definition installation state requires the EDT");
    }
}
