package com.example.platescanner.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal fun Bitmap.rotated(degrees: Int): Bitmap {
    if (degrees % 360 == 0) return this
    val m = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(this, 0, 0, width, height, m, true)
}

/**
 * Crops a detected plate plus a small margin. This crop is what gets read and saved.
 */
internal fun Bitmap.cropAround(box: Rect, padX: Float = PLATE_PAD_X, padY: Float = PLATE_PAD_Y): Bitmap {
    val dx = (box.width() * padX).toInt()
    val dy = (box.height() * padY).toInt()
    val left = (box.left - dx).coerceIn(0, width - 1)
    val top = (box.top - dy).coerceIn(0, height - 1)
    val right = (box.right + dx).coerceIn(left + 1, width)
    val bottom = (box.bottom + dy).coerceIn(top + 1, height)
    return Bitmap.createBitmap(this, left, top, right - left, bottom - top)
}

/**
 * Zoomed-out shot of the back of the car, always centred on the plate.
 *
 * The box is [widthFactor] × the plate box's width wide, 4:5 high, and centred on the plate.
 * Where it runs past the edge of the frame the missing area is filled with black rather than
 * shifting the box, so the plate stays in the middle. Scaled to at most [maxSide] px.
 */
internal fun Bitmap.cropVehicle(plateBox: Rect, widthFactor: Float, maxSide: Int = 1280): Bitmap {
    val w = (plateBox.width() * widthFactor).roundToInt().coerceAtLeast(1)
    val h = (w * 0.8f).roundToInt().coerceAtLeast(1)
    val left = plateBox.centerX() - w / 2
    val top = plateBox.centerY() - h / 2
    val scale = min(1f, maxSide.toFloat() / max(w, h))

    val out = Bitmap.createBitmap(
        (w * scale).roundToInt().coerceAtLeast(1),
        (h * scale).roundToInt().coerceAtLeast(1),
        Bitmap.Config.ARGB_8888,
    )
    val canvas = Canvas(out)
    canvas.drawColor(Color.BLACK)
    val src = Rect(max(0, left), max(0, top), min(width, left + w), min(height, top + h))
    if (src.width() > 0 && src.height() > 0) {
        val dst = RectF(
            (src.left - left) * scale, (src.top - top) * scale,
            (src.right - left) * scale, (src.bottom - top) * scale,
        )
        canvas.drawBitmap(this, src, dst, Paint(Paint.FILTER_BITMAP_FLAG))
    }
    return out
}

/** Loads a picked photo upright and no larger than [maxSide] px on its longest edge. */
object PhotoLoader {

    fun load(context: Context, uri: Uri, maxSide: Int = 2560): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= 28) loadModern(context, uri, maxSide)
        else loadLegacy(context, uri, maxSide)
    } catch (e: Exception) {
        Log.e("PhotoLoader", "Could not load $uri", e)
        null
    }

    private fun loadModern(context: Context, uri: Uri, maxSide: Int): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        // ImageDecoder applies the EXIF rotation itself.
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val scale = maxSide.toFloat() / maxOf(w, h)
            if (scale < 1f) decoder.setTargetSize((w * scale).toInt(), (h * scale).toInt())
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE // pixels must be readable
        }
    }

    private fun loadLegacy(context: Context, uri: Uri, maxSide: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        val orientation = resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
        return bitmap.rotated(degrees)
    }
}
