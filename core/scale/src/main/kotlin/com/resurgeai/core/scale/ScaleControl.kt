package com.resurgeai.core.scale

/**
 * The parts of listening that need app components (a foreground service, a receiver for
 * background wake-ups). Implemented in :app so feature screens can drive them.
 */
interface ScaleControl {
    /** Starts or refreshes listening with the app closed, per the paired scales and settings. */
    fun syncBackgroundListening()

    /** Listens actively for a minute (from a widget tap or a "Weigh in" button), showing progress in a notification. */
    fun weighInNow()
}
