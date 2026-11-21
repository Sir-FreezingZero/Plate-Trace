package com.example.platescanner.ui

import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
 * Settings → Show live readings: a thin box around each detected plate with a small tag
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

@Composable
private fun PlateMarker(b: LiveBox, r: Rect, viewW: Float) {
    val density = LocalDensity.current
    val color = b.color()

    // Thin box with slightly rounded corners and a dark hairline outside it, so it stays
    // visible on bright cars and sky.
    Canvas(
        Modifier
            .offset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
            .size(with(density) { r.width.toDp() }, with(density) { r.height.toDp() })
    ) {
        val radius = CornerRadius(3.dp.toPx())
        val sw = 1.5.dp.toPx()
        drawRoundRect(
            Color.Black.copy(alpha = 0.35f),
            topLeft = androidx.compose.ui.geometry.Offset(-sw, -sw),
            size = androidx.compose.ui.geometry.Size(size.width + 2 * sw, size.height + 2 * sw),
            cornerRadius = radius,
            style = Stroke(sw),
        )
        drawRoundRect(color, cornerRadius = radius, style = Stroke(sw))
    }

    // Tab attached to the box's top-left corner, kept on screen.
    Box(
        Modifier.layout { measurable, _ ->
            val p = measurable.measure(Constraints())
            layout(0, 0) {
                val x = r.left.roundToInt().coerceIn(0, max(0, viewW.roundToInt() - p.width))
                val y = (r.top.roundToInt() - p.height + 1).coerceAtLeast(0)
                p.place(x, y)
            }
        }
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                .background(color)
                .padding(horizontal = 5.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                b.savedText ?: b.text ?: "?",
                color = Color.Black,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                maxLines = 1,
            )
            Text(
                " " + tagStatus(b),
                color = Color.Black.copy(alpha = 0.6f),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                maxLines = 1,
            )
        }
    }
}

/** Small status after the plate number: "2/3", "✓", "?" (unsure), "✕" (not a plate format). */
private fun tagStatus(b: LiveBox): String = when {
    b.saved -> "✓"
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
