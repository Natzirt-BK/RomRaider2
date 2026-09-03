/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.logger.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import java.awt.GraphicsEnvironment

/** Keeps a new desktop window inside the usable bounds of the current screen. */
@Composable
internal fun rememberFittedWindowState(
    preferredWidth: Int,
    preferredHeight: Int
): WindowState {
    val initialSize = remember(preferredWidth, preferredHeight) {
        val usableBounds = runCatching {
            GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        }.getOrNull()
        Pair(
            usableBounds?.width
                ?.takeIf { it > 0 }
                ?.let { preferredWidth.coerceAtMost(it) }
                ?: preferredWidth,
            usableBounds?.height
                ?.takeIf { it > 0 }
                ?.let { preferredHeight.coerceAtMost(it) }
                ?: preferredHeight
        )
    }
    return rememberWindowState(
        position = WindowPosition(Alignment.Center),
        width = initialSize.first.dp,
        height = initialSize.second.dp
    )
}
