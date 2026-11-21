package com.example.platescanner.scan

/**
 * Turns a stream of per-frame reads into discrete "sightings".
 *
 * - A plate must be read identically in [confirmFrames] frames within [confirmWindowMs]
 *   before it is recorded. This filters out one-off OCR misreads.
 * - While a plate stays in view it is not recorded again. A new sighting is recorded only
 *   after it has been out of view for at least [cooldownMs].
 *
 * Not thread-safe; CameraX delivers frames one at a time (each is closed before the next
 * arrives), so calls are sequential.
 */
class PlateTracker(
    private val confirmFrames: Int = 2,
    private val confirmWindowMs: Long = 2_000,
    private val cooldownMs: Long = 15_000,
) {
    private class Pending(var count: Int, val firstAt: Long)

    private val pending = HashMap<String, Pending>()
    private val lastSeen = HashMap<String, Long>()

    /** @return true if this read should be saved as a new sighting. */
    fun onRead(plate: String, now: Long): Boolean {
        prune(now)

        val last = lastSeen[plate]
        if (last != null && now - last < cooldownMs) {
            lastSeen[plate] = now // still in view: extend the cooldown
            return false
        }

        val p = pending[plate]
        val entry = if (p == null || now - p.firstAt > confirmWindowMs) {
            Pending(1, now)
        } else {
            p.apply { count++ }
        }

        return if (entry.count >= confirmFrames) {
            pending.remove(plate)
            lastSeen[plate] = now
            true
        } else {
            pending[plate] = entry
            false
        }
    }

    private fun prune(now: Long) {
        if (pending.size > 100) pending.entries.removeAll { now - it.value.firstAt > confirmWindowMs }
        if (lastSeen.size > 500) lastSeen.entries.removeAll { now - it.value > cooldownMs }
    }
}
