/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.logger.compose

import androidx.compose.ui.input.key.Key
import com.romraider.logger.api.LiveDataSample
import com.romraider.logger.api.LoggerChannel
import com.romraider.logger.api.LoggerChannelKind
import com.romraider.logger.api.LoggerChannelService
import com.romraider.logger.api.LoggerLiveDataBus
import com.romraider.logger.api.LoggerSessionService
import com.romraider.logger.api.LoggerWorkspacePreferences
import com.romraider.logger.api.LoggerWorkspaceView
import com.romraider.logger.ecu.ui.spi.LoggerWorkspaceContext
import com.romraider.logger.ecu.ui.spi.LoggerWorkspaceProvider
import java.util.ServiceLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LoggerWorkspaceModelTest {
    @Test
    fun providerIsDiscoverableThroughThePackagedServiceBoundary() {
        val providers = ServiceLoader.load(LoggerWorkspaceProvider::class.java)
            .toList()

        assertEquals(1, providers.size)
        assertEquals("Compose Desktop Logger", providers.single().name)
    }

    @Test
    fun providerCreatesItsPanelWhenLoggerBootstrapIsOffTheSwingThread() {
        val bus = LoggerLiveDataBus.getInstance()
        val session = LoggerSessionService(
            bus, { }, { }, { }, { }, { throw it })
        val channels = LoggerChannelService({ _, _ -> }, { throw it })
        val preferences = LoggerWorkspacePreferences(
            LoggerWorkspaceView.OVERVIEW, false) { _, _ -> }

        val workspace = ComposeLoggerWorkspaceProvider().createWorkspace(
            LoggerWorkspaceContext(bus, session, channels, preferences))

        assertEquals("androidx.compose.ui.awt.ComposePanel",
            workspace.javaClass.name)
    }

    @Test
    fun statisticsUseOnlyTheReceivedSamples() {
        val stats = statistics(listOf(
            sample(10.0), sample(20.0), sample(30.0), sample(40.0)
        ))!!

        assertEquals(10.0, stats.minimum)
        assertEquals(40.0, stats.maximum)
        assertEquals(25.0, stats.average)
        assertEquals(25.0, stats.median)
        assertEquals(11.5, stats.percentile05)
        assertEquals(38.5, stats.percentile95)
        assertEquals(4, stats.count)
    }

    @Test
    fun emptyHistoryDoesNotInventStatistics() {
        assertNull(statistics(emptyList()))
    }

    @Test
    fun keyboardShortcutsSelectEveryWorkspaceView() {
        assertEquals(LoggerWorkspaceView.OVERVIEW,
            workspaceForShortcut(Key.One))
        assertEquals(LoggerWorkspaceView.DATA, workspaceForShortcut(Key.Two))
        assertEquals(LoggerWorkspaceView.GRAPH,
            workspaceForShortcut(Key.Three))
        assertEquals(LoggerWorkspaceView.DASHBOARD,
            workspaceForShortcut(Key.Four))
        assertEquals(LoggerWorkspaceView.ANALYSIS,
            workspaceForShortcut(Key.Five))
        assertNull(workspaceForShortcut(Key.F))
    }

    @Test
    fun channelAccessibilityLabelIncludesIdentityAndSelection() {
        val channel = LoggerChannel(
            "P-RPM", "Engine Speed", "rpm",
            LoggerChannelKind.PARAMETER, true
        )

        assertEquals(
            "Engine Speed, ID P-RPM, rpm, selected",
            channelAccessibilityLabel(channel)
        )
    }

    @Test
    fun telemetryUsesOnlyReceivedPointsAndTimestamps() {
        val channel = LoggerChannel(
            "P-RPM", "Engine Speed", "rpm",
            LoggerChannelKind.PARAMETER, true
        )
        val first = LiveDataSample(
            "P-RPM", "Engine Speed", 1000.0, "1000", "rpm", 1_000L)
        val second = LiveDataSample(
            "P-RPM", "Engine Speed", 2000.0, "2000", "rpm", 3_500L)

        val metrics = sessionMetrics(
            listOf(channel), mapOf("P-RPM" to second),
            mapOf("P-RPM" to listOf(first, second))
        )

        assertEquals(1, metrics.selectedChannels)
        assertEquals(1, metrics.liveChannels)
        assertEquals(2, metrics.receivedPoints)
        assertEquals("2.5 s", metrics.windowLabel)
    }

    @Test
    fun analysisSummaryUsesSelectedChannelsWithReceivedData() {
        val firstChannel = LoggerChannel(
            "P-RPM", "Engine Speed", "rpm",
            LoggerChannelKind.PARAMETER, true)
        val emptyChannel = LoggerChannel(
            "P-BOOST", "Boost", "psi",
            LoggerChannelKind.PARAMETER, true)

        val summary = analysisSummary(
            listOf(firstChannel, emptyChannel),
            mapOf("P-RPM" to listOf(
                LiveDataSample("P-RPM", "Engine Speed", 1000.0,
                    "1000", "rpm", 1_000L),
                LiveDataSample("P-RPM", "Engine Speed", 2000.0,
                    "2000", "rpm", 4_000L)
            ))
        )

        assertEquals(1, summary.channels)
        assertEquals(2, summary.samples)
        assertEquals("3.0 s", summary.durationLabel)
        assertEquals("1 / 2", summary.coverageLabel)
    }

    private fun sample(value: Double) = LiveDataSample(
        "P-TEST", "Test channel", value, value.toString(), "unit", 1L)
}
