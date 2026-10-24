package com.resurgeai.core.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import com.composeunstyled.Text
import com.resurgeai.core.designsystem.theme.Resurge
import java.time.LocalTime
import kotlin.math.roundToInt

/**
 * A stepper's value that can be typed over: tap it to edit in place, with a number keyboard. Done,
 * or tapping away, hands the text to [onSubmit]; text it can't use is dropped and the old value stays.
 * [display] is what shows normally ("7h 30m"); [editText] is what editing starts from ("7.5").
 */
@Composable
fun EditableValue(
    display: String,
    editText: () -> String,
    onSubmit: (String) -> Unit,
    label: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Resurge.colors.text,
    keyboardType: KeyboardType = KeyboardType.Decimal,
    textAlign: TextAlign = TextAlign.Center,
) {
    var editing by remember { mutableStateOf(false) }
    if (!editing) {
        Text(
            display,
            style = style,
            color = color,
            textAlign = textAlign,
            maxLines = 1,
            modifier = modifier
                .clickable(onClickLabel = "Type $label") { editing = true }
                .semantics { contentDescription = "$label: $display" },
        )
        return
    }

    val focus = remember { FocusRequester() }
    var field by remember { editText().let { mutableStateOf(TextFieldValue(it, TextRange(0, it.length))) } }
    var focused by remember { mutableStateOf(false) }
    val submit = {
        if (editing) {
            editing = false
            onSubmit(field.text)
        }
    }
    BasicTextField(
        value = field,
        onValueChange = { field = it },
        singleLine = true,
        textStyle = style.copy(color = color, textAlign = textAlign),
        cursorBrush = SolidColor(Resurge.colors.accent),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        modifier = modifier
            .focusRequester(focus)
            .onFocusChanged {
                if (focused && !it.isFocused) submit()
                focused = it.isFocused
            }
            .semantics { contentDescription = label },
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/** Parsing for typed stepper values. Commas are read as decimal points; thousands separators aren't expected. */
object TypedNumbers {
    /** "7.5", "7,5", " 80 " → the number, or null. */
    fun parse(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

    /** A whole number; commas and spaces are read as thousands separators ("2,000" → 2000). */
    fun parseInt(text: String): Int? = text.replace(",", "").replace(" ", "").trim().toDoubleOrNull()?.roundToInt()

    /** Tenths of a unit from a decimal ("72.4" → 724). */
    fun parseTenths(text: String): Int? = parse(text)?.let { (it * 10).roundToInt() }

    fun tenths(value: Int): String = "%.1f".format(value / 10.0)

    /** A clock time: "21:30", "21.30", "2130", "930" or "9" (hours). */
    fun parseClock(text: String): LocalTime? {
        val t = text.trim()
        val (h, m) = when {
            ':' in t || '.' in t -> t.split(':', '.').let { it.getOrNull(0)?.toIntOrNull() to (it.getOrNull(1)?.toIntOrNull() ?: 0) }
            t.length <= 2 -> t.toIntOrNull() to 0
            t.length in 3..4 -> t.dropLast(2).toIntOrNull() to t.takeLast(2).toIntOrNull()
            else -> null to null
        }
        if (h == null || m == null || h !in 0..23 || m !in 0..59) return null
        return LocalTime.of(h, m)
    }
}
