package com.automatelinux.myEmail.platform

import androidx.compose.runtime.Composable

/** System back (gesture or button) — closes the composer, then the thread. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
