package com.resurgeai.app.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.resurgeai.core.designsystem.components.Card
import com.resurgeai.core.designsystem.components.LocalHeaderInset
import com.resurgeai.core.designsystem.theme.Resurge

/** Placeholder for tabs that land in later phases. The tab title is shown by the shared header. */
@Composable
fun ComingSoon(body: String, phase: String) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(LocalHeaderInset.current + 8.dp))
        Card(Modifier.fillMaxWidth()) {
            Text(body, style = Resurge.type.body)
            Spacer(Modifier.height(6.dp))
            Text("Coming in $phase", style = Resurge.type.caption, color = Resurge.colors.textSubtle)
        }
    }
}
