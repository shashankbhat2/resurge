package com.resurgeai.feature.weight

import com.resurgeai.core.designsystem.components.CoachBadge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.composeunstyled.Text
import com.resurgeai.core.data.session.SessionRepository
import com.resurgeai.core.data.weight.WeightRepository
import com.resurgeai.core.designsystem.components.Button
import com.resurgeai.core.designsystem.components.ButtonVariant
import com.resurgeai.core.designsystem.components.Confetti
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.CoachStyle
import com.resurgeai.core.model.GoalCelebration
import com.resurgeai.core.model.SessionState
import com.resurgeai.core.model.UnitSystem
import com.resurgeai.core.model.WeightGoal
import com.resurgeai.core.model.WeightMath
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

internal data class CelebrationState(
    val celebration: GoalCelebration,
    val goal: WeightGoal,
    val units: WeightUnits,
    val commander: Boolean,
)

@HiltViewModel
internal class GoalCelebrationViewModel @Inject constructor(
    private val repository: WeightRepository,
    session: SessionRepository,
) : ViewModel() {
    /** Shown this session already; covers the moment before the write comes back from Firestore. */
    private val shown = MutableStateFlow<Set<String>>(emptySet())

    private val profile = session.session.filterIsInstance<SessionState.Ready>().map { it.profile }

    val state: StateFlow<CelebrationState?> = combine(
        repository.goal().catch { emit(null) },
        repository.weighIns().catch { emit(emptyList()) },
        profile,
        shown,
    ) { realGoal, weighIns, profile, shown ->
        val units = WeightUnits(profile.units)
        val commander = profile.coachStyle == CoachStyle.COMMANDER
        val goal = realGoal ?: return@combine null
        val now = WeightMath.trend(weighIns, LocalDate.now()).lastOrNull()?.kg ?: return@combine null
        val c = WeightMath.celebration(goal, now) ?: return@combine null
        if (key(goal, c) in shown) return@combine null
        CelebrationState(c, goal, units, commander)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Records the celebration so it never shows again. */
    fun done() {
        val s = state.value ?: return
        shown.update { it + key(s.goal, s.celebration) }
        when (val c = s.celebration) {
            is GoalCelebration.Milestone -> repository.markCelebrated(c.number, null)
            is GoalCelebration.Goal -> repository.markCelebrated(c.milestones, LocalDate.now())
        }
    }

    /** After the goal: hold the goal weight from today. */
    fun maintain() {
        val s = state.value ?: return
        done()
        repository.setGoal(WeightGoal.maintain(s.goal.goalKg, LocalDate.now()))
    }

    private fun key(goal: WeightGoal, c: GoalCelebration) = "${goal.startDate}/${goal.goalKg}/" + when (c) {
        is GoalCelebration.Milestone -> "m${c.number}"
        is GoalCelebration.Goal -> "goal"
    }
}

/**
 * Celebrates a new milestone (confetti, an award, a word from the coach) or the goal (more of all
 * three, then what's next: a new goal or maintaining). Sits over the whole app, so it shows wherever
 * the person is when the trend gets there. [onNewGoal] opens the goal editor for a fresh goal.
 */
@Composable
fun GoalCelebrationHost(onNewGoal: () -> Unit) {
    val viewModel: GoalCelebrationViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = state ?: return
    Dialog(onDismissRequest = viewModel::done, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (val c = s.celebration) {
                is GoalCelebration.Milestone -> MilestoneAward(c, s, onDone = viewModel::done)
                is GoalCelebration.Goal -> GoalReward(
                    c,
                    s,
                    onNewGoal = {
                        viewModel.done()
                        onNewGoal()
                    },
                    onMaintain = viewModel::maintain,
                    onLater = viewModel::done,
                )
            }
            Confetti(
                Modifier.fillMaxSize(),
                pieces = if (s.celebration is GoalCelebration.Goal) 160 else 60,
                durationMs = if (s.celebration is GoalCelebration.Goal) 3_600 else 2_400,
            )
        }
    }
}

