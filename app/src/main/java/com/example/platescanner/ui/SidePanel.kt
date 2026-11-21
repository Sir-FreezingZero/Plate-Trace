package com.example.platescanner.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

internal val Accent = Color(0xFF2ECC71)
internal val RecordRed = Color(0xFFE53935)
internal val PanelColor = Color(0xF2121417)
internal val ThumbBackground = Color(0xFF1E2126)
internal val Muted = Color.White.copy(alpha = 0.6f)

/** A panel that slides in over the camera from the left ([fromStart]) or right edge. */
@Composable
fun SidePanel(
    visible: Boolean,
    fromStart: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val panelWidth = minOf(screenWidth * 0.88f, 420.dp)

    Box(Modifier.fillMaxSize()) {
        // Scrim: tap outside the panel to close it.
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    )
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally { if (fromStart) -it else it },
            exit = slideOutHorizontally { if (fromStart) -it else it },
            modifier = Modifier.align(if (fromStart) Alignment.CenterStart else Alignment.CenterEnd),
        ) {
            Surface(
                modifier = Modifier.width(panelWidth).fillMaxHeight(),
                shape = if (fromStart) {
                    RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
                } else {
                    RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
                },
                color = PanelColor,
                contentColor = Color.White,
            ) {
                Column(Modifier.statusBarsPadding().navigationBarsPadding(), content = content)
            }
        }
    }
}
