/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.logger.compose

import androidx.compose.ui.input.key.Key
import com.romraider.logger.api.LiveDataSample
import com.romraider.logger.api.LoggerChannel
import com.romraider.logger.api.LoggerChannelKind
import com.romraider.logger.api.LoggerWorkspaceView
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
    fun statisticsUseOnlyTheReceivedSamples() {
        val stats = statistics(listOf(
            sample(10.0), sample(20.0), sample(30.0), sample(40.0)
        ))!!

        assertEquals(10.0, stats.minimum)
        assertEquals(40.0, stats.maximum)
        assertEquals(25.0, stats.average)
        assertEquals(25.0, stats.median)
        assertEquals(4, stats.count)
    }

    @Test
    fun emptyHistoryDoesNotInventStatistics() {
        assertNull(statistics(emptyList()))
    }

    @Test
    fun keyboardShortcutsSelectTheThreePrimaryViews() {
        assertEquals(LoggerWorkspaceView.OVERVIEW,
            workspaceForShortcut(Key.One))
        assertEquals(LoggerWorkspaceView.DATA, workspaceForShortcut(Key.Two))
        assertEquals(LoggerWorkspaceView.GRAPH,
            workspaceForShortcut(Key.Three))
        assertEquals(LoggerWorkspaceView.DASHBOARD,
            workspaceForShortcut(Key.Four))
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

    private fun sample(value: Double) = LiveDataSample(
        "P-TEST", "Test channel", value, value.toString(), "unit", 1L)
}
