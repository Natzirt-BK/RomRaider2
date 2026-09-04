/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.logger.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Checkbox
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.romraider.Settings
import com.romraider.Version
import com.romraider.desktop.DesktopApplicationProvider
import com.romraider.desktop.DesktopApplicationCommands
import com.romraider.editor.calibration.CalibrationGridProjectionService
import com.romraider.editor.calibration.TableCalibrationEditController
import com.romraider.editor.document.EditorDocument
import com.romraider.editor.document.EditorDocumentController
import com.romraider.editor.document.EditorDocumentSession
import com.romraider.editor.document.EditorDocumentSnapshot
import com.romraider.editor.ecu.spi.CalibrationWorkspaceContext
import com.romraider.editor.ecu.spi.EditorNavigationWorkspaceContext
import com.romraider.editor.ecu.spi.RomComparisonWorkspaceContext
import com.romraider.editor.io.DefinitionFileSupport
import com.romraider.editor.io.RomLoadInteraction
import com.romraider.editor.recovery.RecoverySnapshot
import com.romraider.logger.api.LoggerSessionState
import com.romraider.logger.analysis.LogDataset
import com.romraider.logger.analysis.RomRaiderCsvLogParser
import com.romraider.logger.runtime.LoggerDesktopRuntime
import com.romraider.maps.Rom
import com.romraider.maps.RomUserInteraction
import com.romraider.maps.RomUserInteractionService
import com.romraider.maps.Table
import com.romraider.swing.IntegratedFileChooser
import com.romraider.swing.SettingsForm
import com.romraider.util.SettingsManager
import com.romraider.ui.ApplicationThemeService
import com.romraider.ui.RuntimeUiProfile
import com.romraider.ui.ThemeMode
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.Vector
import javax.swing.JFileChooser
import javax.swing.filechooser.FileFilter

/**
 * Compose-owned Editor shell. The temporary Swing shell can still be selected
 * with -Dromraider2.desktop.shell=swing during migration qualification.
 */
class ComposeDesktopApplicationProvider : DesktopApplicationProvider {
    override fun getName(): String = "Compose Desktop ECU Studio"

    override fun supports(arguments: Array<out String>): Boolean {
        val requested = System.getProperty("romraider2.desktop.shell", "javafx")
        return requested.equals("compose", ignoreCase = true)
    }

    override fun launch(arguments: Array<out String>) {
        if (arguments.any { it.startsWith("-logger", ignoreCase = true) }) {
            launchLoggerShell(arguments)
        } else {
            launchEditorShell(arguments.map(::File).filter(File::isFile))
        }
    }
}

private val shellGraphite = Color(0xFF10151B)
private val shellSurface = Color(0xFF171E26)
private val shellText = Color(0xFFE4E8ED)
private val shellAccent = Color(0xFF56AEA6)

private data class ShellQuestion(
    val title: String,
    val message: String,
    val approve: String,
    val reject: String,
    val answer: CompletableFuture<Boolean>
)

private fun launchLoggerShell(arguments: Array<out String>) {
    val runtime = LoggerDesktopRuntime()
    application {
        LoggerDesktopWindow(runtime) {
            runtime.close()
            exitApplication()
        }
    }
}

@Composable
private fun LoggerDesktopWindow(
    runtime: LoggerDesktopRuntime,
    onClose: () -> Unit
) {
    val windowIcon = romRaiderWindowIcon()
    val context = remember(runtime) { runtime.workspaceContext }
    val configurationMissing = remember(runtime) {
        runtime.settings.loggerDefinitionFilePath.isNullOrBlank()
    }
    var showSetup by remember(runtime) {
        mutableStateOf(configurationMissing)
    }
    var notice by remember(runtime) { mutableStateOf<String?>(null) }
    var showExternalSetup by remember(runtime) { mutableStateOf(false) }
    var offlineDataset by remember(runtime) { mutableStateOf<LogDataset?>(null) }
    var loadingLog by remember(runtime) { mutableStateOf(false) }
    var sessionState by remember(runtime) {
        mutableStateOf(context.session.state)
    }
    DisposableEffect(context.session) {
        val listener = java.util.function.Consumer<LoggerSessionState> {
            next -> EventQueue.invokeLater { sessionState = next }
        }
        context.session.addStateListener(listener)
        onDispose { context.session.removeStateListener(listener) }
    }
    Window(
        onCloseRequest = onClose,
        title = "${Version.PRODUCT_NAME} ${Version.VERSION} | Logger",
        icon = windowIcon,
        state = rememberFittedWindowState(1380, 860)
    ) {
        val commandHandler = remember(runtime, window) {
            DesktopApplicationCommands.Handler { arguments ->
                if (arguments.any {
                        it.startsWith("-logger", ignoreCase = true)
                    }) {
                    EventQueue.invokeLater {
                        window.isVisible = true
                        window.toFront()
                        window.requestFocus()
                    }
                    true
                } else false
            }
        }
        DisposableEffect(commandHandler) {
            DesktopApplicationCommands.register(commandHandler)
            onDispose { DesktopApplicationCommands.unregister(commandHandler) }
        }
        MenuBar {
            Menu("File") {
                Item(if (loadingLog) "Opening CSV log..." else "Open CSV log...",
                    enabled = !loadingLog,
                    onClick = {
                        val selected = chooseFiles(window, "Open CSV log",
                            FileDialog.LOAD, false).firstOrNull()
                            ?: return@Item
                        loadingLog = true
                        CompletableFuture.supplyAsync {
                            RomRaiderCsvLogParser().parse(selected)
                        }.whenComplete { dataset, error ->
                            EventQueue.invokeLater {
                                loadingLog = false
                                if (error == null) offlineDataset = dataset
                                else notice = rootMessage(error)
                            }
                        }
                    })
                Separator()
                Item("Logger setup...", onClick = { showSetup = true })
                Item("External sensors...", onClick = {
                    showExternalSetup = true
                })
                Separator()
                Item("Close", onClick = onClose)
            }
            Menu("Logger") {
                Item("Connect", onClick = context.session::connect,
                    enabled = sessionState == LoggerSessionState.STOPPED)
                Item("Disconnect", onClick = context.session::disconnect,
                    enabled = sessionState != LoggerSessionState.STOPPED)
                Separator()
                Item("Start recording",
                    onClick = context.session::startRecording,
                    enabled = sessionState == LoggerSessionState.LIVE_ECU ||
                        sessionState == LoggerSessionState.LIVE_EXTERNAL)
                Item("Stop recording",
                    onClick = context.session::stopRecording,
                    enabled = sessionState == LoggerSessionState.RECORDING)
            }
        }
        LoggerWorkspace(context, onOpenSetup = { showSetup = true })
        LaunchedEffect(runtime) {
            if (!configurationMissing && runtime.settings.autoConnectOnStartup &&
                context.session.state == LoggerSessionState.STOPPED) {
                context.session.connect()
            }
        }
        if (showSetup) {
            LoggerSetupDialog(runtime, window,
                onDismiss = { showSetup = false },
                onError = { notice = it })
        }
        if (showExternalSetup) {
            ExternalSensorSetupDialog(runtime,
                onDismiss = { showExternalSetup = false },
                onError = { notice = it })
        }
        notice?.let { message ->
            ShellAlertDialog(
                onDismissRequest = { notice = null },
                title = { Text("Logger setup") },
                text = { Text(message) },
                confirmButton = {
                    Button(onClick = { notice = null }) { Text("OK") }
                }
            )
        }
    }
    offlineDataset?.let { dataset ->
        OfflineLogAnalysisWindow(dataset) { offlineDataset = null }
    }
}

