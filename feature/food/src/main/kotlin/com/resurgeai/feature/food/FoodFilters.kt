package com.resurgeai.feature.food

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.TextInput
import com.composeunstyled.UnstyledButton
import com.composeunstyled.UnstyledTextField
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.Overline
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.MealType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FilterBar(
    state: FoodUiState,
    onRange: (FoodFeed.Range) -> Unit,
    onPickDate: (LocalDate) -> Unit,
    onToggleType: (MealType) -> Unit,
    onClearTypes: () -> Unit,
    onSort: (FoodFeed.Sort) -> Unit,
    onQuery: (String) -> Unit,
) {
    val filters = state.filters
    Column(
        Modifier.fillMaxWidth().clip(Resurge.shape.md).background(Resurge.colors.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SearchField(filters.query, onQuery)
        Overline("Range")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FoodFeed.Range.entries.forEach { range ->
                ChoiceChip(range.label, selected = filters.range == range) { onRange(range) }
            }
        }
        if (filters.range == FoodFeed.Range.CUSTOM) {
            val from = state.rangeStart
            val to = state.rangeEnd
            Text(
                if (from == to) from.format(customDay) else "${from.format(customDay)} – ${to.format(customDay)}",
                style = Resurge.type.caption.copy(fontFeatureSettings = "tnum"),
                color = Resurge.colors.textMuted,
            )
            RangeCalendar(from = from, to = to, today = state.today, onPick = onPickDate)
        }
        Overline("Meal")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip("All", selected = filters.types.isEmpty(), onClick = onClearTypes)
            MealType.entries.forEach { type ->
                ChoiceChip(
                    if (type == MealType.SNACK) "Snack" else type.label,
                    selected = type in filters.types,
                ) { onToggleType(type) }
            }
        }
        Overline("Sort")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FoodFeed.Sort.entries.forEach { sort ->
                ChoiceChip(sort.label, selected = filters.sort == sort) { onSort(sort) }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    val search = rememberTextFieldState(query)
    LaunchedEffect(search) {
        snapshotFlow { search.text.toString() }.collect(onQuery)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Resurge.shape.sm)
            .background(Resurge.colors.surfaceMuted)
            .border(1.dp, Resurge.colors.border, Resurge.shape.sm)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(ResurgeIcons.Search, contentDescription = null, tint = Resurge.colors.textSubtle, size = 16.dp)
        Spacer(Modifier.width(8.dp))
        UnstyledTextField(
            state = search,
            modifier = Modifier.weight(1f).semantics { contentDescription = "Search meals" },
            textStyle = Resurge.type.body,
            textColor = Resurge.colors.text,
            cursorBrush = SolidColor(Resurge.colors.accent),
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        ) {
            TextInput(placeholder = { Text("Search meals and foods", style = Resurge.type.body, color = Resurge.colors.textSubtle) })
        }
    }
}

@Composable
internal fun RangeCalendar(from: LocalDate, to: LocalDate, today: LocalDate, onPick: (LocalDate) -> Unit) {
    var month by remember(from) { mutableStateOf(YearMonth.from(from)) }
    val first = month.atDay(1)
    val startPad = (first.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
    val days = month.lengthOfMonth()
    val cells = startPad + days
    val rows = (cells + 6) / 7
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            UnstyledButton(
                onClick = { month = month.minusMonths(1) },
                role = Role.Button,
                modifier = Modifier.size(36.dp).clip(Resurge.shape.sm).semantics { contentDescription = "Previous month" },
                indication = LocalIndication.current,
            ) { Icon(ResurgeIcons.ArrowLeft, contentDescription = null, tint = Resurge.colors.text, size = 18.dp) }
            Text(
                month.format(monthTitle),
                style = Resurge.type.label.copy(fontWeight = Resurge.type.headline.fontWeight),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            UnstyledButton(
                onClick = { month = month.plusMonths(1) },
                enabled = month.isBefore(YearMonth.from(today)),
                role = Role.Button,
                modifier = Modifier.size(36.dp).clip(Resurge.shape.sm).semantics { contentDescription = "Next month" },
                indication = LocalIndication.current,
            ) { Icon(ResurgeIcons.ArrowRight, contentDescription = null, tint = Resurge.colors.text, size = 18.dp) }
        }
        Row(Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach { dow ->
                Text(
                    dow.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = Resurge.type.caption,
                    color = Resurge.colors.textSubtle,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(rows) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(7) { col ->
                        val index = row * 7 + col
                        val dayNum = index - startPad + 1
                        if (dayNum in 1..days) {
                            val date = month.atDay(dayNum)
                            val inRange = date in from..to
                            val isEnd = date == from || date == to
                            val future = date.isAfter(today)
                            UnstyledButton(
                                onClick = { onPick(date) },
                                enabled = !future,
                                role = Role.Button,
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(Resurge.shape.sm)
                                    .background(
                                        when {
                                            isEnd -> Resurge.colors.accent
                                            inRange -> Resurge.colors.accentMuted
                                            else -> Color.Transparent
                                        },
                                    )
                                    .semantics {
                                        contentDescription = date.toString()
                                        selected = inRange
                                    },
                                contentPadding = PaddingValues(0.dp),
                                indication = LocalIndication.current,
                            ) {
                                Text(
                                    dayNum.toString(),
                                    style = Resurge.type.caption.copy(fontFeatureSettings = "tnum"),
                                    color = when {
                                        future -> Resurge.colors.textSubtle
                                        isEnd -> Resurge.colors.onAccent
                                        else -> Resurge.colors.text
                                    },
                                    textAlign = TextAlign.Center,
                                )
                            }
                        } else {
                            Spacer(Modifier.weight(1f).aspectRatio(1f))
                        }
                    }
                }
            }
        }
        Text("Tap a day, then another, for a range.", style = Resurge.type.caption, color = Resurge.colors.textSubtle)
    }
}

private val monthTitle: DateTimeFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault())
private val customDay: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
