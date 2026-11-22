package com.example.platescanner.scan

import android.graphics.Bitmap
import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Turns per-frame plate reads into saved sightings, choosing the text by **voting** across
 * every read of the plate while it's in view.
 *
 * Why: a single read can be wrong, especially the first ones when a plate is small and blurry.
 * Saving as soon as two reads roughly agree (the previous approach) locked in those early
 * mistakes. In a simulation of an approaching car, voting raised correct saves from 78% to
 * 98%.
 *
 * How:
 * - Reads that look like the same plate ([similar]: look-alike characters, one or two
 *   differences, partial reads) are grouped into a *track*.
 * - A plate is saved **as soon as [agreeReads] reads give exactly the same text** (about one
 *   second of view), while it's still in view.
 * - If that never happens, it's saved when it leaves view for [settleMs] (or after
 *   [maxWaitMs]) with a per-character vote: each read votes with its character confidence,
 *   weighted towards larger (closer, sharper) views. Needs at least [minReads] reads; one-off
 *   reads are noise.
 * - Simulation (approaching car): 3 identical reads 96% correct; vote-only 98%; saving at
 *   2 identical reads only 91%.
 * - The saved photo is the largest (clearest) crop whose own reading is exactly the saved text,
 *   so the photo always shows what was read.
 * - After saving, further reads just keep the plate "in view". It can be saved again once it
 *   hasn't been seen for [cooldownMs] (Settings).
 *
 * Methods are synchronized: frames arrive on the analysis thread, [flush] on the main thread.
 */
class PlateTracker {
    /** Reads needed before a vote-based save; one-off reads are usually noise. */
    @Volatile var minReads: Int = 2

    /** Out of view this long = gone. */
    @Volatile var settleMs: Long = 1_200

    /** Longest a plate stays unsaved while in view before falling back to the vote. */
    @Volatile var maxWaitMs: Long = 3_000

    /** Changed from Settings. */
    @Volatile var cooldownMs: Long = 15_000

    /** Matching reads needed to save while in view (Settings). */
    @Volatile var agreeReads: Int = 3

    /** One plate read in one frame. */
    class Read(
        val text: String,
        val rawText: String,
        val confidences: FloatArray,
        val box: Rect,
        val crop: Bitmap,
        /** Where the phone was when this frame/photo was taken (null if unknown). */
        val location: android.location.Location? = null,
    )

    /** A plate to save. */
    class Result(
        val plate: String,
        val rawText: String,
        val crop: Bitmap,
        val vehicle: Bitmap,
        val location: android.location.Location? = null,
    )

    private class Track(now: Long) {
        val firstAt = now
        var lastAt = now
        val reads = ArrayList<Read>()
        /** For each distinct text read: its largest crop and the car shot from that frame. */
        val bestByText = HashMap<String, Pair<Read, Bitmap>>()
        var committed = false
        var savedText: String? = null
        val lastTexts = ArrayList<String>()
        var lastBox = Rect()
    }

    private val tracks = ArrayList<Track>()

    /**
     * Adds this frame's reads (may be empty) and returns any plates that are now ready to save.
     * [vehicleFor] crops the car shot from the current frame; it's only called when a read is
     * the largest view of its plate so far.
     */
    @Synchronized
    fun onFrame(now: Long, reads: List<Read>, vehicleFor: (Rect) -> Bitmap): List<Result> {
        for (r in reads) {
            // Same plate if the text looks alike, or if it's where a tracked plate just was
            // (very poor reads can differ by more than a couple of characters).
            val track = tracks
                .filter { t ->
                    t.lastTexts.any { similar(it, r.text) } ||
                        (now - t.lastAt <= NEARBY_MS && near(t.lastBox, r.box))
                }
                .maxByOrNull { it.lastAt }
                ?: Track(now).also { tracks.add(it) }
            track.lastAt = now
            track.lastBox = Rect(r.box)
            track.lastTexts.add(r.text)
            if (track.lastTexts.size > 8) track.lastTexts.removeAt(0)
            if (track.committed) continue // already saved; just keep it "in view"
            track.reads.add(r)
            if (track.reads.size > MAX_READS) track.reads.removeAt(0)
            val current = track.bestByText[r.text]
            if (current == null || r.box.width() > current.first.box.width()) {
                track.bestByText[r.text] = r to vehicleFor(r.box)
            }
        }
        return collect(now, force = false)
    }

    /** Where a plate reading stands, for the live view. */
    class Status(val saved: Boolean, val savedText: String?, val matches: Int, val needed: Int)

    /** Status of the track that [text] (just read) belongs to. */
    @Synchronized
    fun status(text: String): Status {
        val t = tracks.filter { tr -> tr.lastTexts.any { similar(it, text) } }.maxByOrNull { it.lastAt }
            ?: return Status(false, null, 0, agreeReads)
        if (t.committed) return Status(true, t.savedText, agreeReads, agreeReads)
        return Status(false, null, t.reads.count { it.text == text }, agreeReads)
    }

