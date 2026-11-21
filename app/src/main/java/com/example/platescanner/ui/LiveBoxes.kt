package com.example.platescanner.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.withFrameMillis
import android.os.SystemClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.platescanner.scan.FrameInfo
import com.example.platescanner.scan.LiveBox
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

private val LabelGlass = Color.Black.copy(alpha = 0.62f)

/** Colour for a plate's state: white = still reading, green = saved, amber = rejected. */
private fun LiveBox.color(): Color = when {
    !accepted -> WatchAmber
    saved -> Accent
    else -> Color.White
}

/** One plate on screen, followed from frame to frame by position. */
private class Marker(val id: Int, box: LiveBox, var capturedAt: Long) {
    var box by mutableStateOf(box)
    var visible by mutableStateOf(true)
    /** Movement in frame pixels per ms, smoothed over recent frames. */
    var vx = 0f
    var vy = 0f
}

/**
 * Settings → Show live readings: a box around each detected plate with a header strip on top
 * showing the reading and its progress towards being saved.
 *
 * Reading a frame takes a moment, so its boxes describe where plates *were*. To keep up with a
 * moving car, each box is followed by position across frames and drawn ahead along its recent
 * motion by the time since its frame arrived (up to [MaxLeadMs]), updated every display frame.
 * Photo mode (frames far apart) just places the boxes.
 *
 * Frame → screen mapping matches PreviewView's FILL_CENTER (scale the upright analysis frame to
 * cover the view, centred); preview and analysis share a 16:9 aspect so they show the same area.
 */
@Composable
fun LiveBoxes(info: FrameInfo, modifier: Modifier = Modifier) {
    val markers = remember { mutableStateListOf<Marker>() }
    val nextId = remember { intArrayOf(0) }
    LaunchedEffect(info) {
        val unmatched = markers.toMutableList()
        for (b in info.boxes) {
            // Same plate: the nearest box from the last frame within about a plate-width.
            val m = unmatched
                .filter { near(it.box.box, b.box) }
                .minByOrNull { distance(it.box.box, b.box) }
            if (m == null) {
                markers.add(Marker(nextId[0]++, b, info.capturedAt))
                continue
            }
            unmatched.remove(m)
            val dt = (info.capturedAt - m.capturedAt).toFloat()
            if (m.visible && dt in 15f..MaxSampleGapMs) {
                val ix = (b.box.exactCenterX() - m.box.box.exactCenterX()) / dt
                val iy = (b.box.exactCenterY() - m.box.box.exactCenterY()) / dt
                m.vx = 0.5f * m.vx + 0.5f * ix
                m.vy = 0.5f * m.vy + 0.5f * iy
            } else {
                m.vx = 0f
                m.vy = 0f
            }
            m.box = b
            m.capturedAt = info.capturedAt
            m.visible = true
        }
        // Missing this frame: stop in place and fade (comes back if seen again soon).
        unmatched.forEach { it.visible = false; it.vx = 0f; it.vy = 0f }
    }

    // Display-frame clock, read only while placing boxes (layout), so it doesn't recompose.
    val clock = remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { }
            clock.longValue = SystemClock.elapsedRealtime()
        }
    }

    BoxWithConstraints(modifier) {
        if (info.width <= 0 || info.height <= 0) return@BoxWithConstraints
        val viewW = constraints.maxWidth.toFloat()
        val viewH = constraints.maxHeight.toFloat()
        val scale = max(viewW / info.width, viewH / info.height)
        val offX = (viewW - info.width * scale) / 2f
        val offY = (viewH - info.height * scale) / 2f
        val padPx = with(LocalDensity.current) { 3.dp.toPx() }

        markers.toList().forEach { m ->
            key(m.id) {
                val b = m.box
                val rect = Rect(
                    offX + b.box.left * scale - padPx,
                    offY + b.box.top * scale - padPx,
                    offX + b.box.right * scale + padPx,
                    offY + b.box.bottom * scale + padPx,
                )
                val alpha by animateFloatAsState(
                    targetValue = if (m.visible) 1f else 0f,
                    animationSpec = tween(if (m.visible) 80 else 250),
                    label = "plateFade",
                    finishedListener = { if (it == 0f) markers.remove(m) },
                )
                PlateMarker(b, rect, alpha) {
                    val lead = (clock.longValue - m.capturedAt).coerceIn(0L, MaxLeadMs)
                    Offset(m.vx * lead * scale, m.vy * lead * scale)
                }
            }
        }
    }
}

/** Furthest ahead a box is drawn of where its frame saw it. */
private const val MaxLeadMs = 400L

/** Frames further apart than this (photo mode) don't give a usable speed. */
private const val MaxSampleGapMs = 500f

private fun distance(a: android.graphics.Rect, b: android.graphics.Rect): Float =
    hypot(a.exactCenterX() - b.exactCenterX(), a.exactCenterY() - b.exactCenterY())

private fun near(a: android.graphics.Rect, b: android.graphics.Rect): Boolean =
    distance(a, b) <= max(a.width(), b.width())

/**
 * Plate box with two strips, as wide as the box and joined to it:
 * - top: the plate number read in this frame;
 * - bottom: status only: "2/3" (matching readings so far / needed), "saved", "unsure", or
 *   "—" (not a plate format).
 * The whole marker turns green once the plate is saved (white while reading, amber rejected).
 */
@Composable
private fun PlateMarker(b: LiveBox, r: Rect, alpha: Float, shift: () -> Offset) {
    val density = LocalDensity.current
    val color = b.color()
    val stripH = 18.dp
    val stripPx = with(density) { stripH.toPx() }
    val shape = RoundedCornerShape(5.dp)

    val top = b.text ?: "—"
    // Bottom strip is status only, never the plate number.
    val bottom = when {
        b.saved -> "saved"
        b.accepted -> "${b.matches}/${b.needed}"
        b.reason == "format" -> "—" // not a plate format
        else -> "unsure"
    }

    Column(
        Modifier
            .offset {
                val d = shift()
                IntOffset((r.left + d.x).roundToInt(), (r.top - stripPx + d.y).roundToInt())
            }
            .size(
                with(density) { r.width.toDp() },
                with(density) { (r.height + 2 * stripPx).toDp() },
            )
            .alpha(alpha)
            .border(1.5.dp, color, shape)
            .clip(shape),
    ) {
        Strip(top, color, stripH)
        Spacer(Modifier.weight(1f))
        Strip(bottom, color, stripH)
    }
}

/** A filled strip with one line of centred text that shrinks to fit the width. */
@Composable
private fun Strip(text: String, color: Color, height: androidx.compose.ui.unit.Dp) {
    val density = LocalDensity.current
    // Sized in dp (not sp) so it always fits the fixed strip height, whatever the font scale.
    val maxSize = with(density) { 12.dp.toSp() }
    var fontSize by remember(text) { mutableStateOf(maxSize) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .background(color)
            .padding(horizontal = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = Color.Black,
            fontSize = fontSize,
            lineHeight = fontSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            onTextLayout = { result ->
                if (result.didOverflowWidth && fontSize.value > 5f) fontSize = (fontSize.value - 1f).sp
            },
        )
    }
}

/** Small stats line shown under the recording timer when live readings are on. */
@Composable
fun LiveStats(info: FrameInfo, modifier: Modifier = Modifier) {
    Text(
        "${info.width}×${info.height} · ${info.millis} ms" + (info.locationText?.let { " · $it" } ?: ""),
        color = Color.White.copy(alpha = 0.75f),
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        modifier = modifier
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(LabelGlass)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
