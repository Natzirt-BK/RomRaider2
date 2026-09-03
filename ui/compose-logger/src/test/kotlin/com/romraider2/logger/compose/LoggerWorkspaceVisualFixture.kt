/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.logger.compose

import com.romraider.logger.api.LiveDataSample
import com.romraider.logger.api.LoggerChannel
import com.romraider.logger.api.LoggerChannelKind
import com.romraider.logger.api.LoggerChannelService
import com.romraider.logger.api.LoggerChannelUnitOption
import com.romraider.logger.api.LoggerLiveDataBus
import com.romraider.logger.api.LoggerSessionService
import com.romraider.logger.api.LoggerWorkspacePreferences
import com.romraider.logger.api.LoggerWorkspaceView
import com.romraider.logger.api.LoggerDashboardTile
import com.romraider.logger.api.LoggerDashboardTileRole
import com.romraider.logger.api.LoggerDashboardTileSize
import com.romraider.logger.api.LoggerGaugeConfiguration
import com.romraider.logger.ecu.ui.spi.LoggerWorkspaceContext
import com.romraider.ui.RuntimeUiProfile
import java.awt.BorderLayout
import java.awt.GraphicsEnvironment
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
    val requestedWidth = args.getOrNull(2)?.toIntOrNull()?.coerceAtLeast(640)
        ?: if (RuntimeUiProfile.isSteamOs()) 1280 else 1320
    val requestedHeight = args.getOrNull(3)?.toIntOrNull()?.coerceAtLeast(560)
        ?: if (RuntimeUiProfile.isSteamOs()) 800 else 860
    val usableBounds = GraphicsEnvironment.getLocalGraphicsEnvironment()
        .maximumWindowBounds
    val windowWidth = requestedWidth.coerceAtMost(usableBounds.width)
    val windowHeight = requestedHeight.coerceAtMost(usableBounds.height)
    val bus = LoggerLiveDataBus.getInstance()
    bus.clearSamples()
    val channels = listOf(
        channel("P-RPM", "Engine Speed", "rpm"),
        channel("P-BOOST", "Manifold Relative Pressure", "psi"),
        channel("P-AFR", "Air/Fuel Ratio", "AFR"),
        channel("P-THROTTLE", "Throttle Opening Angle", "%"),
        LoggerChannel(
            "P-COOLANT", "Engine Coolant Temperature", "°F",
            LoggerChannelKind.PARAMETER, true,
            listOf(
                LoggerChannelUnitOption("fahrenheit", "°F", true),
                LoggerChannelUnitOption("celsius", "°C", false)
            )
        ),
        channel("P-IGN", "Ignition Total Timing", "°"),
        LoggerChannel("S-CLUTCH", "Clutch Switch", "",
            LoggerChannelKind.SWITCH, true),
        LoggerChannel("E-WIDEBAND", "External Wideband", "lambda",
            LoggerChannelKind.EXTERNAL, false)
    ) + (1..24).map { index ->
        LoggerChannel(
            "P-FIXTURE-$index",
            "Additional test channel %02d".format(index),
            if (index % 3 == 0) "V" else "%",
            LoggerChannelKind.PARAMETER,
            false
        )
    }
    lateinit var channelService: LoggerChannelService
    var currentChannels = channels
    channelService = LoggerChannelService(
        { ids, selected ->
            currentChannels = currentChannels.map {
                if (it.parameterId in ids) it.withSelected(selected) else it
            }
            channelService.replaceChannels(currentChannels)
        },
        { id, optionId ->
            currentChannels = currentChannels.map { channel ->
                if (channel.parameterId != id) channel else {
                    val options = channel.unitOptions.map {
                        LoggerChannelUnitOption(
                            it.id, it.label, it.id == optionId)
                    }
                    val units = options.first { it.isSelected }.label
                    LoggerChannel(channel.parameterId, channel.name, units,
                        channel.kind, channel.isSelected, options)
                }
            }
            channelService.replaceChannels(currentChannels)
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
    val preferences = LoggerWorkspacePreferences(
        requestedView, darkTheme
    ) { _, _ -> }
    if (requestedView == LoggerWorkspaceView.DASHBOARD) {
        preferences.setDashboardTile("P-RPM", LoggerDashboardTile(
            LoggerDashboardTileRole.GAUGE,
            LoggerDashboardTileSize.STANDARD, 0))
        preferences.setDashboardTile("P-BOOST", LoggerDashboardTile(
            LoggerDashboardTileRole.ALARM,
            LoggerDashboardTileSize.STANDARD, 1))
        preferences.setDashboardTile("P-AFR", LoggerDashboardTile(
            LoggerDashboardTileRole.VALUE,
            LoggerDashboardTileSize.WIDE, 2))
        preferences.setDashboardTile("P-THROTTLE", LoggerDashboardTile(
            LoggerDashboardTileRole.TREND,
            LoggerDashboardTileSize.WIDE, 3))
        preferences.setDashboardTile("P-COOLANT", LoggerDashboardTile(
            LoggerDashboardTileRole.VALUE,
            LoggerDashboardTileSize.STANDARD, 4))
        preferences.setDashboardTile("P-IGN", LoggerDashboardTile(
            LoggerDashboardTileRole.TREND,
            LoggerDashboardTileSize.LARGE, 5))
        preferences.setGaugeConfiguration("P-BOOST",
            LoggerGaugeConfiguration(null, null, null, 5.0, 0.5))
    }

    SwingUtilities.invokeLater {
        JFrame("RomRaider2 Logger Workspace — Visual Fixture").apply {
            defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
            layout = BorderLayout()
            add(ComposeLoggerWorkspaceProvider().createWorkspace(
                LoggerWorkspaceContext(
                    bus, session, channelService,
                    preferences
                )),
                BorderLayout.CENTER)
            setSize(windowWidth, windowHeight)
            setLocation(
                usableBounds.x + (usableBounds.width - windowWidth) / 2,
                usableBounds.y + (usableBounds.height - windowHeight) / 2
            )
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
