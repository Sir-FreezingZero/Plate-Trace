package com.example.platescanner.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.platescanner.scan.FrameInfo
import kotlin.math.max

/**
 * Settings → Show live readings: draws each detected plate's box on the camera preview with
 * what was read. Green = read and accepted; amber = found but rejected (unsure, or not the
 * chosen plate format).
 *
 * Frame → screen mapping matches PreviewView's FILL_CENTER: scale the (upright) analysis frame
 * to cover the view, centred. Preview and analysis use the same 16:9 aspect, so they show the
 * same area. Boxes lag the preview slightly (by one frame's processing time).
 */
@Composable
fun LiveBoxes(info: FrameInfo, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val labelPaint = remember(density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = with(density) { 14.sp.toPx() }
        }
    }
    val bgPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }

    Canvas(modifier) {
        if (info.width <= 0 || info.height <= 0) return@Canvas
        val scale = max(size.width / info.width, size.height / info.height)
        val offX = (size.width - info.width * scale) / 2f
        val offY = (size.height - info.height * scale) / 2f
        val stroke = 2.dp.toPx()
        val pad = 4.dp.toPx()

        for (b in info.boxes) {
            val color = if (b.accepted) Accent else WatchAmber
            val left = offX + b.box.left * scale
            val top = offY + b.box.top * scale
            val w = b.box.width() * scale
            val h = b.box.height() * scale
            drawRect(color, Offset(left, top), Size(w, h), style = Stroke(stroke))

            val label = (b.text ?: "?") + "  " + (b.confidence * 100).toInt() + "%"
            val textW = labelPaint.measureText(label)
            val textH = labelPaint.textSize
            val labelTop = (top - textH - 2 * pad).coerceAtLeast(0f)
            bgPaint.color = color.toArgb()
            labelPaint.color = android.graphics.Color.BLACK
            drawContext.canvas.nativeCanvas.apply {
                drawRect(left, labelTop, left + textW + 2 * pad, labelTop + textH + 2 * pad, bgPaint)
                drawText(label, left + pad, labelTop + pad + textH * 0.85f, labelPaint)
            }
        }
    }
}
