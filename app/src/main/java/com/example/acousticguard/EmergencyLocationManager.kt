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
            if (location != null) {
                lastKnownLocation = location
            }
        }
    }

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private var continuousLocationCallback: LocationCallback? = null

    fun getLastLocation(callback: (Location?) -> Unit) {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            Log.w("EmergencyLocation", "No location permissions granted.")
            callback(lastKnownLocation)
            return
        }

        // 1. Check if we already have a reasonably fresh cached location
        val cached = lastKnownLocation ?: getBestLastKnownNativeLocation()
        if (cached != null) {
            val ageMs = System.currentTimeMillis() - cached.time
            if (ageMs < 120_000) { // Fresh within 2 minutes
                Log.i("EmergencyLocation", "Delivering fresh cached location: ${cached.latitude}, ${cached.longitude} (age: ${ageMs / 1000}s)")
                callback(cached)
                return
            }
        }

        var isCallbackDelivered = false
        fun deliverResult(location: Location?) {
            if (!isCallbackDelivered && location != null) {
                isCallbackDelivered = true
                updateCachedLocation(location)
                callback(location)
            }
        }

        val cancellationTokenSource = CancellationTokenSource()

        // 2. Query Google Play Services FusedLocationProviderClient (lastLocation + fresh high accuracy)
        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { lastLoc ->
                    if (lastLoc != null) {
                        Log.i("EmergencyLocation", "FusedLocation lastLocation acquired: ${lastLoc.latitude}, ${lastLoc.longitude}")
                        deliverResult(lastLoc)
                    }
                }

            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.token)
                .addOnSuccessListener { currLoc ->
                    if (currLoc != null) {
                        Log.i("EmergencyLocation", "FusedLocation getCurrentLocation (High Accuracy) acquired: ${currLoc.latitude}, ${currLoc.longitude}")
                        deliverResult(currLoc)
                    }
                }
                .addOnFailureListener {
                    // Try balanced power accuracy if high accuracy fails
                    try {
                        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellationTokenSource.token)
                            .addOnSuccessListener { balLoc ->
                                if (balLoc != null) {
                                    Log.i("EmergencyLocation", "FusedLocation getCurrentLocation (Balanced) acquired: ${balLoc.latitude}, ${balLoc.longitude}")
                                    deliverResult(balLoc)
                                }
                            }
                    } catch (e: Exception) {}
                }
        } catch (e: Exception) {
            Log.e("EmergencyLocation", "Error querying FusedLocationProviderClient", e)
        }

        // 3. Fallback: Query native LocationManager last known location
        try {
            val bestNativeLoc = getBestLastKnownNativeLocation()
            if (bestNativeLoc != null) {
                deliverResult(bestNativeLoc)
            }
        } catch (e: Exception) {
            Log.e("EmergencyLocation", "Error getting best native location", e)
        }

        // 4. Fallback: Request single active update using native LocationManager (Network + GPS)
        try {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    Log.i("EmergencyLocation", "Native LocationListener update acquired: ${location.latitude}, ${location.longitude}")
                    try {
                        locationManager?.removeUpdates(this)
                    } catch (e: Exception) {}
                    deliverResult(location)
                }
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            for (p in providers) {
                try {
                    if (locationManager?.isProviderEnabled(p) == true) {
                        locationManager.requestLocationUpdates(p, 0L, 0f, listener, Looper.getMainLooper())
                    }
                } catch (e: Exception) {}
            }

            // Timeout after 6 seconds - if still nothing fresh, deliver best available fallback
            mainHandler.postDelayed({
                if (!isCallbackDelivered) {
                    isCallbackDelivered = true
                    try {
                        locationManager?.removeUpdates(listener)
                        cancellationTokenSource.cancel()
                    } catch (e: Exception) {}

                    val fallback = lastKnownLocation ?: getBestLastKnownNativeLocation()
                    Log.i("EmergencyLocation", "Timeout reached. Delivering fallback location: $fallback")
                    callback(fallback)
                }
            }, 6000)
        } catch (e: Exception) {
            Log.e("EmergencyLocation", "Error setting native location listener", e)
            if (!isCallbackDelivered) {
                isCallbackDelivered = true
                callback(lastKnownLocation ?: getBestLastKnownNativeLocation())
            }
        }
    }

    fun startContinuousLocationUpdates() {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) return

        try {
            val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 30_000)
                .setMinUpdateIntervalMillis(15_000)
                .build()

            continuousLocationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val loc = result.lastLocation ?: return
                    Log.d("EmergencyLocation", "Continuous location updated: ${loc.latitude}, ${loc.longitude}")
                    updateCachedLocation(loc)
                }
            }

            continuousLocationCallback?.let {
                fusedLocationClient.requestLocationUpdates(request, it, Looper.getMainLooper())
            }
        } catch (e: Exception) {
            Log.e("EmergencyLocation", "Error starting continuous location updates", e)
        }
    }

    fun stopContinuousLocationUpdates() {
        try {
            continuousLocationCallback?.let {
                fusedLocationClient.removeLocationUpdates(it)
            }
            continuousLocationCallback = null
        } catch (e: Exception) {
            Log.e("EmergencyLocation", "Error stopping continuous location updates", e)
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
                    if (bestLocation == null || loc.time > bestLocation.time) {
                        bestLocation = loc
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
