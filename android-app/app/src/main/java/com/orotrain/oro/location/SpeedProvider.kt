package com.orotrain.oro.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Source of the canoe's GPS ground speed in km/h (ADR-0018). [speedKmh] emits the latest raw fix, or
 * null when there is no usable fix (provider off, stopped, or no location yet). Smoothing and the
 * "no fix -> skip" decision live in the ViewModel's [com.orotrain.oro.model.SpeedSmoother].
 */
interface SpeedProvider {
    val speedKmh: StateFlow<Float?>
    fun start()
    fun stop()
}

/**
 * [SpeedProvider] over Android [LocationManager] using the GPS provider. No Google Play Services
 * dependency. Requires ACCESS_FINE_LOCATION at runtime; if it is missing, [start] is a no-op and
 * [speedKmh] stays null (the ViewModel then silently skips call-outs — ADR-0018).
 */
class GpsSpeedProvider(context: Context) : SpeedProvider {
    private val appContext = context.applicationContext
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _speedKmh = MutableStateFlow<Float?>(null)
    override val speedKmh: StateFlow<Float?> = _speedKmh.asStateFlow()

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            _speedKmh.value = if (location.hasSpeed()) location.speed * 3.6f else null
        }
        override fun onProviderDisabled(provider: String) { _speedKmh.value = null }
        override fun onProviderEnabled(provider: String) {}
        @Deprecated("Deprecated in API 29")
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
    }

    @SuppressLint("MissingPermission")
    override fun start() {
        if (!hasFineLocation()) {
            Log.w(TAG, "GpsSpeedProvider.start: ACCESS_FINE_LOCATION not granted; staying silent")
            return
        }
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                MIN_INTERVAL_MS,
                MIN_DISTANCE_M,
                listener,
                Looper.getMainLooper()
            )
        } catch (e: Exception) {
            Log.w(TAG, "GpsSpeedProvider.start failed: ${e.message}")
        }
    }

    override fun stop() {
        try { locationManager.removeUpdates(listener) } catch (_: Exception) {}
        _speedKmh.value = null
    }

    private fun hasFineLocation(): Boolean =
        appContext.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "GpsSpeedProvider"
        private const val MIN_INTERVAL_MS = 1_000L  // ~1 Hz fixes
        private const val MIN_DISTANCE_M = 0f       // time-based updates only
    }
}
