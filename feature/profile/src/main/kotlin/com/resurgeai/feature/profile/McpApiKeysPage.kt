package com.resurgeai.feature.profile

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composeunstyled.Text
import com.resurgeai.core.designsystem.components.Button
import com.resurgeai.core.designsystem.components.ButtonVariant
import com.resurgeai.core.designsystem.components.Divider
import com.resurgeai.core.designsystem.components.IconButton
import com.resurgeai.core.designsystem.components.InputField
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.SecureWindow
import com.resurgeai.core.designsystem.components.SoftNote
import com.resurgeai.core.designsystem.components.Spinner
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.McpApiKey
import com.resurgeai.core.model.NewMcpApiKey
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Max key name length; matches the createMcpApiKey callable. */
private const val MAX_NAME = 40

/**
 * Settings → MCP API keys: personal keys an AI agent sends as `Authorization: Bearer rsg_...` to
 * the MCP server. Lists keys (name, last 4, created, last used), makes one (shown once) and
 * revokes one after a confirmation.
 */
@Composable
internal fun McpApiKeysPage(viewModel: McpApiKeysViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var revoking by remember { mutableStateOf<McpApiKey?>(null) }

    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Let an AI agent read your Resurge data and log meals, water and habits through the MCP server. Each key has full access to your account, so only share it with apps you trust.",
            style = Resurge.type.caption,
            color = Resurge.colors.textMuted,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
        when {
            state.loading && state.keys.isEmpty() -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Spinner() }
            state.keys.isEmpty() -> SoftNote("No keys yet.", Modifier.padding(horizontal = 8.dp))
            else -> Column(Modifier.fillMaxWidth().clip(Resurge.shape.md).background(Resurge.colors.surface)) {
                state.keys.forEachIndexed { i, key ->
                    if (i > 0) Divider(Modifier.padding(start = 16.dp))
                    KeyRow(key, enabled = !state.busy, onRevoke = { revoking = key })
                }
            }
        }
        if (!creating && revoking == null) {
            state.error?.let { Text(it, style = Resurge.type.caption, color = Resurge.colors.danger, modifier = Modifier.padding(horizontal = 8.dp)) }
        }
        Spacer(Modifier.size(4.dp))
        Button(
            "New key",
            onClick = { viewModel.clearError(); creating = true },
            variant = ButtonVariant.Secondary,
            leadingIcon = ResurgeIcons.Plus,
            enabled = !state.loading || state.keys.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    val created = state.created
    when {
        created != null -> NewKeySheet(created, onDone = {
            viewModel.dismissCreated()
            creating = false
        })
        creating -> CreateKeySheet(
            busy = state.busy,
            error = state.error,
            onCreate = viewModel::create,
            onDismiss = { creating = false },
        )
    }

    revoking?.let { key ->
        KeySheet(onDismiss = { revoking = null }) {
            Text("Revoke \"${key.name}\"?", style = Resurge.type.title)
            Text(
                "Anything using ${key.masked} loses access right away. This can't be undone.",
                style = Resurge.type.body,
                color = Resurge.colors.textMuted,
            )
            Button("Revoke key", onClick = {
                revoking = null
                viewModel.revoke(key.id)
            }, variant = ButtonVariant.Danger, modifier = Modifier.fillMaxWidth())
            Button("Cancel", onClick = { revoking = null }, variant = ButtonVariant.Secondary, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun KeyRow(key: McpApiKey, enabled: Boolean, onRevoke: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(key.name, style = Resurge.type.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(key.masked, style = Resurge.type.caption.copy(fontFamily = FontFamily.Monospace), color = Resurge.colors.textMuted)
            Text(
                "Created ${formatDay(key.createdAt)} · " + (key.lastUsedAt?.let { "Last used ${formatDay(it)}" } ?: "Never used"),
                style = Resurge.type.caption,
                color = Resurge.colors.textSubtle,
            )
        }
        Spacer(Modifier.width(8.dp))
        IconButton(ResurgeIcons.Trash, contentDescription = "Revoke ${key.name}", onClick = onRevoke, tint = Resurge.colors.danger, enabled = enabled)
    }
}

@Composable
private fun CreateKeySheet(busy: Boolean, error: String?, onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    val name = rememberTextFieldState()
    val submit = { if (name.text.isNotBlank()) onCreate(name.text.toString()) }
    KeySheet(onDismiss = { if (!busy) onDismiss() }) {
        Text("New API key", style = Resurge.type.title)
        Text("Name it after the app that will use it, so you know which one to revoke later.", style = Resurge.type.body, color = Resurge.colors.textMuted)
        InputField(name, placeholder = "e.g. Claude Desktop", maxLength = MAX_NAME, onDone = submit)
        error?.let { Text(it, style = Resurge.type.caption, color = Resurge.colors.danger) }
        Button("Create key", onClick = submit, enabled = name.text.isNotBlank(), loading = busy, modifier = Modifier.fillMaxWidth())
        Button("Cancel", onClick = onDismiss, enabled = !busy, variant = ButtonVariant.Secondary, modifier = Modifier.fillMaxWidth())
    }
}

/** The plain-text key, once. Screenshots are blocked while it shows; it's gone after Done. */
@Composable
private fun NewKeySheet(created: NewMcpApiKey, onDone: () -> Unit) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    SecureWindow(true)
    KeySheet(onDismiss = onDone, dismissible = false) {
        Text("Copy your key", style = Resurge.type.title)
        SoftNote("This is the only time it's shown. Copy it now and keep it somewhere safe; if you lose it, revoke it and make a new one.")
        Text(
            created.key,
            style = Resurge.type.body.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth().clip(Resurge.shape.md).background(Resurge.colors.surfaceMuted).padding(16.dp),
        )
        Button(
            if (copied) "Copied" else "Copy key",
            onClick = {
                copySensitive(context, created.key)
                copied = true
            },
            leadingIcon = if (copied) ResurgeIcons.Check else ResurgeIcons.Copy,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Your agent sends it to the MCP server as the header Authorization: Bearer rsg_…",
            style = Resurge.type.caption,
            color = Resurge.colors.textSubtle,
        )
        Button("Done", onClick = onDone, variant = ButtonVariant.Secondary, modifier = Modifier.fillMaxWidth())
    }
}

/** Copies [text], marked sensitive so Android 13+ hides it from the clipboard preview. */
private fun copySensitive(context: Context, text: String) {
    val clip = ClipData.newPlainText("Resurge MCP API key", text)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
}

/** A bottom sheet over a scrim, like the app's other sheets. */
@Composable
private fun KeySheet(onDismiss: () -> Unit, dismissible: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Dialog(
        onDismissRequest = { if (dismissible) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = dismissible, dismissOnClickOutside = dismissible),
    ) {
        Box(
            Modifier.fillMaxSize().clickable(interactionSource = null, indication = null) { if (dismissible) onDismiss() }.imePadding(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(Resurge.colors.surface)
                    .clickable(interactionSource = null, indication = null) {}
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 4.dp).clip(Resurge.shape.pill).background(Resurge.colors.border))
                content()
            }
        }
    }
}

private val DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

private fun formatDay(at: Instant): String = at.atZone(ZoneId.systemDefault()).format(DAY)
