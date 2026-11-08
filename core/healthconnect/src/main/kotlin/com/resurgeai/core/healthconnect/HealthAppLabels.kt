package com.resurgeai.core.healthconnect

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Shared app-name lookup for dashboard metrics, workout cards, and Cardio Points sources. */
@Singleton
class HealthAppLabels @Inject constructor(@ApplicationContext private val context: Context) {
    private val labels = mutableMapOf<String, String?>()

    fun label(packageName: String): String? = synchronized(labels) {
        labels.getOrPut(packageName) {
            SourceResolver.knownAppName(packageName) ?: runCatching {
                val pm = context.packageManager
                @Suppress("DEPRECATION") // ApplicationInfoFlags needs API 33.
                pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
            }.getOrNull()
        }
    }
}
