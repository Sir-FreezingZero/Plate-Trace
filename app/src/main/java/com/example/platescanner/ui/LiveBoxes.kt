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
 * Settings → Show live readings: viewfinder brackets around each detected plate with a small
 * label showing the reading and its progress towards being saved.
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
        val padPx = with(LocalDensity.current) { 5.dp.toPx() }

        info.boxes.forEachIndexed { i, b ->
            key(b.savedText ?: b.text ?: "box$i") {
                val target = Rect(
                    offX + b.box.left * scale - padPx,
                    offY + b.box.top * scale - padPx,
                    offX + b.box.right * scale + padPx,
                    offY + b.box.bottom * scale + padPx,
                )
                val rect by animateRectAsState(target, tween(220), label = "plateBox")
                PlateMarker(b, rect, viewW)
            }
        }
    }
}

@Composable
private fun PlateMarker(b: LiveBox, r: Rect, viewW: Float) {
    val density = LocalDensity.current
    val color = b.color()

    // Corner brackets with a faint tint inside.
    Canvas(
        Modifier
            .offset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
            .size(with(density) { r.width.toDp() }, with(density) { r.height.toDp() })
    ) {
        val w = size.width
        val h = size.height
        val len = (min(w, h) * 0.38f).coerceIn(6.dp.toPx(), 16.dp.toPx())
        drawRoundRect(color.copy(alpha = 0.10f), cornerRadius = CornerRadius(4.dp.toPx()))
        val corners = Path().apply {
            moveTo(0f, len); lineTo(0f, 0f); lineTo(len, 0f)
            moveTo(w - len, 0f); lineTo(w, 0f); lineTo(w, len)
            moveTo(w, h - len); lineTo(w, h); lineTo(w - len, h)
            moveTo(len, h); lineTo(0f, h); lineTo(0f, h - len)
        }
        drawPath(
            corners, color,
            style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }

    // Label centred above the box, kept on screen.
    val gap = with(density) { 6.dp.roundToPx() }
    Box(
        Modifier.layout { measurable, _ ->
            val p = measurable.measure(Constraints())
            layout(0, 0) {
                val x = (r.center.x - p.width / 2f).roundToInt()
                    .coerceIn(0, max(0, viewW.roundToInt() - p.width))
                val y = (r.top.roundToInt() - p.height - gap).coerceAtLeast(0)
                p.place(x, y)
            }
        }
    ) {
        PlateLabel(b, color)
    }
}

@Composable
private fun PlateLabel(b: LiveBox, color: Color) {
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(LabelGlass)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            b.saved -> {
                Text("✓", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(5.dp))
                PlateText(b.savedText ?: b.text ?: "", Color.White)
                Spacer(Modifier.width(6.dp))
                Text("Saved", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            b.accepted -> {
                PlateText(b.text ?: "", Color.White)
                Spacer(Modifier.width(8.dp))
                ProgressDots(done = b.matches, total = b.needed)
            }
            else -> {
                PlateText(b.text ?: "· · ·", color)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (b.reason == "format") "not a plate format" else "unsure",
                    color = color.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun PlateText(text: String, color: Color) {
    Text(
        text,
        color = color,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        letterSpacing = 0.5.sp,
        maxLines = 1,
    )
}

/** One dot per matching reading needed to save; filled as they come in. */
@Composable
private fun ProgressDots(done: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(total.coerceAtLeast(1)) { i ->
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (i < done) Accent else Color.White.copy(alpha = 0.3f))
            )
        }
    }
}

/** Small stats line shown under the recording timer when live readings are on. */
@Composable
fun LiveStats(info: FrameInfo, modifier: Modifier = Modifier) {
    val fps = if (info.millis > 0) 1000f / info.millis else 0f
    Text(
        "%dp · %.1f fps".format(min(info.width, info.height), fps),
        color = Color.White.copy(alpha = 0.75f),
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        modifier = modifier
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(50))
            .background(LabelGlass)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}
