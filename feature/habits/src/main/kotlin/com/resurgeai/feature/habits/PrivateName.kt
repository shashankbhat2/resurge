package com.resurgeai.feature.habits

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

/**
 * Reveals a private habit's real name after the phone's screen lock (when it has one). Call the
 * returned function to start; [onName] gets the name, or null when this phone can't read it.
 */
@Composable
internal fun rememberRevealName(read: suspend () -> String?, onName: (String?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf(false) }
    val reveal = { scope.launch { onName(read()) } }
    val confirm = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (pending && result.resultCode == Activity.RESULT_OK) reveal()
        pending = false
    }
    return {
        val intent = confirmIntent(context)
        if (intent == null) reveal() else {
            pending = true
            confirm.launch(intent)
        }
    }
}

@Suppress("DEPRECATION") // BiometricPrompt would add a dependency for a one-off confirmation.
private fun confirmIntent(context: Context) = context.getSystemService(KeyguardManager::class.java)
    ?.takeIf { it.isDeviceSecure }
    ?.createConfirmDeviceCredentialIntent("Show habit name", "Confirm it's you")
