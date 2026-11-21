package com.example.platescanner.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.platescanner.Notice
import com.example.platescanner.PlateViewModel
import com.example.platescanner.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Glass = Color.Black.copy(alpha = 0.55f)
private val GlassBorder = Color.White.copy(alpha = 0.25f)

private enum class OpenPanel { None, Settings, Plates }

@Composable
fun ScannerScreen(viewModel: PlateViewModel) {
    val plates by viewModel.plates.collectAsStateWithLifecycle()
    val recording by viewModel.recording.collectAsStateWithLifecycle()
    val recordingSince by viewModel.recordingSince.collectAsStateWithLifecycle()
    val liveInfo by viewModel.liveInfo.collectAsStateWithLifecycle()
    var openPanel by rememberSaveable { mutableStateOf(OpenPanel.None) }

    var notice by remember { mutableStateOf<Notice?>(null) }
    var noticeVisible by remember { mutableStateOf(false) }
    var noticeCount by remember { mutableIntStateOf(0) }
    var watchAlert by remember { mutableStateOf<Notice?>(null) }
    var watchVisible by remember { mutableStateOf(false) }
    var watchCount by remember { mutableIntStateOf(0) }
    val pulse = remember { Animatable(0f) }

    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) viewModel.scanPhoto(uri) }

    // Each saved plate: pulse the menu button and flash the plate number at the top.
    LaunchedEffect(Unit) {
        viewModel.notices.collect { n ->
            if (n.kind == Notice.Kind.WATCH) {
                watchAlert = n
                watchVisible = true
                watchCount++
            }
            notice = n
            noticeVisible = true
            noticeCount++
            if (n.kind != Notice.Kind.INFO) {
                launch {
                    pulse.snapTo(1f)
                    pulse.animateTo(0f, tween(900))
                }
            }
        }
    }
    LaunchedEffect(watchCount) {
        if (watchCount > 0) {
            delay(6_000)
            watchVisible = false
        }
    }
    LaunchedEffect(noticeCount) {
        if (noticeCount > 0) {
            delay(2_000)
            noticeVisible = false
        }
    }

    // Declared before the panels so the plates page's own back handling (detail -> list) wins.
    BackHandler(enabled = openPanel != OpenPanel.None) { openPanel = OpenPanel.None }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CameraPreview(
            analyzer = viewModel.analyzer,
            analysisExecutor = viewModel.analysisExecutor,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (recording) RecordingIndicator(recordingSince)
            liveInfo?.let { LiveReadout(it) }
            AnimatedVisibility(
                visible = noticeVisible,
                enter = fadeIn() + slideInVertically { -it / 2 },
                exit = fadeOut() + slideOutVertically { -it / 2 },
            ) {
                NoticeLabel(notice)
            }
        }

        BottomControls(
            recording = recording,
            pulse = pulse.value,
            plateCount = plates.size,
            onSettings = { openPanel = OpenPanel.Settings },
            onMenu = { openPanel = OpenPanel.Plates },
            onRecord = viewModel::toggleRecording,
            onPickPhoto = {
                pickPhoto.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 20.dp),
        )

        SettingsPage(
            visible = openPanel == OpenPanel.Settings,
            plates = plates,
            recording = recording,
            recordingSince = recordingSince,
            viewModel = viewModel,
            onClose = { openPanel = OpenPanel.None },
        )
        // Full-screen page drawn over the camera; the camera keeps running underneath so
        // recording continues while it's open.
        PlatesPage(
            visible = openPanel == OpenPanel.Plates,
            plates = plates,
            recording = recording,
            recordingSince = recordingSince,
            viewModel = viewModel,
            onClose = { openPanel = OpenPanel.None },
        )

        // Watch-list alert: drawn last so it shows above the camera and both pages.
        AnimatedVisibility(
            visible = watchVisible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            WatchBanner(watchAlert, onDismiss = { watchVisible = false })
        }
    }
}

