package com.example.platescanner.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Keeps the latest GPS/network position while recording, using Android's own LocationManager
 * (no Google Play services, no maps).
 */
class LocationTracker(context: Context) {

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(LocationManager::class.java)

    @Volatile private var latest: Location? = null
    private var running = false

    // Full object (not a lambda): on Android 9 and older the other methods are abstract.
    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) = offer(location)
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // checked by hasPermission()
    fun start() {
        if (running || manager == null || !hasPermission()) return
        running = true
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            try {
                if (!manager.isProviderEnabled(provider)) continue
                manager.getLastKnownLocation(provider)?.let { offer(it) }
                manager.requestLocationUpdates(provider, 2_000L, 0f, listener, Looper.getMainLooper())
            } catch (e: Exception) {
                Log.w("LocationTracker", "Can't use $provider", e)
            }
        }
    }

    fun stop() {
        if (!running) return
        running = false
        try {
            manager?.removeUpdates(listener)
        } catch (e: Exception) {
            Log.w("LocationTracker", "removeUpdates failed", e)
        }
    }

    /** The latest position if it's no older than [maxAgeMs], else null. */
    fun current(maxAgeMs: Long = 60_000): Location? {
        val loc = latest ?: return null
        val ageMs = (SystemClock.elapsedRealtimeNanos() - loc.elapsedRealtimeNanos) / 1_000_000
        return if (ageMs <= maxAgeMs) loc else null
    }

    /** Keep a newer fix, or a more accurate one from about the same time. */
    private fun offer(loc: Location) {
        val cur = latest
        if (cur == null) {
            latest = loc
            return
        }
        val newerMs = (loc.elapsedRealtimeNanos - cur.elapsedRealtimeNanos) / 1_000_000
        val moreAccurate = loc.hasAccuracy() && (!cur.hasAccuracy() || loc.accuracy < cur.accuracy)
        if (newerMs > 5_000 || (newerMs >= -5_000 && moreAccurate)) latest = loc
    }
}

/** Road, city and state for a position. */
data class Place(val road: String?, val city: String?, val state: String?)

/**
 * Looks up road / city / state with Android's built-in Geocoder. Needs internet and isn't
 * available on every phone; returns null when it can't answer.
 */
object PlaceLookup {
    private val cache = HashMap<String, Place>()

    @Suppress("DEPRECATION") // the blocking call is fine here; we're on Dispatchers.IO
    suspend fun lookup(context: Context, latitude: Double, longitude: Double): Place? =
        withContext(Dispatchers.IO) {
            if (!Geocoder.isPresent()) return@withContext null
            // ~10 m grid so nearby sightings reuse one lookup.
            val key = "%.4f,%.4f".format(Locale.US, latitude, longitude)
            synchronized(cache) { cache[key] }?.let { return@withContext it }
            try {
                val address = Geocoder(context, Locale.getDefault())
                    .getFromLocation(latitude, longitude, 1)
                    ?.firstOrNull()
                    ?: return@withContext null
                val place = Place(
                    road = address.thoroughfare,
                    city = address.locality ?: address.subAdminArea,
                    state = address.adminArea,
                )
                synchronized(cache) { cache[key] = place }
                place
            } catch (e: Exception) {
                Log.w("PlaceLookup", "Reverse geocode failed", e)
                null
            }
        }
}
