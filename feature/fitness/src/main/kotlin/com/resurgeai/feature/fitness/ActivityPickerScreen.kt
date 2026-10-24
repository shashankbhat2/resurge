package com.resurgeai.feature.fitness

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import com.composeunstyled.UnstyledButton
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.LocalIndication
import com.resurgeai.core.designsystem.components.Divider
import com.resurgeai.core.designsystem.components.SectionLabel
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.composeunstyled.Text
import com.resurgeai.core.data.fitness.WorkoutsRepository
import com.resurgeai.core.designsystem.components.ActivityVisuals
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.InputField
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.TopBar
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.Activity
import com.resurgeai.core.model.ActivityCatalog
import com.resurgeai.core.model.ActivityCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/** Recently logged activities, most recent first, for one-tap repeats. */
@HiltViewModel
class ActivityPickerViewModel @Inject constructor(repository: WorkoutsRepository) : ViewModel() {
    val recent: StateFlow<List<Activity>> = repository.records(Instant.now().minus(Duration.ofDays(60)))
        .map { records ->
            records.filter { !it.deleted }.sortedByDescending { it.start }
                .map { it.activityId }.distinct().take(5).map(ActivityCatalog::byId)
        }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/** Search the activity catalog, or pick from recent activities and categories. */
@Composable
fun ActivityPickerRoute(
    onPick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ActivityPickerViewModel = hiltViewModel(),
) {
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val query = rememberTextFieldState()
    val text by remember { snapshotFlow { query.text.toString() } }.collectAsState("")
    val results = remember(text) { ActivityCatalog.search(text) }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar("Choose activity", onBack = onBack)
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            InputField(query, placeholder = "Search activities (run, gym, yoga…)", maxLength = 40)
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        ) {
            if (text.isBlank()) {
                // Always the first item, even before recents load: the list anchors on its first
                // item, so adding Recent above Cardio later would leave the list scrolled to Cardio.
                item(key = "recent") { if (recent.isNotEmpty()) ActivityGroup("Recent", recent, onPick) }
                ActivityCategory.entries.forEach { category ->
                    item(key = "c_$category") { ActivityGroup(category.label, ActivityCatalog.all.filter { it.category == category }, onPick) }
                }
            } else if (results.isEmpty()) {
                item(key = "none") {
                    Text(
                        "No match. Pick \"Other workout\" and give it a name.",
                        style = Resurge.type.body,
                        color = Resurge.colors.textMuted,
                        modifier = Modifier.padding(vertical = 16.dp, horizontal = 4.dp),
                    )
                }
                item(key = "other") { ActivityGroup(null, listOf(ActivityCatalog.byId("other")), onPick) }
            } else {
                item(key = "results") { ActivityGroup("${results.size} ${if (results.size == 1) "match" else "matches"}", results, onPick) }
            }
            item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
        }
    }
}

/** A labelled card of activities, one row each, split by inset hairlines. */
@Composable
private fun ActivityGroup(title: String?, activities: List<Activity>, onPick: (String) -> Unit) {
    Column {
        if (title != null) SectionLabel(title) else Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth().clip(Resurge.shape.md).background(Resurge.colors.surface)) {
            activities.forEachIndexed { i, activity ->
                if (i > 0) Divider(Modifier.padding(start = 68.dp))
                ActivityRow(activity, onPick)
            }
        }
    }
}

@Composable
private fun ActivityRow(activity: Activity, onPick: (String) -> Unit) {
    UnstyledButton(
        onClick = { onPick(activity.id) },
        modifier = Modifier.fillMaxWidth(),
        indication = LocalIndication.current,
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(Resurge.shape.sm).background(Resurge.colors.surfaceMuted), contentAlignment = Alignment.Center) {
                Icon(ActivityVisuals.icon(activity.icon), contentDescription = null, tint = Resurge.colors.text, size = 22.dp)
            }
            Spacer(Modifier.width(12.dp))
            Text(activity.name, style = Resurge.type.body, modifier = Modifier.weight(1f))
            Icon(ResurgeIcons.ChevronRight, contentDescription = null, tint = Resurge.colors.textSubtle, size = 18.dp)
        }
    }
}
