package com.example.platescanner.scan

import android.graphics.Rect
import com.google.mlkit.vision.text.Text

/** A piece of recognised text that looks like a license plate. */
data class PlateCandidate(
    /** Normalised plate number: upper-case A–Z and 0–9 only, e.g. "ABC1234". */
    val plate: String,
    /** Text as read, e.g. "ABC-1234". */
    val rawText: String,
    /** Bounding box of the text in the upright frame. */
    val box: Rect,
)

/**
 * Decides which lines of OCR text are probably license plates.
 *
 * The rules are deliberately generic so they work for most countries. To tighten them for one
 * region, add a pattern to [REGION_PATTERNS] (for example the UK format "AB12CDE" is
 * `Regex("^[A-Z]{2}[0-9]{2}[A-Z]{3}$")`). When that list is non-empty, a plate must match
 * at least one of its patterns.
 */
object PlateTextFilter {

    private val GENERIC = Regex("^[A-Z0-9]{4,8}$")

    /** Optional region-specific formats. Empty = accept any plausible plate. */
    val REGION_PATTERNS: List<Regex> = emptyList()

    /** Plates are short and wide: width / height of the text line. */
    private const val MIN_ASPECT = 1.6f
    private const val MAX_ASPECT = 11f

    fun normalize(raw: String): String =
        raw.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }

    fun isPlausible(plate: String): Boolean {
        if (!GENERIC.matches(plate)) return false
        val digits = plate.count { it.isDigit() }
        val letters = plate.length - digits
        if (digits == 0) return false                    // plain words: "STOP", "TAXI", "FORD"
        if (letters == 0 && plate.length < 5) return false // years, house numbers, clocks
        if (REGION_PATTERNS.isNotEmpty() && REGION_PATTERNS.none { it.matches(plate) }) return false
        return true
    }

    fun extract(text: Text): List<PlateCandidate> {
        val result = LinkedHashMap<String, PlateCandidate>()
        for (block in text.textBlocks) {
            for (line in block.lines) {
                // Try the whole line first ("ABC 1234"); if it carries extra words
                // ("CALIFORNIA 7ABC123"), fall back to single words and adjacent word pairs.
                val lineBox = line.boundingBox ?: continue
                if (consider(line.text, lineBox, result)) continue
                val els = line.elements
                for (i in els.indices) {
                    els[i].boundingBox?.let { consider(els[i].text, it, result) }
                    if (i + 1 < els.size) {
                        val a = els[i].boundingBox
                        val b = els[i + 1].boundingBox
                        if (a != null && b != null) {
                            consider(els[i].text + " " + els[i + 1].text, Rect(a).apply { union(b) }, result)
                        }
                    }
                }
            }
        }
        return result.values.toList()
    }

    /** @return true if [text] was accepted as a plate. */
    private fun consider(text: String, box: Rect, out: MutableMap<String, PlateCandidate>): Boolean {
        val raw = text.trim()
        if (box.height() <= 0) return false

        // Plates have at most a couple of separators and are written in capitals.
        if (raw.count { it == ' ' } > 2) return false
        if (raw.count { it.isLowerCase() } > 1) return false

        val aspect = box.width().toFloat() / box.height()
        if (aspect < MIN_ASPECT || aspect > MAX_ASPECT) return false

        val plate = normalize(raw)
        if (!isPlausible(plate)) return false
        if (plate !in out) out[plate] = PlateCandidate(plate, raw, Rect(box))
        return true
    }
}
