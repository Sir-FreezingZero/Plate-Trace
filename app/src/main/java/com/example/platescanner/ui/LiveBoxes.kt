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

    // Plain thin rectangle.
    Canvas(
        Modifier
            .offset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
            .size(with(density) { r.width.toDp() }, with(density) { r.height.toDp() })
    ) {
        drawRect(color, style = Stroke(1.5.dp.toPx()))
    }

    // Tag attached to the box's top-left corner, kept on screen.
    Box(
        Modifier.layout { measurable, _ ->
            val p = measurable.measure(Constraints())
            layout(0, 0) {
                val x = r.left.roundToInt().coerceIn(0, max(0, viewW.roundToInt() - p.width))
                val y = (r.top.roundToInt() - p.height).coerceAtLeast(0)
                p.place(x, y)
            }
        }
    ) {
        Text(
            tagText(b),
            color = Color.Black,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            maxLines = 1,
            modifier = Modifier
                .background(color)
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

/** e.g. "LXC15M 2/3", "LXC15M ✓", "LXC1SM ?" (unsure), "SALE50 ✕" (not a plate format). */
private fun tagText(b: LiveBox): String = when {
    b.saved -> "${b.savedText ?: b.text} ✓"
    b.accepted -> "${b.text} ${b.matches}/${b.needed}"
    b.reason == "format" -> "${b.text ?: "?"} ✕"
    else -> "${b.text ?: ""} ?".trim()
}

/** Small stats line shown under the recording timer when live readings are on. */
@Composable
fun LiveStats(info: FrameInfo, modifier: Modifier = Modifier) {
    val fps = if (info.millis > 0) 1000f / info.millis else 0f
    Text(
        "%dp · %.1f fps".format(min(info.width, info.height), fps),
        color = Color.White.copy(alpha = 0.75f),
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        modifier = modifier
            .padding(top = 4.dp)
            .background(LabelGlass)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}
