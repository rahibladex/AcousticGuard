package com.example.acousticguard

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

class EmergencyLocationManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "EmergencyLocationCache"
        private const val KEY_LAT = "cached_latitude"
        private const val KEY_LNG = "cached_longitude"
        private const val KEY_TIME = "cached_time"

        @Volatile
        var lastKnownLocation: Location? = null
            private set

        fun isValidLocation(loc: Location?): Boolean {
            if (loc == null) return false
            val lat = loc.latitude
            val lng = loc.longitude
            return lat != 0.0 && lng != 0.0 && lat in -90.0..90.0 && lng in -180.0..180.0
        }

        fun isValidCoordinates(lat: Double, lng: Double): Boolean {
            return lat != 0.0 && lng != 0.0 && lat in -90.0..90.0 && lng in -180.0..180.0
        }
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val locationManager: LocationManager? by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    }

    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var continuousFusedCallback: LocationCallback? = null
    private var continuousNativeListener: LocationListener? = null

    init {
        // Restore from disk cache on initialization
        if (lastKnownLocation == null) {
            val savedLoc = getSavedLocationFromPrefs()
            if (savedLoc != null) {
                lastKnownLocation = savedLoc
            }
        }
    }

    fun updateCachedLocation(location: Location?) {
        if (location != null && isValidLocation(location)) {
            lastKnownLocation = location
            saveLocationToPrefs(location)
        }
    }

    private fun saveLocationToPrefs(loc: Location?) {
        if (loc == null || !isValidLocation(loc)) return
        try {
            prefs.edit()
                .putString(KEY_LAT, loc.latitude.toString())
                .putString(KEY_LNG, loc.longitude.toString())
                .putLong(KEY_TIME, loc.time)
                .apply()
        } catch (e: Exception) {
            Log.e("EmergencyLocation", "Failed to save location to prefs", e)
        }
    }

    fun getSavedLocationFromPrefs(): Location? {
        return try {
            val latStr = prefs.getString(KEY_LAT, null) ?: return null
            val lngStr = prefs.getString(KEY_LNG, null) ?: return null
            val time = prefs.getLong(KEY_TIME, System.currentTimeMillis())
            val lat = latStr.toDoubleOrNull() ?: return null
            val lng = lngStr.toDoubleOrNull() ?: return null
            if (isValidCoordinates(lat, lng)) {
                Location("PersistentCache").apply {
                    this.latitude = lat
                    this.longitude = lng
                    this.time = time
                }
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Checks all synchronous sources in 0ms:
     * 1. Memory cache
     * 2. Native LocationManager (GPS, NETWORK, PASSIVE, FUSED)
     * 3. SharedPreferences persistent storage
     */
    fun getImmediateBestLocation(): Location? {
        if (isValidLocation(lastKnownLocation)) {
            return lastKnownLocation
        }

        val nativeLoc = getBestLastKnownNativeLocation()
        if (isValidLocation(nativeLoc)) {
            lastKnownLocation = nativeLoc
            saveLocationToPrefs(nativeLoc)
            return nativeLoc
        }

        val diskLoc = getSavedLocationFromPrefs()
        if (isValidLocation(diskLoc)) {
            lastKnownLocation = diskLoc
            return diskLoc
        }

        return null
    }

    fun getLastLocation(callback: (Location?) -> Unit) {
        // 1. If we have immediate coordinates, return immediately
        val immediate = getImmediateBestLocation()
        if (immediate != null) {
            Log.i("EmergencyLocation", "getLastLocation: Returning immediate coordinates: ${immediate.latitude}, ${immediate.longitude}")
            callback(immediate)
            requestActiveFreshFix {}
            return
        }

        // 2. Otherwise actively fetch fresh fix with 3s timeout
        var isDelivered = false
        val timeoutRunnable = Runnable {
            if (!isDelivered) {
                isDelivered = true
                val fallback = getImmediateBestLocation()
                Log.w("EmergencyLocation", "getLastLocation timeout: fallback = $fallback")
                callback(fallback)
            }
        }
        mainHandler.postDelayed(timeoutRunnable, 3000)

        requestActiveFreshFix { freshLoc ->
            if (!isDelivered && freshLoc != null) {
                isDelivered = true
                mainHandler.removeCallbacks(timeoutRunnable)
                updateCachedLocation(freshLoc)
                callback(freshLoc)
            }
        }
    }

    private fun requestActiveFreshFix(onResult: (Location?) -> Unit) {
        try {
            // Google Play Services Fused Location
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (isValidLocation(loc)) {
                    updateCachedLocation(loc)
                    onResult(loc)
                }
            }

            val cts = CancellationTokenSource()
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { loc ->
                    if (isValidLocation(loc)) {
                        updateCachedLocation(loc)
                        onResult(loc)
                    }
                }
                .addOnFailureListener {
                    try {
                        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                            .addOnSuccessListener { balLoc ->
                                if (isValidLocation(balLoc)) {
                                    updateCachedLocation(balLoc)
                                    onResult(balLoc)
                                }
                            }
                    } catch (e: Exception) {}
                }
        } catch (e: Exception) {
            Log.e("EmergencyLocation", "FusedLocation error in requestActiveFreshFix", e)
        }

        // Native providers
        try {
            val mgr = locationManager
            if (mgr != null) {
                val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                for (p in providers) {
                    try {
                        val last = mgr.getLastKnownLocation(p)
                        if (isValidLocation(last)) {
                            updateCachedLocation(last)
                            onResult(last)
                        }
                    } catch (e: Exception) {}
                }
            }
        } catch (e: Exception) {}
    }

    fun startContinuousLocationUpdates(onLocationChanged: ((Location) -> Unit)? = null) {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) return

        // 1. Deliver immediate position if available
        val initial = getImmediateBestLocation()
        if (initial != null) {
            updateCachedLocation(initial)
            onLocationChanged?.invoke(initial)
        }

        // 2. Fused location updates
        try {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000)
                .setMinUpdateIntervalMillis(3_000)
                .setMinUpdateDistanceMeters(1f)
                .build()

            continuousFusedCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val loc = result.lastLocation ?: return
                    if (isValidLocation(loc)) {
                        Log.d("EmergencyLocation", "Fused streaming location: ${loc.latitude}, ${loc.longitude}")
                        updateCachedLocation(loc)
                        onLocationChanged?.invoke(loc)
                    }
                }
            }

            continuousFusedCallback?.let {
                fusedLocationClient.requestLocationUpdates(request, it, Looper.getMainLooper())
            }
        } catch (e: Exception) {
            Log.e("EmergencyLocation", "Error starting Fused continuous updates", e)
        }

        // 3. Native location updates (GPS + Cellular/Wi-Fi Network fallback)
        try {
            val mgr = locationManager
            if (mgr != null) {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (isValidLocation(location)) {
                            Log.d("EmergencyLocation", "Native streaming location: ${location.latitude}, ${location.longitude}")
                            updateCachedLocation(location)
                            onLocationChanged?.invoke(location)
                        }
                    }
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }
                continuousNativeListener = listener

                val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                for (p in providers) {
                    try {
                        if (mgr.isProviderEnabled(p)) {
                            mgr.requestLocationUpdates(p, 3000L, 1f, listener, Looper.getMainLooper())
                        }
                    } catch (e: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.e("EmergencyLocation", "Error starting native location listener", e)
        }
    }

    fun stopContinuousLocationUpdates() {
        try {
            continuousFusedCallback?.let {
                fusedLocationClient.removeLocationUpdates(it)
            }
            continuousFusedCallback = null
        } catch (e: Exception) {}

        try {
            continuousNativeListener?.let {
                locationManager?.removeUpdates(it)
            }
            continuousNativeListener = null
        } catch (e: Exception) {}
    }

    fun getBestLastKnownNativeLocation(): Location? {
        val mgr = locationManager ?: return null
        var bestLocation: Location? = null

        val providerList = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )

        for (provider in providerList) {
            try {
                val loc = mgr.getLastKnownLocation(provider) ?: continue
                if (isValidLocation(loc)) {
                    if (bestLocation == null || loc.time > bestLocation.time) {
                        bestLocation = loc
                    }
                }
            } catch (e: Exception) {}
        }
        return bestLocation
    }
}
