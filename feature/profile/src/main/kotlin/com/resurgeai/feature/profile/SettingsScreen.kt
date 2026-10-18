package com.resurgeai.feature.profile

import com.resurgeai.core.designsystem.components.Avatar
import androidx.compose.foundation.layout.fillMaxSize
import com.resurgeai.core.designsystem.components.TopBar
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import com.resurgeai.core.designsystem.components.StepperRow
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.designsystem.components.Divider
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.SoftNote
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.Switch
import com.resurgeai.core.designsystem.components.TimeStepper
import com.resurgeai.core.designsystem.theme.LocalUse24HourClock
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.designsystem.theme.clockFormatter
import com.resurgeai.core.model.CoachStyle
import com.resurgeai.core.model.HabitSettings
import com.resurgeai.core.model.MealReminders
import com.resurgeai.core.model.Targets
import com.resurgeai.core.model.UnitSystem
import com.resurgeai.core.model.ThemePreference
import com.resurgeai.core.model.TimeFormat

private enum class Page(val title: String) {
    MAIN("Settings"),
    APPEARANCE("Appearance"),
    TIME_FORMAT("Time format"),
    COACH_STYLE("Coaching style"),
    HABITS("Habits"),
    MEALS("Meal reminders"),
    UNITS("Units"),
    TARGETS("Daily targets"),
}