@Composable
private fun ExternalSensorSetupDialog(
    runtime: LoggerDesktopRuntime,
    onDismiss: () -> Unit,
    onError: (String) -> Unit
) {
    val sensors = remember(runtime) { runtime.externalSensors }
    var ports by remember(runtime) {
        mutableStateOf(sensors.associate { it.id to it.port })
    }
    ShellAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("External sensors") },
        text = {
            if (sensors.isEmpty()) {
                Text("No external sensor plugins are installed.")
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 470.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sensors, key = { it.id }) { sensor ->
                        OutlinedTextField(
                            value = ports[sensor.id].orEmpty(),
                            onValueChange = { value ->
                                ports = ports + (sensor.id to value)
                            },
                            label = { Text(sensor.name) },
                            placeholder = { Text("Serial port") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                runCatching {
                    runtime.setExternalSensorPorts(ports)
                    SettingsManager.save(runtime.settings)
                }.onSuccess { onDismiss() }
                    .onFailure { onError(rootMessage(it)) }
            }) { Text("Save ports") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun LoggerSetupDialog(
    runtime: LoggerDesktopRuntime,
    owner: java.awt.Window,
    onDismiss: () -> Unit,
    onError: (String) -> Unit
) {
    val settings = runtime.settings
    var definition by remember { mutableStateOf(
        settings.loggerDefinitionFilePath.orEmpty()) }
    var port by remember { mutableStateOf(settings.loggerPort.orEmpty()) }
    var protocol by remember { mutableStateOf(settings.loggerProtocol.orEmpty()) }
    var transport by remember { mutableStateOf(
        settings.transportProtocol.orEmpty()) }
    var target by remember { mutableStateOf(settings.targetModule.orEmpty()) }
    var outputDirectory by remember { mutableStateOf(
        settings.loggerOutputDirPath.orEmpty()) }
    var autoConnect by remember { mutableStateOf(settings.autoConnectOnStartup) }

    ShellAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Logger setup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(definition, { definition = it },
                        label = { Text("Logger definition XML") },
                        singleLine = true, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        chooseFiles(owner, "Choose Logger definition",
                            FileDialog.LOAD, false,
                            definition.takeIf(String::isNotBlank)?.let(::File),
                            "Logger definitions (.xml)") { file ->
                            file.extension.equals("xml", ignoreCase = true)
                        }.firstOrNull()?.let {
                            definition = it.absolutePath
                        }
                    }) { Text("Browse") }
                }
                OutlinedTextField(port, { port = it },
                    label = { Text("Serial port (blank for J2534)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(protocol, { protocol = it },
                        label = { Text("Protocol") }, singleLine = true,
                        modifier = Modifier.weight(1f))
                    OutlinedTextField(transport, { transport = it },
                        label = { Text("Transport") }, singleLine = true,
                        modifier = Modifier.weight(1f))
                }
                OutlinedTextField(target, { target = it },
                    label = { Text("Target module") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(outputDirectory, { outputDirectory = it },
                        label = { Text("Log output directory") },
                        singleLine = true, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        chooseDirectory(owner, "Choose log output directory",
                            outputDirectory.takeIf(String::isNotBlank)
                                ?.let(::File))
                            ?.let { outputDirectory = it.absolutePath }
                    }) { Text("Browse") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(autoConnect, { autoConnect = it })
                    Text("Connect when the Logger opens")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val previous = loggerSetupValues(settings)
                val requested = LoggerSetupValues(
                    definition.trim(), port.trim(), protocol.trim(),
                    transport.trim(), target.trim(), outputDirectory.trim(),
                    autoConnect)
                runCatching {
                    runtime.requireConfigurationEditable()
                    applyLoggerSetup(settings, requested)
                    runtime.reloadConfiguration()
                    SettingsManager.save(settings)
                }.onSuccess { onDismiss() }
                    .onFailure {
                        applyLoggerSetup(settings, previous)
                        runCatching { runtime.reloadConfiguration() }
                        onError(rootMessage(it))
                    }
            }) { Text("Save setup") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private data class LoggerSetupValues(
    val definition: String,
    val port: String,
    val protocol: String,
    val transport: String,
    val target: String,
    val outputDirectory: String,
    val autoConnect: Boolean
)

private fun loggerSetupValues(settings: Settings) = LoggerSetupValues(
    settings.loggerDefinitionFilePath.orEmpty(),
    settings.loggerPort.orEmpty(), settings.loggerProtocol.orEmpty(),
    settings.transportProtocol.orEmpty(), settings.targetModule.orEmpty(),
    settings.loggerOutputDirPath.orEmpty(), settings.autoConnectOnStartup)

private fun applyLoggerSetup(settings: Settings, values: LoggerSetupValues) {
    settings.loggerDefinitionFilePath = values.definition
    settings.loggerPort = values.port
    settings.loggerProtocol = values.protocol
    settings.transportProtocol = values.transport
    settings.targetModule = values.target
    settings.loggerOutputDirPath = values.outputDirectory
    settings.autoConnectOnStartup = values.autoConnect
}

private fun launchEditorShell(startupFiles: List<File>) = application {
    val windowIcon = romRaiderWindowIcon()
    val controller = remember { EditorDocumentController() }
    var snapshot by remember { mutableStateOf(controller.session.snapshot()) }
    var status by remember { mutableStateOf("Ready") }
    var progress by remember { mutableStateOf(0) }
    var notice by remember { mutableStateOf<String?>(null) }
    var question by remember { mutableStateOf<ShellQuestion?>(null) }
    var closeRequest by remember { mutableStateOf<Rom?>(null) }
    var exitRequested by remember { mutableStateOf(false) }
    var definitionPrompt by remember {
        mutableStateOf(SettingsManager.getSettings().ecuDefinitionFiles.none {
            it.isFile && DefinitionFileSupport.isSupported(it)
        })
    }
    var requestedFiles by remember { mutableStateOf(startupFiles) }
    var loggerRuntime by remember { mutableStateOf<LoggerDesktopRuntime?>(null) }
    var loggerLoading by remember { mutableStateOf(false) }
    var comparisonOpen by remember { mutableStateOf(false) }
    var liveTuneOpen by remember { mutableStateOf(false) }
    var definitionManagerOpen by remember { mutableStateOf(false) }
    var userLevel by remember {
        mutableIntStateOf(SettingsManager.getSettings().userLevel)
    }
    var editorSettingsRevision by remember { mutableIntStateOf(0) }
    var recoverySnapshots by remember {
        mutableStateOf<List<RecoverySnapshot>>(emptyList())
    }

    fun openLogger() {
        if (loggerRuntime != null || loggerLoading) return
        loggerLoading = true
        status = "Opening Logger ..."
        CompletableFuture.supplyAsync { LoggerDesktopRuntime() }
            .whenComplete { runtime, error ->
                EventQueue.invokeLater {
                    loggerLoading = false
                    if (error == null) {
                        loggerRuntime = runtime
                        status = "Ready"
                    } else {
                        notice = rootMessage(error)
                        status = "Unable to open Logger"
                    }
                }
            }
    }

    val listener = remember {
        EditorDocumentSession.Listener { next ->
            EventQueue.invokeLater { snapshot = next }
        }
    }
    val loadInteraction = remember {
        ComposeLoadInteraction(
            progress = { text, percent ->
                EventQueue.invokeLater {
                    status = text
                    progress = percent
                }
            },
            notice = { text -> EventQueue.invokeLater { notice = text } },
            question = { next -> EventQueue.invokeLater { question = next } }
        )
    }
    val romInteraction = remember {
        ComposeRomInteraction(
            notice = { text -> EventQueue.invokeLater { notice = text } },
            question = { next -> EventQueue.invokeLater { question = next } },
            status = { text -> EventQueue.invokeLater { status = text } }
        )
    }
    val commandHandler = remember {
        DesktopApplicationCommands.Handler { arguments ->
            if (arguments.any { it.startsWith("-logger", ignoreCase = true) }) {
                EventQueue.invokeLater(::openLogger)
                true
            } else {
                val files = arguments.map(::File).filter(File::isFile)
                if (files.isNotEmpty()) {
                    EventQueue.invokeLater { requestedFiles = requestedFiles + files }
                }
                true
            }
        }
    }

    DisposableEffect(controller) {
        controller.session.addListener(listener)
        RomUserInteractionService.addHandler(romInteraction)
        DesktopApplicationCommands.register(commandHandler)
        onDispose {
            DesktopApplicationCommands.unregister(commandHandler)
            RomUserInteractionService.removeHandler(romInteraction)
            controller.session.removeListener(listener)
            loggerRuntime?.close()
            controller.close()
        }
    }

    LaunchedEffect(controller) {
        CompletableFuture.supplyAsync {
            controller.discoverRecoverySnapshots()
        }.whenComplete { recovered, error ->
            EventQueue.invokeLater {
                if (error == null) recoverySnapshots = recovered.orEmpty()
                else notice = "Unable to inspect recovery files: " +
                    rootMessage(error)
            }
        }
    }

    Window(
        onCloseRequest = {
            if (snapshot.documents.any(EditorDocument::isDirty)) {
                exitRequested = true
            } else {
                SettingsManager.save(SettingsManager.getSettings())
                exitApplication()
            }
        },
        title = editorTitle(snapshot),
        icon = windowIcon,
        state = rememberFittedWindowState(1360, 860)
    ) {
        fun openImages(files: List<File>) {
            files.forEach { file ->
                status = "Opening ${file.name} ..."
                progress = 0
                controller.open(file, loadInteraction).whenComplete { result, error ->
                    EventQueue.invokeLater {
                        if (error != null) {
                            notice = rootMessage(error)
                            status = "Unable to open ${file.name}"
                        } else if (result != null && result.isLoaded) {
                            status = if (result.totalChecksums == 0) "Ready"
                            else "${result.validChecksums}/${result.totalChecksums} " +
                                "checksums are correct"
                            progress = 100
                        } else {
                            status = "Ready"
                            progress = 0
                        }
                    }
                }
            }
        }

        fun chooseRoms() {
            chooseFiles(window, "Open ROM image", FileDialog.LOAD, true)
                .takeIf { it.isNotEmpty() }?.let(::openImages)
        }

        fun saveActive(saveAs: Boolean) {
            val rom = snapshot.activeRom ?: return
            val target = if (!saveAs) rom.fullFileName else null
            val selected = target ?: chooseFiles(
                window, "Save ROM image", FileDialog.SAVE, false
            ).firstOrNull()?.withRomSuffix(rom) ?: return
            if (selected.exists() && selected != target) {
                val answer = CompletableFuture<Boolean>()
                question = ShellQuestion(
                    "Replace existing file?",
                    "${selected.name} already exists. Replace it?",
                    "Replace", "Cancel", answer
                )
                answer.thenAccept { approved ->
                    if (approved) EventQueue.invokeLater {
                        performSave(controller, rom, selected,
                            { status = it }, { notice = it })
                    }
                }
            } else {
                performSave(controller, rom, selected,
                    { status = it }, { notice = it })
            }
        }

        fun saveAllAndExit() {
            val dirty = snapshot.documents.filter(EditorDocument::isDirty)
            status = "Saving ${dirty.size} ROM" +
                if (dirty.size == 1) " ..." else "s ..."
            val saves = dirty.map { document ->
                controller.save(document.rom, document.rom.fullFileName)
            }
            CompletableFuture.allOf(*saves.toTypedArray())
                .whenComplete { _, error ->
                    EventQueue.invokeLater {
                        if (error == null) {
                            SettingsManager.save(SettingsManager.getSettings())
                            exitApplication()
                        } else {
                            exitRequested = false
                            status = "Save failed"
                            notice = rootMessage(error)
                        }
                    }
                }
        }

        fun selectUserLevel(level: Int, name: String) {
            val settings = SettingsManager.getSettings()
            settings.userLevel = level
            SettingsManager.save(settings)
            userLevel = settings.userLevel
            editorSettingsRevision++
            status = "User level: $name"
        }

        fun openEditorSettings() {
            EventQueue.invokeLater {
                val form = SettingsForm()
                form.addWindowListener(object : WindowAdapter() {
                    override fun windowClosed(event: WindowEvent) {
                        userLevel = SettingsManager.getSettings().userLevel
                        editorSettingsRevision++
                    }
                })
                form.setLocationRelativeTo(window)
                form.isVisible = true
            }
        }

        MenuBar {
            Menu("File") {
                Item("Open ROM...", onClick = ::chooseRoms)
                Item("Save", onClick = { saveActive(false) },
                    enabled = snapshot.activeRom != null)
                Item("Save As...", onClick = { saveActive(true) },
                    enabled = snapshot.activeRom != null)
                Separator()
                Item("Close ROM", onClick = {
                    snapshot.activeRom?.let { rom ->
                        if (snapshot.activeDocument?.isDirty == true) {
                            closeRequest = rom
                        } else controller.closeRom(rom)
                    }
                }, enabled = snapshot.activeRom != null)
                Item("Exit", onClick = {
                    if (snapshot.documents.any(EditorDocument::isDirty)) {
                        exitRequested = true
                    } else {
                        SettingsManager.save(SettingsManager.getSettings())
                        exitApplication()
                    }
                })
            }
            Menu("Edit") {
                Item("Undo", onClick = {
                    runCatching { controller.session.undo() }
                        .onFailure { notice = rootMessage(it) }
                }, enabled = snapshot.activeDocument?.canUndo() == true)
                Item("Redo", onClick = {
                    runCatching { controller.session.redo() }
                        .onFailure { notice = rootMessage(it) }
                }, enabled = snapshot.activeDocument?.canRedo() == true)
            }
            if (!RuntimeUiProfile.isSteamOs()) {
                Menu("View") {
                    Item("Light theme", onClick = {
                        applyTheme(ThemeMode.LIGHT)
                    })
                    Item("Dark theme", onClick = {
                        applyTheme(ThemeMode.DARK)
                    })
                    Item("High contrast", onClick = {
                        applyTheme(ThemeMode.HIGH_CONTRAST)
                    })
                }
            }
            Menu("User Level") {
                editorUserLevels.forEach { (level, name) ->
                    Item(userLevelMenuLabel(level, name, userLevel),
                        onClick = { selectUserLevel(level, name) })
                }
            }
            Menu("Settings") {
                Item("Editor Settings...", onClick = ::openEditorSettings)
            }
            Menu("Tools") {
                Item("Compare open ROMs", onClick = {
                    comparisonOpen = true
                }, enabled = snapshot.documents.size >= 2)
                Item("Live Tune preview", onClick = {
                    liveTuneOpen = true
                }, enabled = snapshot.activeRom != null)
            }
            Menu("Logger") {
                Item(if (loggerLoading) "Opening Logger..." else "Open Logger",
                    onClick = ::openLogger,
                    enabled = loggerRuntime == null && !loggerLoading)
            }
        }

        ComposeEditorShell(snapshot, controller, status, progress, userLevel,
            editorSettingsRevision,
            ::chooseRoms, { saveActive(false) }, { saveActive(true) },
            manageDefinitions = { definitionManagerOpen = true },
            compareRoms = { comparisonOpen = true },
            openLiveTune = { liveTuneOpen = true },
            openLogger = ::openLogger,
            loggerLoading = loggerLoading,
            loggerOpen = loggerRuntime != null)

        LaunchedEffect(requestedFiles) {
            if (requestedFiles.isNotEmpty()) {
                val next = requestedFiles
                requestedFiles = emptyList()
                openImages(next)
            }
        }

        if (definitionPrompt) {
            ShellAlertDialog(
                onDismissRequest = { definitionPrompt = false },
                title = { Text("ECU definitions") },
                text = {
                    Text("No ECU definition files are configured. RomRaider2 " +
                        "needs an exact definition match before opening a ROM.")
                },
                confirmButton = {
                    Button(onClick = {
                        definitionPrompt = false
                        addDefinitions(window, SettingsManager.getSettings(),
                            { status = it }, { notice = it })
                    }) { Text("Add definitions") }
                },
                dismissButton = {
                    TextButton(onClick = { definitionPrompt = false }) {
                        Text("Not now")
                    }
                }
            )
        }

        if (!definitionPrompt && recoverySnapshots.isNotEmpty()) {
            val recovery = recoverySnapshots.first()
            ShellAlertDialog(
                onDismissRequest = {
                    recoverySnapshots = recoverySnapshots.drop(1)
                },
                title = { Text("Recover unsaved ROM") },
                text = {
                    Text("RomRaider2 found recoverable work from an abnormal " +
                        "exit.\n\n${recovery.sourceName}\n" +
                        "${recovery.sourcePath.ifBlank {
                            "Original location unavailable"
                        }}\n${recovery.changedCells} changed " +
                        (if (recovery.changedCells == 1) "cell" else "cells") +
                        "\n\nRestore opens an unsaved workspace and does not " +
                        "overwrite the original ROM.")
                },
                confirmButton = {
                    Button(onClick = {
                        recoverySnapshots = recoverySnapshots.drop(1)
                        status = "Restoring ${recovery.sourceName} ..."
                        controller.openRecovered(recovery, loadInteraction)
                            .whenComplete { result, error ->
                                EventQueue.invokeLater {
                                    if (error != null) {
                                        notice = rootMessage(error)
                                        status = "Recovery failed"
                                    } else if (result?.isLoaded == true) {
                                        status = "Recovered ROM opened as unsaved"
                                    } else status = "Recovery was not opened"
                                }
                            }
                    }) { Text("Restore as unsaved") }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            runCatching { controller.discardRecovery(recovery) }
                                .onSuccess {
                                    recoverySnapshots =
                                        recoverySnapshots.drop(1)
                                }
                                .onFailure { notice = rootMessage(it) }
                        }) { Text("Discard recovery") }
                        TextButton(onClick = {
                            recoverySnapshots = recoverySnapshots.drop(1)
                        }) { Text("Keep for later") }
                    }
                }
            )
        }

        question?.let { active ->
            ShellAlertDialog(
                onDismissRequest = {
                    active.answer.complete(false)
                    question = null
                },
                title = { Text(active.title) },
                text = { Text(active.message) },
                confirmButton = {
                    Button(onClick = {
                        active.answer.complete(true)
                        question = null
                    }) { Text(active.approve) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        active.answer.complete(false)
                        question = null
                    }) { Text(active.reject) }
                }
            )
        }

        notice?.let { message ->
            ShellAlertDialog(
                onDismissRequest = { notice = null },
                title = { Text("RomRaider2") },
                text = { Text(message) },
                confirmButton = {
                    Button(onClick = { notice = null }) { Text("OK") }
                }
            )
        }

        closeRequest?.let { rom ->
            ShellAlertDialog(
                onDismissRequest = { closeRequest = null },
                title = { Text("Unsaved ROM changes") },
                text = { Text("${rom.fileName} has unsaved changes.") },
                confirmButton = {
                    Button(onClick = {
                        closeRequest = null
                        performSave(controller, rom, rom.fullFileName,
                            { status = it }, { notice = it }) {
                            controller.closeRom(rom)
                        }
                    }) { Text("Save") }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            closeRequest = null
                            controller.closeRom(rom)
                        }) { Text("Discard") }
                        TextButton(onClick = { closeRequest = null }) {
                            Text("Cancel")
                        }
                    }
                }
            )
        }

        if (exitRequested) {
            ShellAlertDialog(
                onDismissRequest = { exitRequested = false },
                title = { Text("Unsaved ROM changes") },
                text = {
                    Text("One or more ROMs have unsaved changes. Exit and " +
                        "discard those changes?")
                },
                confirmButton = {
                    Button(onClick = ::saveAllAndExit) {
                        Text("Save all and exit")
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            SettingsManager.save(SettingsManager.getSettings())
                            exitApplication()
                        }) { Text("Discard and exit") }
                        TextButton(onClick = { exitRequested = false }) {
                            Text("Cancel")
                        }
                    }
                }
            )
        }
    }

    loggerRuntime?.let { active ->
        LoggerDesktopWindow(active) {
            active.close()
            loggerRuntime = null
        }
    }
    if (comparisonOpen) {
        val roms = snapshot.documents.map { it.rom }
        Window(
            onCloseRequest = { comparisonOpen = false },
            title = "${Version.PRODUCT_NAME} ${Version.VERSION} | Compare ROMs",
            icon = windowIcon,
            state = rememberFittedWindowState(1060, 720)
        ) {
            RomComparisonWorkspace(RomComparisonWorkspaceContext(
                roms
            ) { _, right, comparison ->
                right.tableCatalog.firstOrNull {
                    it.name == comparison.tableName
                }?.let {
                    controller.activateRom(right)
                    controller.openTable(right, it)
                }
            })
        }
    }
    if (liveTuneOpen && snapshot.activeRom != null) {
        val rom = snapshot.activeRom!!
        Window(
            onCloseRequest = { liveTuneOpen = false },
            title = "${Version.PRODUCT_NAME} ${Version.VERSION} | Live Tune Preview",
            icon = windowIcon,
            state = rememberFittedWindowState(1120, 720)
        ) {
            LiveTunePreviewWorkspace(rom, snapshot.activeTable,
                snapshot.revision)
        }
    }
    if (definitionManagerOpen) {
        DefinitionManagerWindow(
            onClose = { definitionManagerOpen = false },
            onSaved = { count ->
                status = "$count ECU definition" +
                    if (count == 1) " configured" else "s configured"
                definitionManagerOpen = false
            },
            onError = { notice = it }
        )
    }
}

