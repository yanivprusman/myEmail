package com.automatelinux.myEmail.platform

import androidx.compose.runtime.Composable

/** System back (gesture or button) — closes the composer, then the thread. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

/** Status-bar icon colour: light over the ink header, dark over paper. */
@Composable
expect fun StatusBarIcons(light: Boolean)
