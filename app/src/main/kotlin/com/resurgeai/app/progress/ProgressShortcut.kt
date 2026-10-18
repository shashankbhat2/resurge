package com.resurgeai.app.progress

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.resurgeai.app.MainActivity
import com.resurgeai.app.R
import com.resurgeai.app.notifications.ResurgeMessagingService

/**
 * Long-press the app icon → "Snapshot": straight to the progress camera, without a tile or
 * tab for it anywhere in the app. Dynamic rather than static so debug and release (different
 * package names) both get it.
 */
object ProgressShortcut {
    const val OPEN_CAPTURE = "progress_capture"
    const val OPEN_PROGRESS = "progress"
    private const val ID = "progress_photo"

    fun publish(context: Context) {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(ResurgeMessagingService.EXTRA_OPEN, OPEN_CAPTURE)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val shortcut = ShortcutInfoCompat.Builder(context, ID)
            .setShortLabel("Snapshot")
            .setLongLabel("Take a snapshot")
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_progress))
            .setIntent(intent)
            .build()
        runCatching { ShortcutManagerCompat.pushDynamicShortcut(context, shortcut) }
    }
}
