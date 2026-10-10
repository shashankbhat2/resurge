package com.resurgeai.app.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.resurgeai.core.designsystem.components.Avatar
import com.resurgeai.core.designsystem.components.Card
import com.resurgeai.core.designsystem.components.IconBadge
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.TopBar
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.UserProfile
import java.time.LocalTime

/** Temporary Today tab until the Health Connect dashboard lands in Phase 2. */
@Composable
fun TodayPlaceholder(profile: UserProfile) {
    val greeting = when (LocalTime.now().hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(greeting, style = Resurge.type.body, color = Resurge.colors.textMuted)
                Text(profile.firstName ?: "there", style = Resurge.type.display)
            }
            Avatar(profile.displayName, profile.photoUrl, size = 44.dp)
        }
        Spacer(Modifier.height(24.dp))
        Card(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(ResurgeIcons.Activity, tint = Resurge.colors.activity)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Health dashboard", style = Resurge.type.headline)
                    Text(
                        "Sleep, steps, calories and workouts from Health Connect — coming in Phase 2.",
                        style = Resurge.type.caption,
                        color = Resurge.colors.textMuted,
                    )
                }
            }
        }
    }
}

@Composable
fun ComingSoon(title: String, body: String, phase: String) {
    Column(Modifier.fillMaxSize()) {
        TopBar(title = title)
        Card(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(body, style = Resurge.type.body)
            Spacer(Modifier.height(4.dp))
            Text("Coming in $phase", style = Resurge.type.caption, color = Resurge.colors.textSubtle)
        }
    }
}
