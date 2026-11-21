package com.example.platescanner.scan

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Two on-device models built specifically for license plates (both MIT licensed, from the
 * fast-alpr project by ankandrew; see assets/licenses):
 *
 * 1. **Detector** (`plate_detector.onnx`, YOLOv9-tiny, 512×512 input): finds plate boxes in a
 *    whole frame.
 * 2. **Reader** (`plate_ocr.onnx`, fast-plate-ocr `cct-s-v2-global`, 128×64 input): reads the
 *    characters from one cropped plate.
 *
 * Pre- and post-processing mirror the reference Python implementation (letterboxed RGB 0–1
 * float input for the detector; stretched RGB uint8 input for the reader), which was checked
 * to give identical results before porting.
 *
 * Sessions are thread-safe, so live frames and gallery photos can share one engine.
 */
class PlateEngine(context: Context) : Closeable {

    /** A plate found in a frame, in frame pixel coordinates. */
    class Detection(val box: Rect, val score: Float)

    /** Characters read from a plate crop and the lowest per-character confidence (0–1). */
    class Reading(val text: String, val minConfidence: Float)

    private val env = OrtEnvironment.getEnvironment()
    private val detector: OrtSession
    private val reader: OrtSession

    init {
        val options = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            setIntraOpNumThreads(4)
        }
        detector = env.createSession(readAsset(context, "plate_detector.onnx"), options)
        reader = env.createSession(readAsset(context, "plate_ocr.onnx"), options)
    }

    /** Finds plates in [frame]. */
    fun detect(frame: Bitmap, minScore: Float = DETECT_THRESHOLD): List<Detection> {
        val s = DETECT_SIZE
        val w = frame.width
        val h = frame.height

        // Letterbox: scale to fit s×s keeping aspect, pad with grey (114).
        val r = min(s.toFloat() / w, s.toFloat() / h)
        val newW = (w * r).roundToInt()
        val newH = (h * r).roundToInt()
        val dw = (s - newW) / 2f
        val dh = (s - newH) / 2f
        val left = (dw - 0.1f).roundToInt()
        val top = (dh - 0.1f).roundToInt()

        val input = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        Canvas(input).apply {
            drawColor(Color.rgb(114, 114, 114))
            drawBitmap(frame, null, Rect(left, top, left + newW, top + newH), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        val pixels = IntArray(s * s)
        input.getPixels(pixels, 0, s, 0, 0, s, s)
        input.recycle()

        // NCHW, RGB, 0–1.
        val plane = s * s
        val data = FloatArray(3 * plane)
        for (i in 0 until plane) {
            val p = pixels[i]
            data[i] = ((p shr 16) and 0xFF) / 255f
            data[plane + i] = ((p shr 8) and 0xFF) / 255f
            data[2 * plane + i] = (p and 0xFF) / 255f
        }

        val rows: Array<FloatArray> =
            OnnxTensor.createTensor(env, FloatBuffer.wrap(data), longArrayOf(1, 3, s.toLong(), s.toLong())).use { t ->
                detector.run(mapOf("images" to t)).use { result ->
                    @Suppress("UNCHECKED_CAST")
                    (result[0].value as Array<FloatArray>)
                }
            }

        // Each row: [batch, x1, y1, x2, y2, class, score] in letterboxed coordinates.
        return rows.mapNotNull { row ->
            val score = row[6]
            if (score < minScore) return@mapNotNull null
            val x1 = ((row[1] - dw) / r).toInt().coerceIn(0, w)
            val y1 = ((row[2] - dh) / r).toInt().coerceIn(0, h)
            val x2 = ((row[3] - dw) / r).toInt().coerceIn(0, w)
            val y2 = ((row[4] - dh) / r).toInt().coerceIn(0, h)
            if (x2 - x1 < MIN_BOX_PX || y2 - y1 < MIN_BOX_PX / 2) null
            else Detection(Rect(x1, y1, x2, y2), score)
        }
    }

    /** Reads the characters on a cropped plate image. */
    fun read(crop: Bitmap): Reading {
        val scaled = Bitmap.createScaledBitmap(crop, OCR_W, OCR_H, true)
        val pixels = IntArray(OCR_W * OCR_H)
        scaled.getPixels(pixels, 0, OCR_W, 0, 0, OCR_W, OCR_H)
        if (scaled !== crop) scaled.recycle()

        // NHWC, RGB, uint8 (the model normalises internally).
        val buffer = ByteBuffer.allocateDirect(OCR_W * OCR_H * 3).order(ByteOrder.nativeOrder())
        for (p in pixels) {
            buffer.put(((p shr 16) and 0xFF).toByte())
            buffer.put(((p shr 8) and 0xFF).toByte())
            buffer.put((p and 0xFF).toByte())
        }
        buffer.rewind()

        val slots: Array<FloatArray> =
            OnnxTensor.createTensor(
                env, buffer, longArrayOf(1, OCR_H.toLong(), OCR_W.toLong(), 3), OnnxJavaType.UINT8,
            ).use { t ->
                reader.run(mapOf("input" to t), setOf("plate")).use { result ->
                    @Suppress("UNCHECKED_CAST")
                    (result[0].value as Array<Array<FloatArray>>)[0]
                }
            }

        // Each slot is a probability distribution over ALPHABET; '_' is padding.
        val chars = StringBuilder()
        val confidences = ArrayList<Float>(slots.size)
        for (slot in slots) {
            var best = 0
            for (k in 1 until slot.size) if (slot[k] > slot[best]) best = k
            chars.append(ALPHABET[best])
            confidences.add(slot[best])
        }
        val text = chars.toString().trimEnd(PAD)
        val minConf = if (text.isEmpty()) 0f else confidences.subList(0, text.length).min()
        return Reading(text.replace(PAD.toString(), ""), minConf)
    }

    override fun close() {
        detector.close()
        reader.close()
    }

    companion object {
        const val DETECT_SIZE = 512
        const val DETECT_THRESHOLD = 0.4f
        private const val MIN_BOX_PX = 12
        private const val OCR_W = 128
        private const val OCR_H = 64
        private const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_"
        private const val PAD = '_'

        private fun readAsset(context: Context, name: String): ByteArray =
            context.assets.open(name).use { it.readBytes() }
    }
}

/**
 * Margin added around a detected plate before reading it. Tested on sample images: reading a
 * slightly loose crop is much more reliable than the detector's tight box (50/50 vs 39/50).
 */
internal const val PLATE_PAD_X = 0.15f
internal const val PLATE_PAD_Y = 0.20f
