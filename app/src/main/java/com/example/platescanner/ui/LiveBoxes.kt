package com.example.platescanner.ui

import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.Icons
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.platescanner.scan.FrameInfo
import com.example.platescanner.scan.LiveBox
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val LabelGlass = Color.Black.copy(alpha = 0.62f)

/** Colour for a plate's state: white = still reading, green = saved, amber = rejected. */
private fun LiveBox.color(): Color = when {
    !accepted -> WatchAmber
    saved -> Accent
    else -> Color.White
}

/**
 * Settings → Show live readings: a box around each detected plate with a header strip on top
 * showing the reading and its progress towards being saved.
 *
 * Frame → screen mapping matches PreviewView's FILL_CENTER (scale the upright analysis frame to
 * cover the view, centred); preview and analysis share a 16:9 aspect so they show the same
 * area. Boxes glide to each new position instead of jumping, which also hides the one-frame lag.
 */
@Composable
fun LiveBoxes(info: FrameInfo, modifier: Modifier = Modifier) {
    // Boxes currently on screen, by key. A box missing from the latest frame stays briefly and
    // fades out, so a plate the detector misses for one frame doesn't blink.
    val shown = remember { mutableStateMapOf<String, Pair<LiveBox, Boolean>>() }
    LaunchedEffect(info) {
        val present = HashSet<String>()
        info.boxes.forEachIndexed { i, b ->
            val k = b.key(i)
            present.add(k)
            shown[k] = b to true
        }
        for (k in shown.keys.toList()) {
            if (k !in present) shown[k]?.let { (b, _) -> shown[k] = b to false }
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

        shown.entries.toList().forEach { (k, entry) ->
            val (b, visible) = entry
            key(k) {
                val target = Rect(
                    offX + b.box.left * scale - padPx,
                    offY + b.box.top * scale - padPx,
                    offX + b.box.right * scale + padPx,
                    offY + b.box.bottom * scale + padPx,
                )
                val rect by animateRectAsState(target, tween(150), label = "plateBox")
                val alpha by animateFloatAsState(
                    targetValue = if (visible) 1f else 0f,
                    animationSpec = tween(if (visible) 120 else 450),
                    label = "plateFade",
                    finishedListener = { if (it == 0f) shown.remove(k) },
                )
                PlateMarker(b, rect, alpha)
            }
        }
    }
}

private fun LiveBox.key(index: Int) = savedText ?: text ?: "box$index"

/**
 * Plate box with two strips, as wide as the box and joined to it:
 * - top: the plate number read in this frame;
 * - bottom: status only: "2/3" (matching readings so far / needed), "saved", "unsure", or
 *   "—" (not a plate format).
 * The whole marker turns green once the plate is saved (white while reading, amber rejected).
 */
@Composable
private fun PlateMarker(b: LiveBox, r: Rect, alpha: Float) {
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
            .offset { IntOffset(r.left.roundToInt(), (r.top - stripPx).roundToInt()) }
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
