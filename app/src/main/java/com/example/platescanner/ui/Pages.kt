package com.example.platescanner.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val Accent = Color(0xFF2ECC71)
internal val RecordRed = Color(0xFFE53935)
internal val WatchAmber = Color(0xFFFFB300)
internal val ThumbBackground = Color(0xFF1E2126)
internal val Muted = Color.White.copy(alpha = 0.6f)
internal val PageColor = Color(0xFF0E0F11)
internal val LineColor = Color.White.copy(alpha = 0.14f)
internal val FieldColor = Color.White.copy(alpha = 0.06f)

/**
 * A full-screen page that fades in over the camera. The camera stays composed underneath,
 * so recording keeps running while a page is open.
 */
@Composable
fun FullPage(visible: Boolean, content: @Composable ColumnScope.() -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(150)),
    ) {
        Surface(Modifier.fillMaxSize(), color = PageColor, contentColor = Color.White) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
                content = content,
            )
        }
    }
}

/** Back arrow, title (+ optional subtitle), recording timer, then any [actions]. */
@Composable
fun PageHeader(
    title: String,
    subtitle: String?,
    recording: Boolean,
    recordingSince: Long,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back to camera")
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, color = Muted, fontSize = 12.sp)
        }
        if (recording) RecordingIndicator(recordingSince)
        actions()
    }
}
