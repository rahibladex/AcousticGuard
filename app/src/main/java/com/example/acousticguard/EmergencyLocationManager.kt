package com.example.acousticguard

import android.Manifest
import android.content.Context
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
        @Volatile
        var lastKnownLocation: Location? = null
            private set

        fun updateCachedLocation(location: Location?) {
            if (location != null && isValidLocation(location)) {
                lastKnownLocation = location
            }
        }

        fun isValidLocation(loc: Location?): Boolean {
            if (loc == null) return false
            val lat = loc.latitude
            val lng = loc.longitude
            return lat != 0.0 && lng != 0.0 && lat in -90.0..90.0 && lng in -180.0..180.0
        }
    }

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private var continuousLocationCallback: LocationCallback? = null
    private var nativeLocationListener: LocationListener? = null

    /**
     * Synchronously returns the immediate best location known to the device.
     */
    fun getImmediateBestLocation(): Location? {
        if (isValidLocation(lastKnownLocation)) {
            return lastKnownLocation
        }
        val nativeLoc = getBestLastKnownNativeLocation()
        if (isValidLocation(nativeLoc)) {
            lastKnownLocation = nativeLoc
            return nativeLoc
        }
        return null
    }

    /**
     * Gets location with immediate cache return and asynchronous high-accuracy refinement.
     */
    fun getLastLocation(callback: (Location?) -> Unit) {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            Log.w("EmergencyLocation", "No location permissions granted.")
            callback(getImmediateBestLocation())
            return
        }

        // 1. If we have ANY valid cached location from memory or OS, return it immediately so SMS is instant!
        val immediateLoc = getImmediateBestLocation()
        if (immediateLoc != null) {
            Log.i("EmergencyLocation", "Delivering immediate location for instant SMS: ${immediateLoc.latitude}, ${immediateLoc.longitude}")
            callback(immediateLoc)
            // Also trigger background refresh to update cache with freshest fix
            requestFreshFix {}
            return
        }

        // 2. If no cached location exists at all, actively query Fused + Native with fallback
        var delivered = false
        val timeoutRunnable = Runnable {
            if (!delivered) {
                delivered = true
                val fallback = getImmediateBestLocation()
                Log.w("EmergencyLocation", "GPS query timed out, returning fallback: $fallback")
                callback(fallback)
            }
        }
        mainHandler.postDelayed(timeoutRunnable, 4000)

        requestFreshFix { freshLoc ->
            if (!delivered && freshLoc != null) {
                delivered = true
                mainHandler.removeCallbacks(timeoutRunnable)
                updateCachedLocation(freshLoc)
                callback(freshLoc)
            }
        }
    }

    private fun requestFreshFix(onResult: (Location?) -> Unit) {
        try {
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
            Log.e("EmergencyLocation", "Error in requestFreshFix", e)
        }

        // Also query native location manager
        try {
            val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            for (p in providers) {
                if (locationManager?.isProviderEnabled(p) == true) {
                    val last = locationManager?.getLastKnownLocation(p)
                    if (isValidLocation(last)) {
                        updateCachedLocation(last)
                        onResult(last)
                    }
                }
            }
        } catch (e: Exception) {}
    }

    fun startContinuousLocationUpdates(onLocationChanged: ((Location) -> Unit)? = null) {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) return

        // 1. Initial warm-up from OS cache
        val initial = getImmediateBestLocation()
        if (initial != null) {
            updateCachedLocation(initial)
            onLocationChanged?.invoke(initial)
        }

        // 2. Fused Location Provider continuous updates
        try {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000)
                .setMinUpdateIntervalMillis(5_000)
                .setMinUpdateDistanceMeters(2f)
                .build()

            continuousLocationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val loc = result.lastLocation ?: return
                    if (isValidLocation(loc)) {
                        Log.d("EmergencyLocation", "Fused continuous location: ${loc.latitude}, ${loc.longitude}")
                        updateCachedLocation(loc)
                        onLocationChanged?.invoke(loc)
                    }
                }
            }

            continuousLocationCallback?.let {
                fusedLocationClient.requestLocationUpdates(request, it, Looper.getMainLooper())
            }
        } catch (e: Exception) {
            Log.e("EmergencyLocation", "Error starting Fused location updates", e)
        }

        // 3. Native LocationManager continuous updates (Network & GPS fallback)
        try {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (isValidLocation(location)) {
                        Log.d("EmergencyLocation", "Native continuous location: ${location.latitude}, ${location.longitude}")
                        updateCachedLocation(location)
                        onLocationChanged?.invoke(location)
                    }
                }
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            nativeLocationListener = listener

            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            for (p in providers) {
                if (locationManager?.isProviderEnabled(p) == true) {
                    locationManager.requestLocationUpdates(p, 5000L, 2f, listener, Looper.getMainLooper())
                }
            }
        } catch (e: Exception) {
            Log.e("EmergencyLocation", "Error starting native location listener", e)
        }
    }

    fun stopContinuousLocationUpdates() {
        try {
            continuousLocationCallback?.let {
                fusedLocationClient.removeLocationUpdates(it)
            }
            continuousLocationCallback = null
        } catch (e: Exception) {
            Log.e("EmergencyLocation", "Error stopping Fused location updates", e)
        }

        try {
            nativeLocationListener?.let {
                locationManager?.removeUpdates(it)
            }
            nativeLocationListener = null
        } catch (e: Exception) {
            Log.e("EmergencyLocation", "Error stopping native location updates", e)
        }
    }

    fun getBestLastKnownNativeLocation(): Location? {
        val mgr = locationManager ?: return null
        var bestLocation: Location? = null

        val providerList = mutableListOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            providerList.add(LocationManager.FUSED_PROVIDER)
        }

        for (provider in providerList) {
            try {
                if (mgr.allProviders.contains(provider)) {
                    val loc = mgr.getLastKnownLocation(provider) ?: continue
                    if (isValidLocation(loc)) {
                        if (bestLocation == null || loc.time > bestLocation.time) {
                            bestLocation = loc
                        }
                    }
                }
            } catch (e: SecurityException) {
                // Ignore permission issues for individual providers
            } catch (e: Exception) {
                // Ignore errors for individual providers
            }
        }
        return bestLocation
    }
}
