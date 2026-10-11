package com.resurgeai.feature.fitness

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.lazy.items
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
import com.resurgeai.core.designsystem.components.Card
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.IconBadge
import com.resurgeai.core.designsystem.components.InputField
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.SectionHeader
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
        TopBar("Log a workout", onBack = onBack)
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            InputField(query, placeholder = "Search activities (run, gym, yoga…)", maxLength = 40)
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (text.isBlank()) {
                if (recent.isNotEmpty()) {
                    item(key = "recent") { SectionHeader("Recent") }
                    items(recent, key = { "r_${it.id}" }) { ActivityRow(it, onPick) }
                }
                ActivityCategory.entries.forEach { category ->
                    item(key = "c_$category") { SectionHeader(category.label) }
                    items(ActivityCatalog.all.filter { it.category == category }, key = { it.id }) { ActivityRow(it, onPick) }
                }
            } else {
                if (results.isEmpty()) {
                    item(key = "none") {
                        Text(
                            "No match. Try \"Other workout\" and give it a name.",
                            style = Resurge.type.body,
                            color = Resurge.colors.textMuted,
                            modifier = Modifier.padding(vertical = 16.dp, horizontal = 4.dp),
                        )
                    }
                    item(key = "other") { ActivityRow(ActivityCatalog.byId("other"), onPick) }
                }
                items(results, key = { it.id }) { ActivityRow(it, onPick) }
            }
            item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
        }
    }
}

@Composable
private fun ActivityRow(activity: Activity, onPick: (String) -> Unit) {
    Card(Modifier.fillMaxWidth(), onClick = { onPick(activity.id) }, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(ActivityVisuals.icon(activity.icon), tint = ActivityVisuals.color(activity.category), size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(activity.name, style = Resurge.type.label)
                Text(activity.category.label, style = Resurge.type.caption, color = Resurge.colors.textMuted)
            }
            Icon(ResurgeIcons.ChevronRight, contentDescription = null, tint = Resurge.colors.textSubtle, size = 18.dp)
        }
    }
}
