package com.resurgeai.feature.auth

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composeunstyled.Text
import com.resurgeai.core.designsystem.components.Button
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.IconBadge
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.Screen
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.designsystem.theme.ResurgeTheme

@Composable
fun SignInRoute(viewModel: SignInViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    SignInScreen(state = state, onSignIn = { viewModel.signIn(context) })
}

@Composable
fun SignInScreen(state: SignInUiState, onSignIn: () -> Unit) {
    Screen {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                Spacer(Modifier.height(48.dp))
                LogoMark()
                Spacer(Modifier.height(24.dp))
                Text("Resurge", style = Resurge.type.brandLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Your health data, food and an AI coach — in one calm place.",
                    style = Resurge.type.body,
                    color = Resurge.colors.textMuted,
                )
                Spacer(Modifier.height(40.dp))
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Feature(
                        ResurgeIcons.Watch, Resurge.colors.activity,
                        "Phone + wearable, together",
                        "Sleep, steps, calories and workouts from Health Connect.",
                    )
                    Feature(
                        ResurgeIcons.Food, Resurge.colors.energy,
                        "Snap a meal, log it",
                        "Scan your plate, tweak the estimate, done.",
                    )
                    Feature(
                        ResurgeIcons.Coach, Resurge.colors.sleep,
                        "A coach that knows your data",
                        "Insights and answers grounded in your own trends.",
                    )
                }
            }

            state.error?.let {
                Text(
                    it,
                    style = Resurge.type.caption,
                    color = Resurge.colors.danger,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    textAlign = TextAlign.Center,
                )
            }
            Button(
                text = "Continue with Google",
                onClick = onSignIn,
                loading = state.loading,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Not medical advice. Your data stays in your account.",
                style = Resurge.type.caption,
                color = Resurge.colors.textSubtle,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun LogoMark() {
    Image(
        painter = painterResource(R.drawable.ic_brand_tile),
        contentDescription = null,
        modifier = Modifier
            .size(56.dp)
            .clip(Resurge.shape.md),
    )
}

@Composable
private fun Feature(@DrawableRes icon: Int, tint: Color, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        IconBadge(icon, tint = tint, size = 40.dp)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = Resurge.type.headline)
            Text(body, style = Resurge.type.body, color = Resurge.colors.textMuted)
        }
    }
}

@Preview
@Composable
private fun SignInPreview() {
    ResurgeTheme { SignInScreen(SignInUiState(), onSignIn = {}) }
}
