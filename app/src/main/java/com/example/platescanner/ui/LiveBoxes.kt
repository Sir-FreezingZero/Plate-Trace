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
    BoxWithConstraints(modifier) {
        if (info.width <= 0 || info.height <= 0) return@BoxWithConstraints
        val viewW = constraints.maxWidth.toFloat()
        val viewH = constraints.maxHeight.toFloat()
        val scale = max(viewW / info.width, viewH / info.height)
        val offX = (viewW - info.width * scale) / 2f
        val offY = (viewH - info.height * scale) / 2f
        val padPx = with(LocalDensity.current) { 2.dp.toPx() }

        info.boxes.forEachIndexed { i, b ->
            key(b.savedText ?: b.text ?: "box$i") {
                val target = Rect(
                    offX + b.box.left * scale - padPx,
                    offY + b.box.top * scale - padPx,
                    offX + b.box.right * scale + padPx,
                    offY + b.box.bottom * scale + padPx,
                )
                val rect by animateRectAsState(target, tween(150), label = "plateBox")
                PlateMarker(b, rect, viewW)
            }
        }
    }
}

/**
 * The plate box with a header strip joined to its top edge, exactly as wide as the box, holding
 * the reading centred. The strip is as tall as its text, and the text measures itself and
 * shrinks until it fits the box width.
 */
@Composable
private fun PlateMarker(b: LiveBox, r: Rect, @Suppress("UNUSED_PARAMETER") viewW: Float) {
    val density = LocalDensity.current
    val color = b.color()
    val widthPx = r.width.roundToInt().coerceAtLeast(1)
    val boxHeight = with(density) { r.height.toDp() }
    val shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 3.dp, bottomEnd = 3.dp)

    val label = buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(b.savedText ?: b.text ?: "?") }
        withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = Color.Black.copy(alpha = 0.65f))) {
            append("  " + tagStatus(b))
        }
    }
    // Start at 12sp and step down until the label fits on one line inside the box.
    // Re-fit only when the text changes or the width changes noticeably (not every animation frame).
    var fontSize by remember(label.text, widthPx / 24) { mutableStateOf(12.sp) }

    Column(
        Modifier
            .layout { measurable, _ ->
                val p = measurable.measure(Constraints.fixedWidth(widthPx))
                layout(0, 0) {
                    // Bottom of the column = bottom of the plate box; the header sits above it.
                    p.place(r.left.roundToInt(), r.bottom.roundToInt() - p.height)
                }
            }
            .border(2.5.dp, Color.Black.copy(alpha = 0.25f), shape) // contrast on bright scenes
            .border(1.5.dp, color, shape)
            .clip(shape),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(color)
                .padding(horizontal = 3.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                color = Color.Black,
                fontSize = fontSize,
                lineHeight = fontSize * 1.1f,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center,
                onTextLayout = { result ->
                    if (result.didOverflowWidth && fontSize.value > 6f) fontSize = (fontSize.value - 1f).sp
                },
            )
        }
        Spacer(Modifier.height(boxHeight))
    }
}

/** Small status after the plate number: "2/3", "saved", "?" (unsure), "✕" (not a plate format). */
private fun tagStatus(b: LiveBox): String = when {
    b.saved -> "saved"
    b.accepted -> "${b.matches}/${b.needed}"
    b.reason == "format" -> "✕"
    else -> "?"
}

/** Small stats line shown under the recording timer when live readings are on. */
@Composable
fun LiveStats(info: FrameInfo, modifier: Modifier = Modifier) {
    Text(
        "${info.width}×${info.height} · ${info.millis} ms",
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