/**
 * Settings as its own screen, opened from the profile photo: a flat menu (value on the right,
 * chevron for submenus) with simple option and control pages. Back on a page returns to the menu.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenCatalog: (() -> Unit)? = null,
    onOpenProgressPhotos: (() -> Unit)? = null,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var page by rememberSaveable { mutableStateOf(Page.MAIN) }
    BackHandler(enabled = page != Page.MAIN) { page = Page.MAIN }

    Column(Modifier.fillMaxSize().background(Resurge.colors.bg)) {
        TopBar(title = page.title, onBack = { if (page == Page.MAIN) onBack() else page = Page.MAIN })
        AnimatedContent(
            targetState = page,
            transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(100)) },
            label = "settings-page",
            modifier = Modifier.weight(1f),
        ) { current ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
            ) {
            when (current) {
                Page.MAIN -> MainMenu(
                    state = state,
                    onOpen = { page = it },
                    onHealthConnect = { runCatching { context.startActivity(viewModel.healthConnectSettingsIntent()) } },
                    onOpenCatalog = onOpenCatalog,
                    onOpenProgressPhotos = onOpenProgressPhotos,
                    onSignOut = viewModel::signOut,
                )
                Page.APPEARANCE -> Options(
                    options = listOf(ThemePreference.SYSTEM to "System", ThemePreference.LIGHT to "Light", ThemePreference.DARK to "Dark"),
                    selected = state.theme,
                    onSelect = viewModel::setTheme,
                )
                Page.TIME_FORMAT -> {
                    val use24 = LocalUse24HourClock.current
                    Options(
                        options = listOf(TimeFormat.H12 to "12-hour", TimeFormat.H24 to "24-hour"),
                        selected = state.timeFormat ?: if (use24) TimeFormat.H24 else TimeFormat.H12,
                        onSelect = viewModel::setTimeFormat,
                    )
                }
                Page.COACH_STYLE -> {
                    Options(
                        options = CoachStyle.entries.map { it to it.label },
                        selected = state.profile?.coachStyle ?: CoachStyle.FRIEND,
                        onSelect = viewModel::setCoachStyle,
                        descriptions = CoachStyle.entries.associateWith { it.description },
                    )
                    Footnote("Sets how your coach talks in chat, insights and your weekly review.")
                }
                Page.HABITS -> state.profile?.habitSettings?.let { HabitsPage(it, viewModel::setHabitSettings) }
                Page.MEALS -> state.profile?.mealReminders?.let { MealsPage(it, viewModel::setMealReminders) }
                Page.UNITS -> Options(
                    options = UnitSystem.entries.map { it to it.label },
                    selected = state.profile?.units ?: UnitSystem.METRIC,
                    onSelect = viewModel::setUnits,
                )
                Page.TARGETS -> state.profile?.targets?.let { TargetsPage(it, viewModel::setTargets) }
            }
            }
        }
    }
}

@Composable
private fun MainMenu(
    state: ProfileUiState,
    onOpen: (Page) -> Unit,
    onHealthConnect: () -> Unit,
    onOpenCatalog: (() -> Unit)?,
    onOpenProgressPhotos: (() -> Unit)?,
    onSignOut: () -> Unit,
) {
    val profile = state.profile
    val use24 = LocalUse24HourClock.current
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        profile?.let {
            MenuGroup {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(it.displayName, it.photoUrl, size = 48.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(it.displayName ?: "You", style = Resurge.type.headline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        it.email?.let { email -> Text(email, style = Resurge.type.caption, color = Resurge.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
        }

        MenuLabel("Preferences")
        MenuGroup {
            MenuRow("Appearance", state.theme.name.lowercase().replaceFirstChar { it.uppercase() }) { onOpen(Page.APPEARANCE) }
            MenuDivider()
            MenuRow("Time format", if ((state.timeFormat ?: if (use24) TimeFormat.H24 else TimeFormat.H12) == TimeFormat.H24) "24-hour" else "12-hour") { onOpen(Page.TIME_FORMAT) }
            profile?.let {
                MenuDivider()
                MenuRow("Units", if (it.units == UnitSystem.METRIC) "Metric" else "Imperial") { onOpen(Page.UNITS) }
            }
        }

        MenuLabel("Coach and goals")
        MenuGroup {
            MenuRow("Coaching style", (profile?.coachStyle ?: CoachStyle.FRIEND).label) { onOpen(Page.COACH_STYLE) }
            profile?.let { p ->
                MenuDivider()
                MenuRow("Daily targets", "%,d kcal · %s sleep".format(p.targets.calories, Targets.formatSleep(p.targets.sleepMinutes))) { onOpen(Page.TARGETS) }
                MenuDivider()
                MenuRow("Habits", "Resets ${p.habitSettings.resetTime.format(clockFormatter())}") { onOpen(Page.HABITS) }
                MenuDivider()
                val on = MealReminders.DEFAULT_TIMES.keys.count { p.mealReminders.time(it) != null }
                MenuRow("Meal reminders", if (on == 0) "Off" else "$on a day") { onOpen(Page.MEALS) }
            }
        }

        MenuLabel("Data")
        MenuGroup {
            MenuRow("Health Connect", "Permissions", onClick = onHealthConnect)
            onOpenProgressPhotos?.let {
                MenuDivider()
                MenuRow("Snapshots", null, onClick = it)
            }
            onOpenCatalog?.let {
                MenuDivider()
                MenuRow("Design catalog", "Debug", onClick = it)
            }
        }

        Spacer(Modifier.height(8.dp))
        MenuGroup {
            MenuRow("Sign out", null, chevron = false, danger = true, onClick = onSignOut)
        }
        Text(
            "Resurge 0.1.0",
            style = Resurge.type.caption,
            color = Resurge.colors.textSubtle,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun MenuLabel(text: String) {
    Text(
        text.uppercase(),
        style = Resurge.type.overline,
        color = Resurge.colors.textMuted,
        modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 2.dp),
    )
}

@Composable
private fun MenuGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(Resurge.shape.md)
            .background(Resurge.colors.surface),
        content = content,
    )
}

@Composable
private fun MenuDivider() = Divider(Modifier.padding(start = 16.dp))

/** A settings row: label on the left; value and chevron always aligned to the right edge. */
@Composable
private fun MenuRow(
    label: String,
    value: String?,
    chevron: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    UnstyledButton(onClick = onClick, indication = LocalIndication.current, modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = Resurge.type.body, color = if (danger) Resurge.colors.danger else Resurge.colors.text, maxLines = 1)
            Spacer(Modifier.width(16.dp))
            // Takes the rest of the row and right-aligns the value, so every row's value lines up.
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                value?.let { Text(it, style = Resurge.type.body, color = Resurge.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            if (chevron) {
                Spacer(Modifier.width(6.dp))
                Icon(ResurgeIcons.ChevronRight, contentDescription = null, tint = Resurge.colors.textSubtle, size = 18.dp)
            }
        }
    }
}

@Composable
private fun <T> Options(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    descriptions: Map<T, String> = emptyMap(),
) {
    options.forEach { (value, label) ->
        val isSelected = value == selected
        UnstyledButton(
            onClick = { onSelect(value) },
            indication = LocalIndication.current,
            modifier = Modifier.fillMaxWidth().semantics { this.selected = isSelected },
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(label, style = Resurge.type.body)
                    descriptions[value]?.let { Text(it, style = Resurge.type.caption, color = Resurge.colors.textMuted) }
                }
                if (isSelected) Icon(ResurgeIcons.Check, contentDescription = "Selected", tint = Resurge.colors.accent, size = 20.dp)
            }
        }
    }
}

