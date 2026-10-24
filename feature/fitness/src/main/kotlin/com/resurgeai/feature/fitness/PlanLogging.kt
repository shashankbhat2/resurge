package com.resurgeai.feature.fitness

import com.resurgeai.core.data.fitness.WorkoutRecord
import com.resurgeai.core.model.ActivityCatalog
import com.resurgeai.core.model.FitnessMath
import com.resurgeai.core.model.PlanDay
import com.resurgeai.core.model.PlanSets
import com.resurgeai.core.model.StrengthExercise
import com.resurgeai.core.model.WorkoutDraft
import com.resurgeai.core.model.WorkoutMetricsInput
import java.time.Instant

/** Turns a planned session into a logged workout. Shared by "Done as planned" and the session logger. */
internal object PlanLogging {
    const val DEFAULT_MINUTES = 45

    /** The activity a planned day logs as; strength without one logs as weight training. */
    fun activityId(day: PlanDay): String = day.activityId?.takeIf { ActivityCatalog.byId(it).id == it } ?: "weights"

    /** Each planned exercise at its planned sets and reps, at the weight used [history] last time. */
    fun plannedExercises(day: PlanDay, history: List<WorkoutRecord>, now: Instant): List<StrengthExercise> {
        val last = LastSession.find(history, activityId(day), now, null)
        return day.exercises.map { e -> StrengthExercise(e.name, PlanSets.planned(e, last[LastSession.key(e.name)])) }
    }

    /** A workout of [day] that ended at [end] and took [durationMin], with these [exercises]. */
    fun draft(day: PlanDay, end: Instant, durationMin: Int, exercises: List<StrengthExercise>, weightKg: Double?): WorkoutDraft {
        val activity = ActivityCatalog.byId(activityId(day))
        return WorkoutDraft(
            activityId = activity.id,
            start = end.minusSeconds(durationMin * 60L),
            durationMin = durationMin,
            metrics = WorkoutMetricsInput(
                kcal = FitnessMath.estimateKcal(activity, durationMin, weightKg),
                exercises = exercises.filter { it.sets.isNotEmpty() },
            ),
            title = day.title.takeIf { it.isNotBlank() && !it.equals(activity.name, ignoreCase = true) },
        )
    }
}
