package com.resurgeai.feature.profile

import androidx.annotation.DrawableRes
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.designsystem.components.Avatar
import com.resurgeai.core.designsystem.components.Button
import com.resurgeai.core.designsystem.components.ButtonVariant
import com.resurgeai.core.designsystem.components.Card
import com.resurgeai.core.designsystem.components.Divider
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.IconBadge
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.SegmentOption
import com.resurgeai.core.designsystem.components.SegmentedControl
import com.resurgeai.core.designsystem.components.TopBar
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.designsystem.theme.LocalUse24HourClock
import com.resurgeai.core.designsystem.theme.ResurgeTheme
import com.resurgeai.core.model.Goal
import com.resurgeai.core.model.Targets
import com.resurgeai.core.model.ThemePreference
import com.resurgeai.core.model.TimeFormat
import com.resurgeai.core.model.UserProfile

@Composable
fun ProfileRoute(
    onBack: () -> Unit,
    onOpenCatalog: (() -> Unit)? = null,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ProfileScreen(
        state = state,
        onBack = onBack,
        onThemeChange = viewModel::setTheme,
        onTimeFormatChange = viewModel::setTimeFormat,
        onSignOut = viewModel::signOut,
        onOpenHealthConnect = { runCatching { context.startActivity(viewModel.healthConnectSettingsIntent()) } },
        onOpenCatalog = onOpenCatalog,
    )
}

@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onBack: () -> Unit,
    onThemeChange: (ThemePreference) -> Unit,
    onTimeFormatChange: (TimeFormat) -> Unit,
    onSignOut: () -> Unit,
    onOpenHealthConnect: () -> Unit = {},
    onOpenCatalog: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxSize()) {
        TopBar(title = "Settings", onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            state.profile?.let { profile ->
                Card(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(profile.displayName, profile.photoUrl, size = 56.dp)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(profile.displayName ?: "You", style = Resurge.type.headline)
                            profile.email?.let {
                                Text(it, style = Resurge.type.caption, color = Resurge.colors.textMuted)
                            }
                        }
                    }
                }

                SectionLabel("Daily targets")
                Card(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                    InfoRow(ResurgeIcons.Flame, Resurge.colors.energy, "Calories", "%,d kcal".format(profile.targets.calories))
                    Divider(Modifier.padding(start = 64.dp))
                    InfoRow(ResurgeIcons.Droplet, Resurge.colors.water, "Water", "%,d ml".format(profile.targets.waterMl))
                }

                if (profile.goals.isNotEmpty()) {
                    SectionLabel("Goals")
                    Card(Modifier.fillMaxWidth()) {
                        Text(profile.goals.joinToString { it.label }, style = Resurge.type.body)
                    }
                }
            }

            SectionLabel("Appearance")
            SegmentedControl(
                options = listOf(
                    SegmentOption(ThemePreference.SYSTEM, "System", ResurgeIcons.Monitor),
                    SegmentOption(ThemePreference.LIGHT, "Light", ResurgeIcons.Sun),
                    SegmentOption(ThemePreference.DARK, "Dark", ResurgeIcons.Moon),
                ),
                selected = state.theme,
                onSelect = onThemeChange,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel("Time format")
            val use24 = LocalUse24HourClock.current
            SegmentedControl(
                options = listOf(
                    SegmentOption(TimeFormat.H12, "12-hour"),
                    SegmentOption(TimeFormat.H24, "24-hour"),
                ),
                // Unset = device default, which is what LocalUse24HourClock already reflects.
                selected = state.timeFormat ?: if (use24) TimeFormat.H24 else TimeFormat.H12,
                onSelect = onTimeFormatChange,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel("Data")
            Card(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                LinkRow(ResurgeIcons.Activity, Resurge.colors.activity, "Health Connect", "Permissions and data sources", onOpenHealthConnect)
                if (onOpenCatalog != null) {
                    Divider(Modifier.padding(start = 64.dp))
                    LinkRow(ResurgeIcons.Sparkles, Resurge.colors.accent, "Design catalog", "Debug builds only", onOpenCatalog)
                }
            }

            Spacer(Modifier.height(32.dp))
            Button(
                text = "Sign out",
                onClick = onSignOut,
                variant = ButtonVariant.Danger,
                leadingIcon = ResurgeIcons.LogOut,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Resurge 0.1.0",
                style = Resurge.type.caption,
                color = Resurge.colors.textSubtle,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
            )
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = Resurge.type.caption,
        color = Resurge.colors.textSubtle,
        modifier = Modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun InfoRow(@DrawableRes icon: Int, tint: Color, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, tint = tint, size = 32.dp)
        Spacer(Modifier.width(16.dp))
        Text(label, style = Resurge.type.label, modifier = Modifier.weight(1f))
        Text(value, style = Resurge.type.label, color = Resurge.colors.textMuted)
    }
}

@Composable
private fun LinkRow(@DrawableRes icon: Int, tint: Color, title: String, subtitle: String, onClick: () -> Unit) {
    UnstyledButton(
        onClick = onClick,
        indication = LocalIndication.current,
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(icon, tint = tint, size = 32.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = Resurge.type.label)
                Text(subtitle, style = Resurge.type.caption, color = Resurge.colors.textMuted)
            }
            Icon(ResurgeIcons.ChevronRight, contentDescription = null, tint = Resurge.colors.textSubtle, size = 18.dp)
        }
    }
}

@Preview
@Composable
private fun ProfilePreview() {
    ResurgeTheme {
        ProfileScreen(
            state = ProfileUiState(
                profile = UserProfile(
                    uid = "1", displayName = "Shashank Bhat", email = "me@example.com", photoUrl = null,
                    goals = setOf(Goal.SLEEP_BETTER, Goal.MOVE_MORE), targets = Targets(), onboardingComplete = true,
                ),
            ),
            onBack = {},
            onThemeChange = {},
            onTimeFormatChange = {},
            onSignOut = {},
        )
    }
}
