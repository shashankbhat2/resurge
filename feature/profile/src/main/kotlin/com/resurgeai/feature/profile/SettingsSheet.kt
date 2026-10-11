package com.resurgeai.feature.profile

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composeunstyled.DragIndication
import com.composeunstyled.Scrim
import com.composeunstyled.Sheet
import com.composeunstyled.SheetDetent
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.composeunstyled.UnstyledModalBottomSheet
import com.composeunstyled.rememberModalBottomSheetState
import com.resurgeai.core.designsystem.components.Divider
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.NumberStepper
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.Switch
import com.resurgeai.core.designsystem.components.TimeStepper
import com.resurgeai.core.designsystem.theme.LocalUse24HourClock
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.designsystem.theme.clockFormatter
import com.resurgeai.core.model.CoachStyle
import com.resurgeai.core.model.HabitSettings
import com.resurgeai.core.model.Targets
import com.resurgeai.core.model.ThemePreference
import com.resurgeai.core.model.TimeFormat

private enum class Page(val title: String) {
    MAIN("Settings"),
    APPEARANCE("Appearance"),
    TIME_FORMAT("Time format"),
    COACH_STYLE("Coaching style"),
    HABITS("Habits"),
    TARGETS("Daily targets"),
}

/**
 * Settings as a bottom sheet, opened from the profile photo: a flat menu (value on the right,
 * chevron for submenus) with simple option / control pages.
 */
@Composable
fun SettingsSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onOpenCatalog: (() -> Unit)? = null,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val sheet = rememberModalBottomSheetState(initialDetent = SheetDetent.Hidden, animationSpec = tween(260))
    var page by remember { mutableStateOf(Page.MAIN) }

    LaunchedEffect(visible) {
        if (visible) page = Page.MAIN
        sheet.targetDetent = if (visible) SheetDetent.FullyExpanded else SheetDetent.Hidden
    }

    UnstyledModalBottomSheet(
        state = sheet,
        onDismiss = onDismiss,
        overlay = { Scrim(scrimColor = Color.Black.copy(alpha = 0.45f), enter = fadeIn(), exit = fadeOut()) },
    ) {
        Sheet(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Resurge.colors.bg),
        ) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                DragIndication(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 10.dp, bottom = 6.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(Resurge.shape.pill)
                        .background(Resurge.colors.border),
                )
                AnimatedContent(
                    targetState = page,
                    transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(100)) },
                    label = "settings-page",
                ) { current ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 640.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 16.dp),
                    ) {
                        PageHeader(current, onBack = { page = Page.MAIN })
                        when (current) {
                            Page.MAIN -> MainMenu(
                                state = state,
                                onOpen = { page = it },
                                onHealthConnect = { runCatching { context.startActivity(viewModel.healthConnectSettingsIntent()) } },
                                onOpenCatalog = onOpenCatalog?.let { open -> { onDismiss(); open() } },
                                onSignOut = { onDismiss(); viewModel.signOut() },
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
                            Page.TARGETS -> state.profile?.targets?.let { TargetsPage(it, viewModel::setTargets) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageHeader(page: Page, onBack: () -> Unit) {
    if (page == Page.MAIN) {
        Text(page.title, style = Resurge.type.title, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
    } else {
        UnstyledButton(onClick = onBack, indication = LocalIndication.current, modifier = Modifier.padding(start = 12.dp).clip(Resurge.shape.pill)) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(ResurgeIcons.ArrowLeft, contentDescription = "Back", tint = Resurge.colors.text, size = 20.dp)
                Spacer(Modifier.width(10.dp))
                Text(page.title, style = Resurge.type.title)
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
    onSignOut: () -> Unit,
) {
    val profile = state.profile
    profile?.let {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp)) {
            Text(it.displayName ?: "You", style = Resurge.type.headline)
            it.email?.let { email -> Text(email, style = Resurge.type.caption, color = Resurge.colors.textMuted) }
        }
        Divider()
    }
    val use24 = LocalUse24HourClock.current
    MenuRow("Appearance", state.theme.name.lowercase().replaceFirstChar { it.uppercase() }) { onOpen(Page.APPEARANCE) }
    MenuRow("Time format", if ((state.timeFormat ?: if (use24) TimeFormat.H24 else TimeFormat.H12) == TimeFormat.H24) "24-hour" else "12-hour") { onOpen(Page.TIME_FORMAT) }
    MenuRow("Coaching style", (profile?.coachStyle ?: CoachStyle.FRIEND).label) { onOpen(Page.COACH_STYLE) }
    profile?.habitSettings?.let { h ->
        MenuRow("Habits", "Resets ${h.resetTime.format(clockFormatter())}") { onOpen(Page.HABITS) }
    }
    profile?.targets?.let { t ->
        MenuRow("Daily targets", "%,d kcal · %,d ml".format(t.calories, t.waterMl)) { onOpen(Page.TARGETS) }
    }
    MenuRow("Health Connect", "Permissions", chevron = false, onClick = onHealthConnect)
    onOpenCatalog?.let { MenuRow("Design catalog", "Debug", onClick = it) }
    Divider(Modifier.padding(vertical = 4.dp))
    MenuRow("Sign out", null, chevron = false, danger = true, onClick = onSignOut)
    Text(
        "Resurge 0.1.0",
        style = Resurge.type.caption,
        color = Resurge.colors.textSubtle,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
    )
}

@Composable
private fun MenuRow(
    label: String,
    value: String?,
    chevron: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    UnstyledButton(onClick = onClick, indication = LocalIndication.current, modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = Resurge.type.body, color = if (danger) Resurge.colors.danger else Resurge.colors.text, modifier = Modifier.weight(1f))
            value?.let { Text(it, style = Resurge.type.body, color = Resurge.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
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
private fun TargetsPage(targets: Targets, onChange: (Targets) -> Unit) {
    Column(Modifier.padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("Calories eaten", style = Resurge.type.label, color = Resurge.colors.textMuted)
        Spacer(Modifier.height(12.dp))
        NumberStepper(targets.calories, { onChange(targets.copy(calories = it)) }, step = 50, range = 1200..4500, unit = "kcal per day")
        Spacer(Modifier.height(20.dp))
        Divider()
        Spacer(Modifier.height(20.dp))
        Text("Water", style = Resurge.type.label, color = Resurge.colors.textMuted)
        Spacer(Modifier.height(12.dp))
        NumberStepper(targets.waterMl, { onChange(targets.copy(waterMl = it)) }, step = 250, range = 1000..5000, unit = "ml per day")
    }
}

@Composable
private fun Footnote(text: String) {
    Box(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(text, style = Resurge.type.caption, color = Resurge.colors.textSubtle)
    }
}
