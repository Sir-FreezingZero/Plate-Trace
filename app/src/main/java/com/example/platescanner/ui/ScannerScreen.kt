package com.example.platescanner.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.platescanner.PlateViewModel
import com.example.platescanner.SaveEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Accent = Color(0xFF2ECC71)

@Composable
fun ScannerScreen(viewModel: PlateViewModel) {
    val plates by viewModel.plates.collectAsStateWithLifecycle()
    var panelOpen by rememberSaveable { mutableStateOf(false) }
    var lastEvent by remember { mutableStateOf<SaveEvent?>(null) }
    var labelVisible by remember { mutableStateOf(false) }
    var eventCount by remember { mutableIntStateOf(0) }
    val pulse = remember { Animatable(0f) }

    // Each saved plate: pulse the bubble and show a small label under it.
    LaunchedEffect(Unit) {
        viewModel.saveEvents.collect { event ->
            lastEvent = event
            labelVisible = true
            eventCount++
            launch {
                pulse.snapTo(1f)
                pulse.animateTo(0f, tween(900))
            }
        }
    }
    LaunchedEffect(eventCount) {
        if (eventCount > 0) {
            delay(1_800)
            labelVisible = false
        }
    }

    // Declared before the panel so the panel's own back handling (detail -> list) wins.
    BackHandler(enabled = panelOpen) { panelOpen = false }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CameraPreview(
            analyzer = viewModel.analyzer,
            analysisExecutor = viewModel.analysisExecutor,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.End,
        ) {
            FloatingBubble(
                count = plates.size,
                pulse = pulse.value,
                onClick = { panelOpen = true },
            )
            AnimatedVisibility(
                visible = labelVisible,
                enter = fadeIn() + slideInVertically { -it / 2 },
                exit = fadeOut() + slideOutVertically { -it / 2 },
            ) {
                SavedLabel(lastEvent)
            }
        }

        PlatesPanel(
            visible = panelOpen,
            plates = plates,
            viewModel = viewModel,
            onDismiss = { panelOpen = false },
        )
    }
}

@Composable
private fun FloatingBubble(count: Int, pulse: Float, onClick: () -> Unit) {
    val ring = lerp(Color.White.copy(alpha = 0.35f), Accent, pulse)
    Box(
        modifier = Modifier
            .scale(1f + 0.15f * pulse)
            .size(52.dp)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .border(1.5.dp, ring, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (count == 0) {
            Icon(Icons.Filled.List, contentDescription = "Detected plates", tint = Color.White)
        } else {
            Text(
                text = if (count > 999) "999+" else count.toString(),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = if (count > 99) 13.sp else 16.sp,
            )
        }
    }
}

@Composable
private fun SavedLabel(event: SaveEvent?) {
    if (event == null) return
    Text(
        text = event.plate,
        color = Color.White,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        modifier = Modifier
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (event.isNew) Accent.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
