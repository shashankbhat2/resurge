package com.resurgeai.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * A progress photo's metadata. The image itself is end-to-end encrypted: only the device holding the
 * user's photo key can read it. [uploaded] is false until the encrypted file reaches cloud storage.
 */
data class ProgressPhoto(
    val id: String,
    val takenAt: Instant,
    val width: Int,
    val height: Int,
    val uploaded: Boolean = true,
) {
    fun date(zone: ZoneId = ZoneId.systemDefault()): LocalDate = takenAt.atZone(zone).toLocalDate()
}

/** Where the user is with their photo key on this device. */
enum class PhotoKeyState {
    /** Still checking. */
    LOADING,

    /** No key anywhere yet: created with the first photo. */
    NOT_SET_UP,

    /** The key is on this device; photos can be read and added. */
    READY,

    /**
     * A key exists in the cloud but this phone couldn't get it (a new phone that wasn't restored from
     * a Google backup): earlier photos can't be opened here.
     */
    LOCKED,

    /** No key on this device and the cloud couldn't be reached to check. */
    OFFLINE,
}

object ProgressMath {
    /**
     * The day progress counts from: the weight goal's start when it's before the first photo, else
     * the first photo. Null without photos.
     */
    fun anchor(goalStart: LocalDate?, photos: List<ProgressPhoto>, zone: ZoneId = ZoneId.systemDefault()): LocalDate? {
        val first = photos.minOfOrNull { it.date(zone) } ?: return null
        return if (goalStart != null && goalStart.isBefore(first)) goalStart else first
    }

    /** Whole weeks from [anchor] to [date]: week 0 is the first seven days. */
    fun week(anchor: LocalDate, date: LocalDate): Int = (ChronoUnit.DAYS.between(anchor, date) / 7).toInt().coerceAtLeast(0)

    /** "Day 1", "Day 5", then "Week 2" from the second week on. */
    fun label(anchor: LocalDate, date: LocalDate): String {
        val days = ChronoUnit.DAYS.between(anchor, date).coerceAtLeast(0)
        return if (days < 7) "Day ${days + 1}" else "Week ${days / 7 + 1}"
    }
}
