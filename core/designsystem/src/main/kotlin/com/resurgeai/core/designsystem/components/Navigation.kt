package com.resurgeai.core.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.designsystem.theme.Resurge

data class BottomBarItem<T>(val key: T, val label: String, @DrawableRes val icon: Int)

@Composable
fun <T> BottomBar(
    items: List<BottomBarItem<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.background(Resurge.colors.surface)) {
        Divider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            items.forEach { item ->
                val isSelected = item.key == selected
                val color = if (isSelected) Resurge.colors.text else Resurge.colors.textSubtle
                UnstyledButton(
                    onClick = { onSelect(item.key) },
                    role = Role.Tab,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { this.selected = isSelected },
                    indication = LocalIndication.current,
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(item.icon, contentDescription = null, tint = color, size = 22.dp)
                        Spacer(Modifier.height(4.dp))
                        Text(item.label, style = Resurge.type.caption, color = color)
                    }
                }
            }
        }
    }
}

/** Simple top bar: optional back button, title, optional trailing action. Applies status bar insets. */
@Composable
fun TopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        if (onBack != null) {
            IconButton(ResurgeIcons.ArrowLeft, contentDescription = "Back", onClick = onBack)
        } else {
            Spacer(Modifier.width(8.dp))
        }
        Text(
            title,
            style = Resurge.type.title,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        )
        trailing?.invoke()
    }
}
