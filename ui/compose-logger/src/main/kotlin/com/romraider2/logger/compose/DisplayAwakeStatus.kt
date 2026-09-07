/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.logger.compose

import androidx.compose.runtime.*
import com.romraider.ui.DesktopDisplayAwake
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun rememberDisplayAwakeStatus(awake: DesktopDisplayAwake): DesktopDisplayAwake.Status {
    var status by remember(awake) { mutableStateOf(awake.status) }
    LaunchedEffect(awake) {
        while (isActive) { status = awake.status; delay(250) }
    }
    return status
}
