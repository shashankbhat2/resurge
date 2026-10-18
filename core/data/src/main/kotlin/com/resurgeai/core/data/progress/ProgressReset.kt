package com.resurgeai.core.data.progress

import com.resurgeai.core.data.prefs.PreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Debug only: puts progress photos back to never-used for this account. Deletes every photo (here
 * and in the cloud), every reel, the reel settings and the photo key, and clears the local flags,
 * so the first-run screen shows again.
 */
@Singleton
class ProgressReset @Inject constructor(
    private val photos: ProgressRepository,
    private val reels: ReelRepository,
    private val keys: PhotoKeys,
    private val preferences: PreferencesRepository,
) {
    suspend operator fun invoke() {
        photos.deleteAll()
        reels.deleteAll()
        reels.resetSettings()
        keys.forget()
        preferences.resetProgressPrefs()
    }
}
