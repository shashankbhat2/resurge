package com.resurgeai.feature.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.SleepStage

/** Hypnogram rows, top to bottom. Generic "sleeping" is drawn on the Light row. */
internal val SLEEP_LEVELS = listOf(SleepStage.AWAKE, SleepStage.REM, SleepStage.LIGHT, SleepStage.DEEP)

internal fun SleepStage.level(): Int = when (this) {
    SleepStage.AWAKE -> 0
    SleepStage.REM -> 1
    SleepStage.LIGHT, SleepStage.SLEEPING, SleepStage.UNKNOWN -> 2
    SleepStage.DEEP -> 3
}

internal val SleepStage.label: String
    get() = when (this) {
        SleepStage.AWAKE -> "Awake"
        SleepStage.REM -> "REM"
        SleepStage.LIGHT -> "Light"
        SleepStage.DEEP -> "Deep"
        SleepStage.SLEEPING, SleepStage.UNKNOWN -> "Asleep"
    }

/** One colour per stage, used by the Today ring, the list rows and the hypnogram. */
@Composable
internal fun sleepStageColor(stage: SleepStage): Color = when (stage) {
    SleepStage.AWAKE -> Resurge.colors.energy
    SleepStage.REM -> Resurge.colors.sleep
    SleepStage.LIGHT, SleepStage.SLEEPING, SleepStage.UNKNOWN -> Resurge.colors.water
    SleepStage.DEEP -> Resurge.colors.activity
}