@Composable
private fun DefinitionManagerWindow(
    onClose: () -> Unit,
    onSaved: (Int) -> Unit,
    onError: (String) -> Unit
) {
    val windowIcon = romRaiderWindowIcon()
    val settings = SettingsManager.getSettings()
    var files by remember {
        mutableStateOf(settings.ecuDefinitionFiles.toList())
    }
    var selected by remember { mutableStateOf<File?>(files.firstOrNull()) }
    var filter by remember { mutableStateOf("") }
    Window(
        onCloseRequest = onClose,
        title = "${Version.PRODUCT_NAME} ${Version.VERSION} | ECU Definitions",
        icon = windowIcon,
        state = rememberFittedWindowState(940, 650)
    ) {
        val dark = rememberApplicationDarkTheme()
        MaterialTheme(colors = if (dark) darkColors(
            primary = shellAccent, background = shellGraphite,
            surface = shellSurface, onPrimary = Color.White,
            onBackground = shellText, onSurface = shellText
        ) else lightColors(primary = Color(0xFF007F78))) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
                Column(Modifier.fillMaxSize().padding(18.dp)) {
                    Text("ECU definition priority", fontSize = 22.sp,
                        fontWeight = FontWeight.Bold)
                    Text("Files are loaded from top to bottom. Higher entries " +
                        "take priority when definitions overlap.",
                        color = MaterialTheme.colors.onSurface.copy(.62f),
                        fontSize = 12.sp)
                    Spacer(Modifier.height(13.dp))
                    OutlinedTextField(filter, { filter = it },
                        label = { Text("Search definitions") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    val visible = files.filter { file ->
                        filter.isBlank() || file.path.contains(filter,
                            ignoreCase = true)
                    }
                    LazyColumn(Modifier.weight(1f).fillMaxWidth()
                        .background(MaterialTheme.colors.surface,
                            RoundedCornerShape(8.dp))
                        .padding(7.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        if (visible.isEmpty()) {
                            item {
                                Text(if (files.isEmpty())
                                    "No ECU definitions are configured."
                                else "No definitions match the search.",
                                    Modifier.padding(14.dp),
                                    color = MaterialTheme.colors.onSurface.copy(.58f))
                            }
                        }
                        itemsIndexed(visible, key = { _, file -> file.absolutePath }) {
                                _, file ->
                            val priority = files.indexOf(file) + 1
                            val active = selected == file
                            Row(Modifier.fillMaxWidth().clickable {
                                selected = file
                            }.background(if (active)
                                MaterialTheme.colors.primary.copy(.14f)
                            else Color.Transparent, RoundedCornerShape(6.dp))
                                .border(1.dp, if (active)
                                    MaterialTheme.colors.primary.copy(.55f)
                                else MaterialTheme.colors.onSurface.copy(.08f),
                                    RoundedCornerShape(6.dp))
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(priority.toString(), Modifier.width(34.dp),
                                    color = MaterialTheme.colors.primary,
                                    fontWeight = FontWeight.Bold)
                                Column(Modifier.weight(1f)) {
                                    Text(file.name, fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis)
                                    Text(file.absolutePath, fontSize = 10.sp,
                                        color = MaterialTheme.colors.onSurface.copy(.55f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis)
                                }
                                val supported = DefinitionFileSupport.isSupported(file)
                                val state = when {
                                    !file.exists() -> "MISSING"
                                    !supported -> "UNSUPPORTED"
                                    else -> "READY"
                                }
                                Text(state,
                                    color = if (file.exists() && supported)
                                        shellAccent else Color(0xFFD92632),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = {
                            chooseFiles(window, "Add ECU definitions",
                                FileDialog.LOAD, true,
                                selectionDescription =
                                    "ECU definitions (.xml, .xdf, .vdf, .jdf, .Cxx)",
                                selectionFilter =
                                    DefinitionFileSupport::isSupported)
                                .filter(File::isFile)
                                .forEach { file ->
                                    if (files.none { it.absoluteFile ==
                                            file.absoluteFile }) files = files + file
                                    selected = file
                                }
                        }) { Text("Add") }
                        Button(onClick = {
                            val active = selected ?: return@Button
                            files = files - active
                            selected = files.firstOrNull()
                        }, enabled = selected != null) { Text("Remove") }
                        Spacer(Modifier.width(7.dp))
                        Button(onClick = {
                            files = moveDefinition(files, selected, -1)
                        }, enabled = selected != null && files.indexOf(selected) > 0) {
                            Text("Move up")
                        }
                        Button(onClick = {
                            files = moveDefinition(files, selected, 1)
                        }, enabled = selected != null &&
                            files.indexOf(selected) in 0 until files.lastIndex) {
                            Text("Move down")
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onClose) { Text("Cancel") }
                        Button(onClick = {
                            runCatching {
                                require(files.all(DefinitionFileSupport::isSupported)) {
                                    "Remove unsupported definition files before saving."
                                }
                                settings.ecuDefinitionFiles = Vector(files)
                                files.firstOrNull()?.parentFile
                                    ?.let(settings::setLastDefinitionDir)
                                SettingsManager.save(settings)
                            }.onSuccess { onSaved(files.size) }
                                .onFailure { onError(rootMessage(it)) }
                        }) { Text("Save") }
                    }
                }
            }
        }
    }
}

private fun moveDefinition(
    files: List<File>,
    selected: File?,
    offset: Int
): List<File> {
    val source = files.indexOf(selected)
    val target = source + offset
    if (source < 0 || target !in files.indices) return files
    return files.toMutableList().also { values ->
        val value = values.removeAt(source)
        values.add(target, value)
    }
}

@Composable
internal fun ComposeEditorShell(
    snapshot: EditorDocumentSnapshot,
    controller: EditorDocumentController,
    status: String,
    progress: Int,
    userLevel: Int,
    editorSettingsRevision: Int,
    open: () -> Unit,
    saveNow: () -> Unit,
    saveAs: () -> Unit,
    manageDefinitions: () -> Unit,
    compareRoms: () -> Unit,
    openLiveTune: () -> Unit,
    openLogger: () -> Unit,
    loggerLoading: Boolean,
    loggerOpen: Boolean
) {
    val dark = rememberApplicationDarkTheme()
    val colors = if (dark) darkColors(
        primary = shellAccent, background = shellGraphite,
        surface = shellSurface, onPrimary = Color.White,
        onBackground = shellText, onSurface = shellText
    ) else lightColors(
        primary = Color(0xFF007F78), background = Color(0xFFF2F5F8),
        surface = Color.White, onPrimary = Color.White,
        onBackground = Color(0xFF1C2228), onSurface = Color(0xFF1C2228)
    )
    val navigation = remember(controller) {
        EditorNavigationWorkspaceContext(controller.session) { location ->
            controller.session.snapshot().documents.firstOrNull {
                com.romraider.editor.workspace.EditorWorkspaceService
                    .romIdentity(it.rom) == location.romId
            }?.rom?.let { rom ->
                rom.tableCatalog.firstOrNull { it.name == location.tableName }
                    ?.let { controller.openTable(rom, it) }
            }
        }
    }

    MaterialTheme(colors = colors) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
            Column(Modifier.fillMaxSize()) {
                ShellToolbar(snapshot, open, saveNow, saveAs,
                    manageDefinitions)
                EditorCommandDeck(snapshot, userLevel, compareRoms,
                    openLiveTune, openLogger, loggerLoading, loggerOpen)
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    Box(Modifier.width(292.dp).fillMaxHeight()) {
                        EditorNavigationSurface(navigation,
                            snapshot.revision.toInt(), 0,
                            userLevel * 31 + editorSettingsRevision)
                    }
                    Spacer(Modifier.width(1.dp).fillMaxHeight()
                        .background(MaterialTheme.colors.onSurface.copy(
                            alpha = .12f)))
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        DocumentTabs(snapshot, controller)
                        ActiveDocument(snapshot, open)
                    }
                }
                ShellStatus(status, progress, snapshot)
            }
        }
    }
}

