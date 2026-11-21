package com.example.platescanner.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.Log

internal fun Bitmap.rotated(degrees: Int): Bitmap {
    if (degrees % 360 == 0) return this
    val m = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(this, 0, 0, width, height, m, true)
}

/**
 * Crops the text box plus a margin, so the saved image shows the whole plate rather than
 * just the characters.
 */
internal fun Bitmap.cropAround(box: Rect, padX: Float = 0.18f, padY: Float = 0.7f): Bitmap {
    val dx = (box.width() * padX).toInt()
    val dy = (box.height() * padY).toInt()
    val left = (box.left - dx).coerceIn(0, width - 1)
    val top = (box.top - dy).coerceIn(0, height - 1)
    val right = (box.right + dx).coerceIn(left + 1, width)
    val bottom = (box.bottom + dy).coerceIn(top + 1, height)
    return Bitmap.createBitmap(this, left, top, right - left, bottom - top)
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
