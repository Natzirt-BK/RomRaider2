/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.logger.compose

import com.romraider.logger.api.LiveDataSample
import com.romraider.logger.api.LoggerChannel
import com.romraider.logger.api.LoggerChannelKind
import com.romraider.logger.api.LoggerChannelService
import com.romraider.logger.api.LoggerLiveDataBus
import com.romraider.logger.api.LoggerSessionService
import com.romraider.logger.api.LoggerWorkspacePreferences
import com.romraider.logger.api.LoggerWorkspaceView
import com.romraider.logger.ecu.ui.spi.LoggerWorkspaceContext
import java.awt.BorderLayout
import java.util.concurrent.CountDownLatch
import javax.swing.JFrame
import javax.swing.SwingUtilities
import javax.swing.WindowConstants
import kotlin.math.cos
import kotlin.math.sin

/** Manual screenshot fixture. It is never included in release packages. */
fun main(args: Array<String>) {
    val requestedView = args.firstOrNull()
        ?.let(LoggerWorkspaceView::fromName) ?: LoggerWorkspaceView.OVERVIEW
    val darkTheme = args.drop(1).firstOrNull()
        ?.equals("dark", ignoreCase = true) == true
    val windowWidth = args.getOrNull(2)?.toIntOrNull()?.coerceAtLeast(640)
        ?: 1320
    val windowHeight = args.getOrNull(3)?.toIntOrNull()?.coerceAtLeast(560)
        ?: 860
    val bus = LoggerLiveDataBus.getInstance()
    bus.clearSamples()
    val channels = listOf(
        channel("P-RPM", "Engine Speed", "rpm"),
        channel("P-BOOST", "Manifold Relative Pressure", "psi"),
        channel("P-AFR", "Air/Fuel Ratio", "AFR"),
        channel("P-THROTTLE", "Throttle Opening Angle", "%"),
        channel("P-COOLANT", "Engine Coolant Temperature", "°F"),
        channel("P-IGN", "Ignition Total Timing", "°"),
        LoggerChannel("S-CLUTCH", "Clutch Switch", "",
            LoggerChannelKind.SWITCH, true),
        LoggerChannel("E-WIDEBAND", "External Wideband", "lambda",
            LoggerChannelKind.EXTERNAL, false)
    )
    lateinit var channelService: LoggerChannelService
    channelService = LoggerChannelService(
        { id, selected ->
            channelService.replaceChannels(channels.map {
                if (it.parameterId == id) it.withSelected(selected) else it
            })
        }, { throw it })
    channelService.replaceChannels(channels)
    val session = LoggerSessionService(
        bus, { bus.connecting() }, { bus.stopped() },
        { bus.loggingData() }, { bus.readingData() }, { throw it })

    val now = System.currentTimeMillis()
    repeat(140) { index ->
        val phase = index / 9.0
        publish(bus, "P-RPM", "Engine Speed", 2650 + sin(phase) * 850,
            "rpm", now + index * 80)
        publish(bus, "P-BOOST", "Manifold Relative Pressure",
            7.8 + sin(phase + .8) * 4.2, "psi", now + index * 80)
        publish(bus, "P-AFR", "Air/Fuel Ratio",
            12.2 + cos(phase * .7) * 1.4, "AFR", now + index * 80)
        publish(bus, "P-THROTTLE", "Throttle Opening Angle",
            42 + sin(phase * .45) * 34, "%", now + index * 80)
        publish(bus, "P-COOLANT", "Engine Coolant Temperature",
            188 + sin(phase * .08) * 4, "°F", now + index * 80)
        publish(bus, "P-IGN", "Ignition Total Timing",
            20 + cos(phase * .6) * 7, "°", now + index * 80)
        publish(bus, "S-CLUTCH", "Clutch Switch",
            if (index % 35 < 5) 1.0 else 0.0, "", now + index * 80)
    }
    bus.readingData()

    SwingUtilities.invokeLater {
        JFrame("RomRaider2 Logger Workspace — Visual Fixture").apply {
            defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
            layout = BorderLayout()
            add(ComposeLoggerWorkspaceProvider().createWorkspace(
                LoggerWorkspaceContext(
                    bus, session, channelService,
                    LoggerWorkspacePreferences(
                        requestedView, darkTheme
                    ) { _, _ -> }
                )),
                BorderLayout.CENTER)
            setSize(windowWidth, windowHeight)
            setLocation(40, 40)
            isVisible = true
        }
    }
    CountDownLatch(1).await()
}

private fun channel(id: String, name: String, units: String) =
    LoggerChannel(id, name, units, LoggerChannelKind.PARAMETER, true)

private fun publish(
    bus: LoggerLiveDataBus,
    id: String,
    name: String,
    value: Double,
    units: String,
    timestamp: Long
) {
    bus.publish(LiveDataSample(id, name, value, value.formatFixture(), units,
        timestamp))
}

private fun Double.formatFixture(): String = when {
    kotlin.math.abs(this) >= 100 -> "%.0f".format(this)
    else -> "%.1f".format(this)
}