private val editorUserLevels = listOf(
    1 to "Beginner",
    2 to "Intermediate",
    3 to "Advanced",
    4 to "Highest",
    5 to "Debug Mode"
)

internal fun userLevelMenuLabel(
    level: Int,
    name: String,
    selectedLevel: Int
): String = (if (level == selectedLevel) "✓ " else "") + "$level $name"

@Composable
private fun ShellAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null
) {
    val dark = rememberApplicationDarkTheme()
    MaterialTheme(colors = if (dark) darkColors(
        primary = shellAccent, background = shellGraphite,
        surface = shellSurface, onPrimary = Color.White,
        onBackground = shellText, onSurface = shellText
    ) else lightColors(
        primary = Color(0xFF007F78), background = Color(0xFFF2F5F8),
        surface = Color.White, onPrimary = Color.White,
        onBackground = Color(0xFF1C2228), onSurface = Color(0xFF1C2228)
    )) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = title,
            text = text,
            confirmButton = confirmButton,
            dismissButton = dismissButton
        )
    }
}

@Composable
private fun ShellToolbar(
    snapshot: EditorDocumentSnapshot,
    open: () -> Unit,
    saveNow: () -> Unit,
    saveAs: () -> Unit,
    manageDefinitions: () -> Unit
) {
    var saveMenuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(72.dp).background(shellGraphite)
            .border(0.dp, Color.Transparent)
            .padding(horizontal = 15.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.width(164.dp)) {
            Image(romRaiderHorizontalLogo(), "RomRaider2",
                Modifier.fillMaxWidth().height(40.dp),
                contentScale = ContentScale.Fit)
            Text("ECU CALIBRATION STUDIO", color = shellAccent,
                fontWeight = FontWeight.Bold, fontSize = 8.sp,
                letterSpacing = 1.15.sp)
        }
        Spacer(Modifier.width(4.dp))
        Box(Modifier.width(1.dp).height(42.dp)
            .background(Color.White.copy(alpha = .16f)))
        Spacer(Modifier.width(4.dp))
        Button(open) { Text("Open ROM") }
        Box {
            Button(onClick = { saveMenuOpen = true },
                enabled = snapshot.activeRom != null) {
                Text("Save As \u25be")
            }
            DropdownMenu(expanded = saveMenuOpen,
                onDismissRequest = { saveMenuOpen = false }) {
                DropdownMenuItem(onClick = {
                    saveMenuOpen = false
                    saveNow()
                }) { Text("Save Now") }
                DropdownMenuItem(onClick = {
                    saveMenuOpen = false
                    saveAs()
                }) { Text("Save As…") }
            }
        }
        TextButton(manageDefinitions,
            colors = ButtonDefaults.textButtonColors(contentColor = shellAccent)) {
            Text("Definitions Manager")
        }
        Spacer(Modifier.weight(1f))
        Column(Modifier.width(220.dp), horizontalAlignment = Alignment.End) {
            Text(if (snapshot.activeRom == null) "WORKSPACE"
                else "ACTIVE ROM", color = shellAccent, fontSize = 8.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(snapshot.activeRom?.fileName ?: "No ROM open",
                color = Color.White.copy(alpha = .86f), fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("RomRaider2 ${Version.VERSION}",
                color = Color.White.copy(alpha = .46f), fontSize = 8.sp)
        }
    }
}

@Composable
private fun EditorCommandDeck(
    snapshot: EditorDocumentSnapshot,
    userLevel: Int,
    compareRoms: () -> Unit,
    openLiveTune: () -> Unit,
    openLogger: () -> Unit,
    loggerLoading: Boolean,
    loggerOpen: Boolean
) {
    val dirtyCount = snapshot.documents.count(EditorDocument::isDirty)
    Row(Modifier.fillMaxWidth().height(42.dp)
        .background(MaterialTheme.colors.surface)
        .border(1.dp, MaterialTheme.colors.onSurface.copy(alpha = .08f))
        .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("ROM WORKSPACE", color = MaterialTheme.colors.primary,
            fontSize = 9.sp, fontWeight = FontWeight.Bold,
            letterSpacing = .85.sp)
        WorkspaceMetric("${snapshot.documents.size} OPEN")
        if (dirtyCount > 0) WorkspaceMetric("$dirtyCount UNSAVED", true)
        WorkspaceMetric("LEVEL $userLevel · ${editorUserLevels
            .firstOrNull { it.first == userLevel }?.second?.uppercase()
            ?: "CUSTOM"}")
        Spacer(Modifier.weight(1f))
        DeckAction("Compare ROMs", snapshot.documents.size >= 2, compareRoms)
        DeckAction("Live Tune", snapshot.activeRom != null, openLiveTune)
        DeckAction(if (loggerLoading) "Opening Logger…"
            else if (loggerOpen) "Logger Open" else "Logger",
            !loggerLoading && !loggerOpen, openLogger)
    }
}

@Composable
private fun WorkspaceMetric(label: String, alert: Boolean = false) {
    Text(label, color = if (alert) Color(0xFFD92632)
        else MaterialTheme.colors.onSurface.copy(alpha = .6f),
        fontSize = 8.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.background(MaterialTheme.colors.onSurface.copy(
            alpha = .055f), RoundedCornerShape(4.dp))
            .padding(horizontal = 7.dp, vertical = 4.dp))
}

@Composable
private fun DeckAction(label: String, enabled: Boolean, action: () -> Unit) {
    OutlinedButton(action, enabled = enabled, modifier = Modifier.height(30.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 10.dp, vertical = 1.dp)) {
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DocumentTabs(
    snapshot: EditorDocumentSnapshot,
    controller: EditorDocumentController
) {
    if (snapshot.documents.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().height(44.dp)
            .background(MaterialTheme.colors.surface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        snapshot.documents.forEach { document ->
            val active = document.rom === snapshot.activeRom
            Text(
                document.name + if (document.isDirty) "  •" else "",
                color = if (active) MaterialTheme.colors.primary
                    else MaterialTheme.colors.onSurface,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                modifier = Modifier
                    .background(if (active)
                        MaterialTheme.colors.primary.copy(alpha = .14f)
                    else Color.Transparent,
                        RoundedCornerShape(5.dp))
                    .clickable { controller.activateRom(document.rom) }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }
    }
}

@Composable
private fun ActiveDocument(snapshot: EditorDocumentSnapshot, open: () -> Unit) {
    val table = snapshot.activeTable
    if (table == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(Modifier.width(520.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                if (snapshot.activeRom == null) {
                    Image(romRaiderHorizontalLogo(), "RomRaider2",
                        Modifier.width(310.dp).height(104.dp),
                        contentScale = ContentScale.Fit)
                    Text("ECU CALIBRATION STUDIO",
                        color = MaterialTheme.colors.primary,
                        fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp)
                    Spacer(Modifier.height(22.dp))
                    Text("Professional ROM calibration workspace",
                        fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Load a ROM to inspect maps, compare revisions, " +
                        "manage changes, and prepare a calibrated image.",
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colors.onSurface.copy(alpha = .64f))
                    Spacer(Modifier.height(20.dp))
                    Button(open) { Text("Open ROM") }
                } else {
                    Text("ROM LOADED", color = MaterialTheme.colors.primary,
                        fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Choose a calibration from the map catalog",
                        fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(snapshot.activeRom.fileName,
                        color = MaterialTheme.colors.onSurface.copy(alpha = .62f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf("TABLE", "GRAPH", "3D SURFACE", "CHANGE HISTORY")
                            .forEach { WorkspaceMetric(it) }
                    }
                }
            }
        }
        return
    }
    val editController = remember(table) {
        TableCalibrationEditController(table)
    }
    DisposableEffect(editController) {
        onDispose { editController.close() }
    }
    CalibrationWorkspace(CalibrationWorkspaceContext(
        CalibrationGridProjectionService.project(table), editController,
        snapshot.activeRom, table))
}

@Composable
private fun ShellStatus(
    status: String,
    progress: Int,
    snapshot: EditorDocumentSnapshot
) {
    Row(
        Modifier.fillMaxWidth().height(32.dp)
            .background(MaterialTheme.colors.surface)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(status, fontSize = 11.sp,
            color = MaterialTheme.colors.onSurface.copy(alpha = .62f))
        Spacer(Modifier.weight(1f))
        if (progress in 1..99) {
            Text("$progress%", fontSize = 11.sp,
                color = MaterialTheme.colors.primary)
            Spacer(Modifier.width(12.dp))
        }
        Text("${snapshot.documents.size} ROM" +
            if (snapshot.documents.size == 1) "" else "s",
            fontSize = 11.sp,
            color = MaterialTheme.colors.onSurface.copy(alpha = .62f))
    }
}

private class ComposeLoadInteraction(
    private val progress: (String, Int) -> Unit,
    private val notice: (String) -> Unit,
    private val question: (ShellQuestion) -> Unit
) : RomLoadInteraction {
    override fun update(status: String, percent: Int) =
        progress(status, percent.coerceIn(0, 100))

    override fun missingDefinition(definition: File?) {
        notice("ECU definition file is missing or moved:\n" +
            (definition?.absolutePath ?: "(not set)"))
    }

    override fun definitionLoadFailed(
        definition: File?, message: String, failure: Throwable?
    ) {
        notice("${definition?.name ?: "Definition"}: $message")
    }

    override fun chooseDefinition(image: File): File? {
        var selected: File? = null
        val choose = Runnable {
            selected = chooseFiles(null, "Choose ECU definition",
                FileDialog.LOAD, false,
                selectionDescription =
                    "ECU definitions (.xml, .xdf, .vdf, .jdf, .Cxx)",
                selectionFilter =
                    DefinitionFileSupport::isSupported).firstOrNull()
        }
        if (EventQueue.isDispatchThread()) choose.run()
        else EventQueue.invokeAndWait(choose)
        return selected
    }

    override fun confirmForceLoad(definition: File): Boolean {
        val answer = CompletableFuture<Boolean>()
        question(ShellQuestion(
            "Definition does not match",
            "${definition.name} does not appear to match this ROM. " +
                "Load its first ROM definition anyway?",
            "Load anyway", "Cancel", answer
        ))
        return answer.get()
    }
}

private class ComposeRomInteraction(
    private val notice: (String) -> Unit,
    private val question: (ShellQuestion) -> Unit,
    private val status: (String) -> Unit
) : RomUserInteraction {
    override fun definitionError(
        rom: Rom?, table: Table?, title: String, message: String,
        failure: Throwable?
    ) = notice("$title\n\n$message")

    override fun confirmChecksumFix(
        rom: Rom?, table: Table?, title: String, message: String
    ): Boolean {
        val answer = CompletableFuture<Boolean>()
        question(ShellQuestion(title, message, "Fix checksum", "Cancel", answer))
        return answer.get()
    }

    override fun checksumValidationFailed(
        rom: Rom?, title: String, message: String
    ) = notice("$title\n\n$message")

    override fun checksumUpdated(rom: Rom?, message: String) = status(message)
}

private fun performSave(
    controller: EditorDocumentController,
    rom: Rom,
    target: File,
    status: (String) -> Unit,
    notice: (String) -> Unit,
    onSaved: () -> Unit = {}
) {
    status("Saving ${target.name} ...")
    controller.save(rom, target).whenComplete { _, error ->
        EventQueue.invokeLater {
            if (error == null) {
                status("Saved ${target.name}")
                onSaved()
            }
            else {
                status("Save failed")
                notice(rootMessage(error))
            }
        }
    }
}

private fun addDefinitions(
    owner: java.awt.Window?,
    settings: Settings,
    status: (String) -> Unit,
    notice: (String) -> Unit
) {
    val files = chooseFiles(owner, "Add ECU definitions", FileDialog.LOAD, true,
        selectionDescription =
            "ECU definitions (.xml, .xdf, .vdf, .jdf, .Cxx)",
        selectionFilter = DefinitionFileSupport::isSupported)
    if (files.isEmpty()) return
    files.filter(File::isFile).forEach(settings::addEcuDefinitionFile)
    files.firstOrNull()?.parentFile?.let(settings::setLastDefinitionDir)
    runCatching { SettingsManager.save(settings) }
        .onSuccess {
            status("${files.size} definition" +
                if (files.size == 1) " added" else "s added")
        }
        .onFailure { notice(rootMessage(it)) }
}

private fun applyTheme(mode: ThemeMode) {
    val settings = SettingsManager.getSettings()
    settings.themeMode = mode
    ApplicationThemeService.getInstance().apply(mode)
    SettingsManager.save(settings)
}

private fun chooseFiles(
    owner: java.awt.Window?,
    title: String,
    mode: Int,
    multiple: Boolean,
    initialSelection: File? = null,
    selectionDescription: String = "Supported files",
    selectionFilter: ((File) -> Boolean)? = null
): List<File> {
    val chooser = IntegratedFileChooser()
    chooser.dialogTitle = title
    chooser.isMultiSelectionEnabled = multiple
    chooser.fileSelectionMode = JFileChooser.FILES_ONLY
    initializeChooser(chooser, initialSelection)
    if (selectionFilter != null) {
        chooser.fileFilter = object : FileFilter() {
            override fun accept(file: File): Boolean =
                file.isDirectory || selectionFilter(file)

            override fun getDescription(): String = selectionDescription
        }
    }
    val result = if (mode == FileDialog.SAVE) {
        chooser.showSaveDialog(owner)
    } else {
        chooser.showOpenDialog(owner)
    }
    if (result != JFileChooser.APPROVE_OPTION) return emptyList()
    val files = if (multiple) chooser.selectedFiles.toList()
        else listOfNotNull(chooser.selectedFile)
    return if (selectionFilter == null) files else files.filter(selectionFilter)
}

private fun chooseDirectory(
    owner: java.awt.Window?,
    title: String,
    initialSelection: File? = null
): File? {
    val chooser = IntegratedFileChooser()
    chooser.dialogTitle = title
    chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    initializeChooser(chooser, initialSelection)
    return if (chooser.showOpenDialog(owner) == JFileChooser.APPROVE_OPTION)
        chooser.selectedFile?.takeIf(File::isDirectory) else null
}

private fun initializeChooser(
    chooser: IntegratedFileChooser,
    initialSelection: File?
) {
    val selected = initialSelection?.absoluteFile ?: return
    val directory = if (selected.isDirectory) selected else selected.parentFile
    if (directory?.isDirectory == true) chooser.currentDirectory = directory
    chooser.selectedFile = selected
}

private fun File.withRomSuffix(rom: Rom): File {
    if (name.contains('.')) return this
    val suffix = if (rom.fileName?.lowercase()?.endsWith(".hex") == true)
        ".hex" else ".bin"
    return File(parentFile, name + suffix)
}

private fun editorTitle(snapshot: EditorDocumentSnapshot): String {
    val base = "${Version.PRODUCT_NAME} ${Version.VERSION} | ECU Studio"
    return snapshot.activeRom?.fileName?.let { "$base — $it" } ?: base
}

private fun rootMessage(failure: Throwable): String {
    var current = failure
    while (current.cause != null && current.cause !== current) {
        current = current.cause!!
    }
    return current.message?.takeIf(String::isNotBlank)
        ?: current.javaClass.simpleName
}