/** Settings → Show live readings: what the reader saw in the latest frame. */
@Composable
private fun LiveReadout(info: com.example.platescanner.scan.FrameInfo) {
    Column(
        Modifier
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.65f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            "${info.width}×${info.height} · ${info.millis} ms",
            color = Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
        )
        if (info.reads.isEmpty()) {
            Text("no plate", color = Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        info.reads.forEach { (text, conf, width) ->
            Text(
                "%-9s %3d%%  %dpx".format(text, (conf * 100).toInt(), width),
                color = if (conf >= 0.8f) Color.White else WatchAmber,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun WatchBanner(alert: Notice?, onDismiss: () -> Unit) {
    if (alert == null) return
    Row(
        Modifier
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(WatchAmber)
            .clickable(onClick = onDismiss)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Notifications, contentDescription = null, tint = Color.Black)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("WATCHED PLATE SEEN", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(alert.text, color = Color.Black, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
        Text("Tap to dismiss", color = Color.Black.copy(alpha = 0.6f), fontSize = 11.sp)
    }
}

/**
 * Bottom row, as sketched:
 * [settings]   [ photo | · ( RECORD ) · | · ]   [menu]
 */
@Composable
private fun BottomControls(
    recording: Boolean,
    pulse: Float,
    plateCount: Int,
    onSettings: () -> Unit,
    onMenu: () -> Unit,
    onRecord: () -> Unit,
    onPickPhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RoundButton(onClick = onSettings) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White)
        }

        Spacer(Modifier.weight(1f))

        Box(contentAlignment = Alignment.Center) {
            Row(
                Modifier
                    .height(48.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Glass)
                    .border(1.dp, GlassBorder, RoundedCornerShape(50)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BarButton(onClick = onPickPhoto) {
                    Icon(
                        painterResource(R.drawable.ic_photo),
                        contentDescription = "Scan a photo",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
                BarDivider()
                BarButton(onClick = {}) {}     // spare
                Spacer(Modifier.width(76.dp))  // room for the record button
                BarButton(onClick = {}) {}     // spare
                BarDivider()
                BarButton(onClick = {}) {}     // spare
            }
            RecordButton(recording = recording, onClick = onRecord)
        }

        Spacer(Modifier.weight(1f))

        Box {
            RoundButton(
                onClick = onMenu,
                ringColor = lerp(GlassBorder, Accent, pulse),
                modifier = Modifier.scale(1f + 0.12f * pulse),
            ) {
                Icon(Icons.Filled.Menu, contentDescription = "Detected plates", tint = Color.White)
            }
            if (plateCount > 0) {
                Text(
                    text = if (plateCount > 99) "99+" else plateCount.toString(),
                    color = Color.Black,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .clip(RoundedCornerShape(50))
                        .background(Accent)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun RoundButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    ringColor: Color = GlassBorder,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(Glass)
            .border(1.5.dp, ringColor, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
private fun BarButton(onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .width(42.dp)
            .height(48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
private fun BarDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(22.dp)
            .background(GlassBorder)
    )
}

/** Big dark circle; red dot when idle, red square while recording. */
@Composable
private fun RecordButton(recording: Boolean, onClick: () -> Unit) {
    val innerSize by animateDpAsState(if (recording) 24.dp else 28.dp, label = "recSize")
    val corner by animateDpAsState(if (recording) 6.dp else 14.dp, label = "recCorner")
    Box(
        modifier = Modifier
            .size(68.dp)
            .shadow(10.dp, CircleShape)
            .clip(CircleShape)
            .background(Color(0xFF111111))
            .border(3.dp, Color.White, CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = if (recording) "Stop recording" else "Start recording"
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(innerSize)
                .clip(RoundedCornerShape(corner))
                .background(RecordRed)
        )
    }
}

@Composable
internal fun RecordingIndicator(since: Long) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(since) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(500)
        }
    }
    val total = ((now - since) / 1_000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    val time = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)

    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Glass)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(RecordRed)
        )
        Spacer(Modifier.width(8.dp))
        Text(time, color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun NoticeLabel(notice: Notice?) {
    if (notice == null) return
    val isPlate = notice.kind != Notice.Kind.INFO
    Text(
        text = notice.text,
        color = Color.White,
        fontFamily = if (isPlate) FontFamily.Monospace else FontFamily.Default,
        fontWeight = if (isPlate) FontWeight.Bold else FontWeight.Normal,
        fontSize = 14.sp,
        modifier = Modifier
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                when (notice.kind) {
                    Notice.Kind.NEW_PLATE -> Accent.copy(alpha = 0.85f)
                    Notice.Kind.WATCH -> WatchAmber.copy(alpha = 0.9f)
                    else -> Glass
                }
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