@Composable
private fun HabitsPage(settings: HabitSettings, onChange: (HabitSettings) -> Unit) {
    Column(Modifier.padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("New day starts at", style = Resurge.type.label, color = Resurge.colors.textMuted)
        Spacer(Modifier.height(12.dp))
        TimeStepper(settings.resetTime, { onChange(settings.copy(resetTime = it)) }, caption = "Habits reset daily at this time")
        Spacer(Modifier.height(20.dp))
        Divider()
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Evening reminder", style = Resurge.type.body)
                Text("Only if habits are still unlogged", style = Resurge.type.caption, color = Resurge.colors.textMuted)
            }
            Switch(settings.reminderEnabled, { onChange(settings.copy(reminderEnabled = it)) }, accessibilityLabel = "Evening habit reminder")
        }
        if (settings.reminderEnabled) {
            TimeStepper(settings.reminderTime, { onChange(settings.copy(reminderTime = it)) }, caption = "Reminder time")
        }
    }
}

@Composable
private fun MealsPage(reminders: MealReminders, onChange: (MealReminders) -> Unit) {
    Column(Modifier.padding(horizontal = 24.dp)) {
        MealReminders.DEFAULT_TIMES.forEach { (type, default) ->
            val time = reminders.time(type)
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(type.label, style = Resurge.type.body)
                    Text(if (time != null) "Reminds at ${time.format(clockFormatter())}" else "Off", style = Resurge.type.caption, color = Resurge.colors.textMuted)
                }
                Switch(time != null, { on -> onChange(reminders.with(type, if (on) default else null)) }, accessibilityLabel = "${type.label} reminder")
            }
            if (time != null) TimeStepper(time, { onChange(reminders.with(type, it)) }, caption = "${type.label} reminder", stepMinutes = 15)
            Divider(Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Only sent if you haven't logged that meal yet. Tap Scan in the notification to open the camera.",
            style = Resurge.type.caption,
            color = Resurge.colors.textSubtle,
        )
    }
}

/** Daily targets in two groups: food and water, then the sleep and step goals that count toward the tide. */
@Composable
private fun TargetsPage(targets: Targets, onChange: (Targets) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GroupLabel("Food and water")
        TargetGroup {
            StepperRow(
                label = "Calories", hint = "Eaten per day", icon = ResurgeIcons.Flame, iconTint = Resurge.colors.energy,
                value = targets.calories, onValueChange = { onChange(targets.copy(calories = it)) },
                step = 50, range = 1200..4500, format = { "%,d".format(it) },
            )
            Divider(Modifier.padding(start = 50.dp))
            StepperRow(
                label = "Water", hint = "ml per day", icon = ResurgeIcons.Droplet, iconTint = Resurge.colors.water,
                value = targets.waterMl, onValueChange = { onChange(targets.copy(waterMl = it)) },
                step = 250, range = 1000..5000, format = { "%,d".format(it) },
            )
        }
        GroupLabel("Counts toward your tide")
        TargetGroup {
            StepperRow(
                label = "Sleep", hint = "Asleep per night", icon = ResurgeIcons.Bed, iconTint = Resurge.colors.sleep,
                value = targets.sleepMinutes, onValueChange = { onChange(targets.copy(sleepMinutes = it)) },
                step = Targets.SLEEP_STEP, range = Targets.SLEEP_RANGE, format = Targets::formatSleep,
            )
            Targets.sleepGoalNote(targets.sleepMinutes)?.let {
                SoftNote(it, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp))
            }
            Divider(Modifier.padding(start = 50.dp))
            StepperRow(
                label = "Steps", hint = "Per day", icon = ResurgeIcons.Footprints, iconTint = Resurge.colors.activity,
                value = targets.steps, onValueChange = { onChange(targets.copy(steps = it)) },
                step = Targets.STEPS_STEP, range = Targets.STEPS_RANGE, format = { "%,d".format(it) },
            )
        }
        Text(
            "When last night's sleep or today's steps reach these goals, they fill a share of today's tide.",
            style = Resurge.type.caption,
            color = Resurge.colors.textSubtle,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text.uppercase(),
        style = Resurge.type.overline,
        color = Resurge.colors.textMuted,
        modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun TargetGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(Resurge.shape.md)
            .background(Resurge.colors.surface),
        content = content,
    )
}

@Composable
private fun Footnote(text: String) {
    Box(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(text, style = Resurge.type.caption, color = Resurge.colors.textSubtle)
    }
}