    /** Saves whatever is pending (e.g. when recording stops). */
    @Synchronized
    fun flush(now: Long): List<Result> = collect(now, force = true)

    private fun collect(now: Long, force: Boolean): List<Result> {
        val out = ArrayList<Result>()
        val iterator = tracks.iterator()
        while (iterator.hasNext()) {
            val t = iterator.next()
            if (t.committed) {
                if (now - t.lastAt > cooldownMs) iterator.remove()
                continue
            }
            // Early save: enough reads agree exactly.
            val agreed = t.reads.groupingBy { it.text }.eachCount()
                .filterValues { it >= agreeReads }
                .maxByOrNull { it.value }?.key

            val plate: String
            if (agreed != null) {
                plate = agreed
            } else {
                val settled = force || now - t.lastAt >= settleMs || now - t.firstAt >= maxWaitMs
                if (!settled) continue
                if (t.reads.size < minReads) {
                    if (force || now - t.lastAt >= settleMs) iterator.remove()
                    continue
                }
                val voted = vote(t.reads)
                plate = (if (voted.isNotEmpty()) PlateTextFilter.accept(voted) else null)
                    ?: t.bestByText.values.maxByOrNull { it.first.box.width() }?.first?.text
                    ?: continue
            }
            val largest = t.bestByText.values.maxByOrNull { it.first.box.width() } ?: continue
            // Prefer a photo that itself reads as the saved text.
            val (read, vehicle) = t.bestByText[plate] ?: largest
            // Location from the saved frame, or failing that from any read of this plate.
            val location = read.location ?: t.reads.lastOrNull { it.location != null }?.location
            out.add(Result(plate, read.rawText, read.crop, vehicle, location))
            t.committed = true
            t.savedText = plate
            t.reads.clear() // free bitmaps; from here on the track only keeps the plate "in view"
            t.bestByText.clear()
        }
        return out
    }

    companion object {
        private const val MAX_READS = 40
        private const val NEARBY_MS = 700L

        /** Box centres within one plate-width of each other. */
        private fun near(a: Rect, b: Rect): Boolean {
            if (a.isEmpty) return false
            val dx = (a.centerX() - b.centerX()).toDouble()
            val dy = (a.centerY() - b.centerY()).toDouble()
            return sqrt(dx * dx + dy * dy) <= maxOf(a.width(), b.width())
        }

        /**
         * Per-character weighted vote. First picks the most supported length, then for each
         * position the character with the highest total weight, where each read's weight is
         * its confidence in that character × √(plate width in px).
         */
        fun vote(reads: List<Read>): String {
            if (reads.isEmpty()) return ""
            val lengthScore = HashMap<Int, Double>()
            for (r in reads) {
                val mean = if (r.confidences.isEmpty()) 0.5 else r.confidences.average()
                lengthScore[r.text.length] =
                    (lengthScore[r.text.length] ?: 0.0) + mean * sqrt(r.box.width().toDouble())
            }
            val len = lengthScore.maxByOrNull { it.value }!!.key
            val sameLength = reads.filter { it.text.length == len }
            val out = StringBuilder(len)
            for (i in 0 until len) {
                val score = HashMap<Char, Double>()
                for (r in sameLength) {
                    val conf = r.confidences.getOrElse(i) { 0.5f }.toDouble()
                    score[r.text[i]] = (score[r.text[i]] ?: 0.0) + conf * sqrt(r.box.width().toDouble())
                }
                out.append(score.maxByOrNull { it.value }!!.key)
            }
            return out.toString()
        }

        /** Characters OCR commonly confuses on plates, mapped to one representative. */
        private fun canonical(c: Char): Char = when (c) {
            'O', 'Q', 'D' -> '0'
            'I', 'L', 'T' -> '1'
            'Z' -> '2'
            'A' -> '4'
            'S' -> '5'
            'G' -> '6'
            'B' -> '8'
            else -> c
        }

        fun fold(s: String): String = s.map(::canonical).joinToString("")

        /**
         * True if two reads are probably the same plate: after folding look-alike characters,
         * they differ by at most one edit (two for reads of 7+ characters, or 6+ when [loose]),
         * or one is contained in the other (a partial read).
         */
        fun similar(a: String, b: String, loose: Boolean = false): Boolean {
            if (a == b) return true
            val x = fold(a)
            val y = fold(b)
            if (x == y) return true
            if (minOf(x.length, y.length) >= 4 && (x.contains(y) || y.contains(x))) return true
            val longest = maxOf(x.length, y.length)
            // loose (used for merging saved plates): two differences already from 6 characters.
            val allowed = if (longest >= 7 || (loose && longest >= 6)) 2 else 1
            if (abs(x.length - y.length) > allowed) return false
            return editDistance(x, y) <= allowed
        }

        private fun editDistance(a: String, b: String): Int {
            var prev = IntArray(b.length + 1) { it }
            var cur = IntArray(b.length + 1)
            for (i in 1..a.length) {
                cur[0] = i
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                }
                val t = prev; prev = cur; cur = t
            }
            return prev[b.length]
        }
    }
}