@Composable
internal fun MilestoneAward(c: GoalCelebration.Milestone, s: CelebrationState, onDone: () -> Unit) {
    Card {
        Badge(ResurgeIcons.Medal, 72.dp)
        Spacer(Modifier.height(14.dp))
        Text("MILESTONE ${c.number} OF ${c.count}", style = Resurge.type.overline, color = Resurge.colors.up)
        Spacer(Modifier.height(4.dp))
        Text("${trim(s.units.format(c.kgDone))} ${if (s.goal.losing) "down" else "up"}", style = Resurge.type.tide.copy(fontSize = 36.sp, lineHeight = 40.sp))
        Spacer(Modifier.height(18.dp))
        CoachSays(milestoneLine(c, s), s.commander)
        Spacer(Modifier.height(20.dp))
        Button("Keep going", onClick = onDone, variant = ButtonVariant.Accent, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
internal fun GoalReward(c: GoalCelebration.Goal, s: CelebrationState, onNewGoal: () -> Unit, onMaintain: () -> Unit, onLater: () -> Unit) {
    val weeks = (ChronoUnit.DAYS.between(c.since, LocalDate.now()) / 7).coerceAtLeast(1)
    Card {
        Badge(ResurgeIcons.Trophy, 96.dp)
        Spacer(Modifier.height(14.dp))
        Text("GOAL REACHED", style = Resurge.type.overline, color = Resurge.colors.up)
        Spacer(Modifier.height(4.dp))
        Text(s.units.format(s.goal.goalKg), style = Resurge.type.tide.copy(fontSize = 40.sp, lineHeight = 44.sp))
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RewardStat(trim(s.units.format(c.totalKg)), if (s.goal.losing) "lost" else "gained", Modifier.weight(1f))
            RewardStat("$weeks", if (weeks == 1L) "week" else "weeks", Modifier.weight(1f))
            RewardStat("${c.milestones}", if (c.milestones == 1) "milestone" else "milestones", Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        CoachSays(goalLine(c, s, weeks), s.commander)
        Spacer(Modifier.height(20.dp))
        Button("Set a new goal", onClick = onNewGoal, variant = ButtonVariant.Accent, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Button("Maintain ${s.units.format(s.goal.goalKg)}", onClick = onMaintain, variant = ButtonVariant.Secondary, modifier = Modifier.fillMaxWidth())
        Button("Not now", onClick = onLater, variant = ButtonVariant.Ghost, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.padding(24.dp).fillMaxWidth().clip(Resurge.shape.lg).background(Resurge.colors.surface).padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

/** The award: the icon on a glowing disc with a soft halo. */
@Composable
private fun Badge(icon: Int, size: Dp) {
    Box(Modifier.size(size + 24.dp).clip(Resurge.shape.pill).background(Resurge.colors.up.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size).clip(Resurge.shape.pill).background(Resurge.colors.up), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Resurge.colors.bg, size = size * 0.5f)
        }
    }
}

@Composable
private fun RewardStat(value: String, label: String, modifier: Modifier) {
    Column(modifier.clip(Resurge.shape.md).background(Resurge.colors.surfaceMuted).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = Resurge.type.metric.copy(fontSize = 18.sp, lineHeight = 24.sp, fontFeatureSettings = "tnum"))
        Text(label, style = Resurge.type.caption, color = Resurge.colors.textMuted)
    }
}

@Composable
private fun CoachSays(text: String, commander: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        CoachBadge(size = 36.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            style = Resurge.type.body,
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f).clip(Resurge.shape.md).background(Resurge.colors.surfaceMuted).padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

/** "4.0 kg" → "4 kg"; keeps a real decimal. */
private fun trim(text: String) = text.replace(Regex("""[.,]0 """), " ")

/**
 * A line in the coach's voice, picked by milestone so they don't repeat back to back. The award
 * already shows the number, so the lines don't repeat it.
 */
internal fun milestoneLine(c: GoalCelebration.Milestone, s: CelebrationState): String {
    val next = c.next?.let { trim(s.units.format(it.targetKg)) }
    val since = shortDate(s.goal.startDate)
    val friend = listOf(
        "You've been at this since $since, one day at a time, and it shows.",
        "Quiet, steady work like this is what lasts. Proud of you.",
        "Plenty of people stop before here. You didn't.",
    )
    val commander = listOf(
        "Good. ${next?.let { "Next stop is $it." } ?: "Keep going."} Stay on it.",
        "That's consistency paying off. Don't ease off now.",
        "That was you, not luck. Same standard tomorrow.",
    )
    val lines = if (s.commander) commander else friend
    return lines[(c.number - 1) % lines.size]
}

internal fun goalLine(c: GoalCelebration.Goal, s: CelebrationState, weeks: Long): String {
    val total = trim(s.units.format(c.totalKg))
    return if (s.commander) {
        "Goal hit. $total in $weeks ${if (weeks == 1L) "week" else "weeks"}. Now choose what's next: a new target, or hold this one."
    } else {
        "You did it. $total in $weeks ${if (weeks == 1L) "week" else "weeks"}, and you showed up for all of it. Take a moment with this one."
    }
}
