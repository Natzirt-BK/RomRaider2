/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.logger.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.romraider.ui.RuntimeUiProfile
import com.romraider.ui.ThemeMode
import com.romraider.ui.ApplicationThemeService

/** Keeps embedded Compose surfaces synchronized with the application theme. */
@Composable
internal fun rememberApplicationDarkTheme(): Boolean {
    val service = ApplicationThemeService.getInstance()
    var mode by remember { mutableStateOf(service.currentMode) }
    DisposableEffect(service) {
        val listener = ApplicationThemeService.Listener { next -> mode = next }
        service.addListener(listener)
        onDispose { service.removeListener(listener) }
    }
    return RuntimeUiProfile.isSteamOs()
        || mode == ThemeMode.DARK
        || mode == ThemeMode.HIGH_CONTRAST
}
