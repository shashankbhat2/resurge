package com.resurgeai.core.designsystem.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Keeps screenshots, screen recording and the recents thumbnail out while [on] (progress photos,
 * a private habit's real name).
 */
@Composable
fun SecureWindow(on: Boolean) {
    val context = LocalContext.current
    DisposableEffect(on) {
        val window = context.findActivity()?.window
        if (on && window != null) {
            // Counted, because the next secure screen arrives before the last one leaves.
            if (secureHolders++ == 0) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            if (on && window != null && --secureHolders == 0) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

private var secureHolders = 0

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
