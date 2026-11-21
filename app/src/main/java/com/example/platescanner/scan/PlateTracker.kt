package com.example.platescanner.scan

import kotlin.math.abs

/**
 * Turns a stream of per-frame reads into discrete "sightings".
 *
 * OCR rarely reads a plate exactly the same way in every frame ("410PM", "4510PM", "A10PM"
 * can all be the same plate), so reads are matched *fuzzily* (see [similar]) rather than by
 * exact text:
 *
 * - A plate must be read (approximately) [confirmFrames] times within [confirmWindowMs]
 *   before it is recorded. This filters out one-off misreads.
 * - Once recorded, any similar read while the plate stays in view just keeps it "in view"
 *   and is not recorded again. A new sighting is recorded only after no similar read for
 *   at least [cooldownMs] (set in Settings).
 * - Later sightings that differ only by look-alike characters (0/O, 1/I, 8/B …) are saved
 *   under the plate number that was first recorded, so they don't become separate plates.
 *
 * Not thread-safe; CameraX delivers frames one at a time (each is closed before the next
 * arrives), so calls are sequential.
 */
class PlateTracker(
    private val confirmFrames: Int = 2,
    private val confirmWindowMs: Long = 2_000,
    /** How long a recorded plate is remembered for merging variant reads. */
    private val memoryMs: Long = 30 * 60_000L,
) {
    /** Changed from Settings; read on the analysis thread. */
    @Volatile var cooldownMs: Long = 15_000

    /** A plate that has been recorded: [plate] is the number it was saved under. */
    private class Track(val plate: String, var lastSeen: Long)

    private class Pending(val plate: String, var count: Int, val firstAt: Long)

    private val tracks = ArrayList<Track>()
    private val pending = ArrayList<Pending>()

    /**
     * @return the plate number to save a new sighting under (may be an earlier spelling of
     * the same plate), or null if nothing should be saved for this read.
     */
    fun onRead(read: String, now: Long): String? {
        prune(now)

        // Most recently seen similar plate (an older, stale match must not hide a fresh one).
        val track = tracks.filter { similar(it.plate, read) }.maxByOrNull { it.lastSeen }
        if (track != null && now - track.lastSeen < cooldownMs) {
            track.lastSeen = now // still in view: extend the cooldown
            return null
        }

        val p = pending.firstOrNull { similar(it.plate, read) && now - it.firstAt <= confirmWindowMs }
        if (p == null) {
            pending.add(Pending(read, 1, now))
            if (confirmFrames > 1) return null
        } else {
            p.count++
            if (p.count < confirmFrames) return null
            pending.remove(p)
        }

        // Confirmed: a new sighting. Reuse an earlier spelling only if it differs just by
        // look-alike characters (a near miss after the cooldown may be a different car).
        val same = tracks.firstOrNull { fold(it.plate) == fold(read) }
        return if (same != null) {
            same.lastSeen = now
            same.plate
        } else {
            tracks.add(Track(read, now))
            read
        }
    }

    private fun prune(now: Long) {
        pending.removeAll { now - it.firstAt > confirmWindowMs }
        tracks.removeAll { now - it.lastSeen > maxOf(memoryMs, cooldownMs) }
    }

    companion object {
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
         * they differ by at most one edit (two for reads of 7+ characters), or one is
         * contained in the other (a partial read).
         */
        fun similar(a: String, b: String): Boolean {
            if (a == b) return true
            val x = fold(a)
            val y = fold(b)
            if (x == y) return true
            if (minOf(x.length, y.length) >= 4 && (x.contains(y) || y.contains(x))) return true
            val allowed = if (maxOf(x.length, y.length) >= 7) 2 else 1
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
