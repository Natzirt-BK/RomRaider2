/* RomRaider2 ECU Studio - GPL 2.0 or later. */
package com.romraider2.logger.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource

/** Uses the approved RR2 badge instead of the Java runtime's fallback icon. */
@Composable
internal fun romRaiderWindowIcon(): Painter = painterResource(
    "com/romraider2/ui/assets/icons/app/romraider2-app-64.png")

/** Full approved wordmark for branded in-application surfaces. */
@Composable
internal fun romRaiderHorizontalLogo(): Painter = painterResource(
    "com/romraider2/ui/assets/branding/romraider2-logo-horizontal.png")
