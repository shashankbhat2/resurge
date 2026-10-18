package com.resurgeai.feature.habits

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.designsystem.components.Button
import com.resurgeai.core.designsystem.components.ButtonVariant
import com.resurgeai.core.designsystem.components.HabitVisuals
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.IconButton
import com.resurgeai.core.designsystem.components.InputField
import com.resurgeai.core.designsystem.components.Overline
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.SectionLabel
import com.resurgeai.core.designsystem.components.StatDivider
import com.resurgeai.core.designsystem.components.TopBar
import com.resurgeai.core.designsystem.components.Switch
import com.resurgeai.core.designsystem.components.SecureWindow
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.DayState
import com.resurgeai.core.model.HabitDraft
import com.resurgeai.core.model.HabitStreaks
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** One habit: its streak, best and consistency, 12 weeks of days, the freeze rule, and archive. */
@Composable
fun HabitDetailRoute(
    habitId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: HabitsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val item = state.item(habitId)
    // Archived (or deleted elsewhere): leave once data has loaded.
    LaunchedEffect(state.loading, item == null) { if (!state.loading && item == null) onBack() }
    if (item == null) return
    var confirmArchive by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = item.habit.name,
            onBack = onBack,
            trailing = { IconButton(ResurgeIcons.Pencil, contentDescription = "Edit habit", onClick = { onEdit(habitId) }) },
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StreakHero(item)
            if (item.habit.private) PrivateCard(item.habit.name, read = { viewModel.realName(habitId) })
            SectionLabel("Last 12 weeks", Modifier.padding(top = 4.dp))
            Column(
                Modifier.fillMaxWidth().clip(Resurge.shape.md).background(Resurge.colors.surface).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Heatmap(item.stats.days, state.today, item.habit.color)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    LegendItem(DayState.DONE, item.habit.color, "Done")
                    LegendItem(DayState.FROZEN, item.habit.color, "Freeze")
                    LegendItem(DayState.MISSED, item.habit.color, "Missed")
                }
            }
            // The freeze rule, in the anchor's own colours.
            Row(
                Modifier.fillMaxWidth().clip(Resurge.shape.sm).background(Resurge.colors.frost).padding(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(ResurgeIcons.Snowflake, contentDescription = null, tint = Resurge.colors.frostInk, size = 18.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Miss a day and one freeze a week (Sunday to Saturday) keeps your streak going, automatically.",
                    style = Resurge.type.label,
                    color = Resurge.colors.frostInk,
                )
            }
            Spacer(Modifier.height(8.dp))
            val canArchive = state.items.size > 1
            Button(
                text = when {
                    !canArchive -> "Keep at least one habit"
                    confirmArchive -> "Tap again to archive"
                    else -> "Archive habit"
                },
                onClick = {
                    if (confirmArchive) {
                        if (viewModel.archive(habitId)) onBack()
                    } else {
                        confirmArchive = true
                    }
                },
                enabled = canArchive,
                variant = ButtonVariant.Danger,
                leadingIcon = ResurgeIcons.Archive,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * A private habit's real name, hidden until asked for (behind the screen lock) and hidden again
 * on leaving. Screenshots are blocked while it shows.
 */
@Composable
private fun PrivateCard(cover: String, read: suspend () -> String?) {
    var shown by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    val reveal = rememberRevealName(read) { name -> if (name != null) shown = name else failed = true }
    SecureWindow(shown != null)
    Row(
        Modifier.fillMaxWidth().clip(Resurge.shape.md).background(Resurge.colors.surface).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(ResurgeIcons.Lock, contentDescription = null, tint = Resurge.colors.textMuted, size = 20.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            val name = shown
            if (name != null) {
                Text(name, style = Resurge.type.body.copy(fontWeight = Resurge.type.headline.fontWeight))
                Text("Shown as \"$cover\" everywhere else", style = Resurge.type.caption, color = Resurge.colors.textMuted)
            } else {
                Text("Private habit", style = Resurge.type.body)
                Text(if (failed) "The name can't be opened on this phone." else "Shown as \"$cover\" everywhere else", style = Resurge.type.caption, color = Resurge.colors.textMuted)
            }
        }
        Spacer(Modifier.width(8.dp))
        if (shown != null) Button("Hide", onClick = { shown = null }, variant = ButtonVariant.Secondary)
        else if (!failed) Button("Show", onClick = reveal, variant = ButtonVariant.Secondary)
    }
}

/** The streak in big type with its badge, then best, last 30 days and this week's freeze. */
@Composable
private fun StreakHero(item: HabitItem) {
    val running = item.stats.current > 0
    Column(Modifier.fillMaxWidth().clip(Resurge.shape.lg).background(Resurge.colors.surface)) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(Resurge.shape.pill).background(if (running) Resurge.colors.glow else Resurge.colors.surfaceMuted),
                contentAlignment = Alignment.Center,
            ) {
                Icon(ResurgeIcons.HabitStreak, contentDescription = null, tint = if (running) Resurge.colors.onGlow else Resurge.colors.textMuted, size = 28.dp)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Overline("Habit streak")
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${item.stats.current}", style = Resurge.type.tide.copy(fontSize = 44.sp, lineHeight = 46.sp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (item.stats.current == 1) "day" else "days", style = Resurge.type.label, color = Resurge.colors.textMuted, modifier = Modifier.padding(bottom = 6.dp))
                }
            }
        }
        com.resurgeai.core.designsystem.components.Divider()
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            HeroStat(if (item.stats.best == 1) "1 day" else "${item.stats.best} days", "Best streak", Modifier.weight(1f))
            StatDivider()
            HeroStat("${(item.stats.completion30 * 100).roundToInt()}%", "Last 30 days", Modifier.weight(1f))
            StatDivider()
            HeroStat(if (item.stats.freezesLeftThisWeek > 0) "Ready" else "Used", "Freeze this week", Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeroStat(value: String, label: String, modifier: Modifier) {
    Column(modifier.fillMaxHeight().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(value, style = Resurge.type.metric.copy(fontSize = 18.sp, lineHeight = 22.sp), maxLines = 1)
        Text(label, style = Resurge.type.caption, color = Resurge.colors.textSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 12 Sunday-start weeks as columns, Sun…Sat rows; the current week is the rightmost column. */
@Composable
private fun Heatmap(days: Map<LocalDate, DayState>, today: LocalDate, colorKey: String) {
    val lastWeek = HabitStreaks.weekStart(today)
    val firstWeek = lastWeek.minusWeeks(11)
    Row {
        Column(Modifier.padding(end = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            (0 until 7).forEach { d ->
                Box(Modifier.height(18.dp), contentAlignment = Alignment.CenterStart) {
                    Text(
                        firstWeek.plusDays(d.toLong()).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        style = Resurge.type.caption,
                        color = Resurge.colors.textSubtle,
                    )
                }
            }
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            (0 until 12).forEach { w ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    (0 until 7).forEach { d ->
                        val date = firstWeek.plusWeeks(w.toLong()).plusDays(d.toLong())
                        val state = if (date.isAfter(today)) DayState.NONE else days[date] ?: DayState.NONE
                        val cell = Modifier.fillMaxWidth().height(18.dp)
                        val shape = RoundedCornerShape(4.dp)
                        when (state) {
                            DayState.DONE -> Box(cell.clip(shape).background(HabitVisuals.fill(colorKey)))
                            DayState.FROZEN -> Box(cell.clip(shape).background(Resurge.colors.frost))
                            DayState.MISSED -> Box(cell.clip(shape).background(Resurge.colors.ebb))
                            DayState.PENDING -> Box(cell.dashedBorder(Resurge.colors.textSubtle, 4.dp, 1.dp))
                            DayState.NONE -> Box(cell.clip(shape).background(Resurge.colors.surfaceMuted.copy(alpha = 0.5f)))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(state: DayState, colorKey: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        DayCell(state, colorKey, size = 14.dp, radius = 4.dp)
        Spacer(Modifier.width(6.dp))
        Text(label, style = Resurge.type.caption, color = Resurge.colors.textMuted)
    }
}

/** Add (habitId = null) or edit a habit: a live preview, its name, icon and colour. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HabitEditorRoute(
    habitId: String?,
    onDone: () -> Unit,
    viewModel: HabitsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val existing = habitId?.let { state.item(it)?.habit }
    // For a private habit, [name] is the real name (left empty while hidden) and [cover] what shows.
    val name = rememberTextFieldState()
    val cover = rememberTextFieldState(HabitDraft.DEFAULT_COVER)
    var private by remember { mutableStateOf(false) }
    var icon by remember { mutableStateOf("flame") }
    var color by remember { mutableStateOf("accent") }
    var initialised by remember { mutableStateOf(habitId == null) }
    var revealFailed by remember { mutableStateOf(false) }
    LaunchedEffect(existing) {
        if (!initialised && existing != null) {
            if (existing.private) cover.edit { replace(0, length, existing.name) } else name.edit { replace(0, length, existing.name) }
            private = existing.private
            icon = existing.icon
            color = existing.color
            initialised = true
        }
    }
    val reveal = habitId?.let { id ->
        rememberRevealName(read = { viewModel.realName(id) }) { real ->
            if (real != null) name.edit { replace(0, length, real) } else revealFailed = true
        }
    }
    val wasPrivate = existing?.private == true
    // A stored private name can be kept without retyping; anything else needs a name.
    val nameOk = name.text.isNotBlank() || (private && wasPrivate)
    val canSave = nameOk && (!private || cover.text.isNotBlank()) && (habitId != null || state.canAdd)
    val save = {
        if (canSave) {
            val draft = if (private) {
                HabitDraft(cover.text.toString().trim(), icon, color, private = true, realName = name.text.toString().trim().ifEmpty { null })
            } else {
                HabitDraft(name.text.toString().trim(), icon, color)
            }
            viewModel.save(habitId, draft, onDone)
        }
    }

    SecureWindow(private)
    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar(title = if (habitId == null) "New habit" else "Edit habit", onBack = onDone)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PreviewTile(if (private) cover.text.toString() else name.text.toString(), icon, color)
            SectionLabel("Name", Modifier.padding(top = 4.dp))
            InputField(name, placeholder = if (wasPrivate) "Hidden. Leave empty to keep it" else "e.g. Walk after dinner", onDone = save)
            if (wasPrivate && name.text.isEmpty() && reveal != null) {
                Button("Show name", onClick = reveal, variant = ButtonVariant.Secondary, leadingIcon = ResurgeIcons.Lock, modifier = Modifier.fillMaxWidth())
                if (revealFailed) Text("The name can't be opened on this phone.", style = Resurge.type.caption, color = Resurge.colors.textSubtle)
            }

            Row(
                Modifier.fillMaxWidth().clip(Resurge.shape.md).background(Resurge.colors.surface).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Private", style = Resurge.type.body)
                    Text(
                        "Shows a cover name everywhere else. The real name is encrypted and only shown on this habit's page.",
                        style = Resurge.type.caption,
                        color = Resurge.colors.textMuted,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(private, { on ->
                    private = on
                    // A telling icon would give it away.
                    if (on && icon !in NEUTRAL_ICONS) icon = HabitDraft.PRIVATE_ICON
                }, accessibilityLabel = "Private habit")
            }
            if (private) {
                SectionLabel("Cover name", Modifier.padding(top = 4.dp))
                InputField(cover, placeholder = HabitDraft.DEFAULT_COVER, onDone = save)
            }
            if (!private && wasPrivate && name.text.isBlank()) {
                Text("Show or type the real name to make it public again.", style = Resurge.type.caption, color = Resurge.colors.textSubtle)
            }
            state.saveError?.let { Text(it, style = Resurge.type.caption, color = Resurge.colors.danger) }

            SectionLabel("Colour", Modifier.padding(top = 4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HabitVisuals.colorKeys.forEach { key ->
                    val selected = key == color
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        UnstyledButton(
                            onClick = { color = key },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(Resurge.shape.pill)
                                .border(if (selected) 2.dp else 0.dp, if (selected) Resurge.colors.text else Resurge.colors.surface, Resurge.shape.pill)
                                .padding(if (selected) 4.dp else 0.dp)
                                .clip(Resurge.shape.pill)
                                .background(HabitVisuals.fill(key))
                                .semantics {
                                    contentDescription = HabitVisuals.colorLabel(key)
                                    this.selected = selected
                                },
                            indication = LocalIndication.current,
                        ) {
                            if (selected) Icon(ResurgeIcons.Check, contentDescription = null, tint = Resurge.colors.onAccent, size = 18.dp)
                        }
                        Text(
                            HabitVisuals.colorLabel(key),
                            style = Resurge.type.caption,
                            color = if (selected) Resurge.colors.text else Resurge.colors.textSubtle,
                        )
                    }
                }
            }

            SectionLabel("Icon", Modifier.padding(top = 4.dp))
            FlowRow(
                Modifier.fillMaxWidth().clip(Resurge.shape.md).background(Resurge.colors.surface).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 5,
            ) {
                HabitVisuals.icons.forEach { (key, res) ->
                    val selected = key == icon
                    UnstyledButton(
                        onClick = { icon = key },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(Resurge.shape.sm)
                            .background(if (selected) HabitVisuals.fill(color) else Resurge.colors.surfaceMuted)
                            .semantics {
                                contentDescription = key.replace('_', ' ')
                                this.selected = selected
                            },
                        indication = LocalIndication.current,
                    ) { Icon(res, contentDescription = null, tint = if (selected) Resurge.colors.onAccent else Resurge.colors.textMuted, size = 22.dp) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        // Docked, so saving stays reachable above the keyboard and the icon grid.
        Box(Modifier.fillMaxWidth().background(Resurge.colors.bg).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 16.dp, vertical = 12.dp)) {
            Button(
                text = if (habitId == null) "Add habit" else "Save",
                onClick = save,
                enabled = canSave,
                variant = ButtonVariant.Accent,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Icons that say nothing about what a private habit is. */
private val NEUTRAL_ICONS = setOf("leaf", "star", "circle", "flame", "sun")

/** How the habit will look on Today once done: its colour fill, icon, name and a check. */
@Composable
private fun PreviewTile(name: String, icon: String, color: String) {
    val fill = HabitVisuals.fill(color)
    Row(
        Modifier.fillMaxWidth().clip(Resurge.shape.lg).background(fill).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(HabitVisuals.icon(icon), contentDescription = null, tint = Resurge.colors.onAccent, size = 32.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Preview", style = Resurge.type.caption, color = Resurge.colors.onAccent.copy(alpha = 0.8f))
            Text(name.ifBlank { "Your habit" }, style = Resurge.type.headline, color = Resurge.colors.onAccent, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(32.dp).clip(Resurge.shape.pill).background(Resurge.colors.onAccent), contentAlignment = Alignment.Center) {
            Icon(ResurgeIcons.Check, contentDescription = null, tint = fill, size = 18.dp)
        }
    }
}
