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
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

class EmergencyLocationManager(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    fun getLastLocation(callback: (Location?) -> Unit) {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            Log.w("EmergencyLocation", "No location permissions granted.")
            callback(null)
            return
        }

        var isCallbackDelivered = false
        fun deliverResult(location: Location?) {
            if (!isCallbackDelivered) {
                isCallbackDelivered = true
                callback(location)
            }
        }

        val cancellationTokenSource = CancellationTokenSource()

        // 1. Try Google Play Services FusedLocationProviderClient (highest accuracy & reliability)
        try {
            // First check lastLocation for instant result
            fusedLocationClient.lastLocation
                .addOnSuccessListener { lastLoc ->
                    if (lastLoc != null && !isCallbackDelivered) {
                        Log.i("EmergencyLocation", "FusedLocation lastLocation acquired: ${lastLoc.latitude}, ${lastLoc.longitude}")
                        deliverResult(lastLoc)
                    }
                }

            // Also request fresh current high-accuracy location
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.token)
                .addOnSuccessListener { currLoc ->
                    if (currLoc != null && !isCallbackDelivered) {
                        Log.i("EmergencyLocation", "FusedLocation getCurrentLocation acquired: ${currLoc.latitude}, ${currLoc.longitude}")
                        deliverResult(currLoc)
                    }
                }
                .addOnFailureListener { e ->
                    Log.w("EmergencyLocation", "FusedLocation getCurrentLocation failed: ${e.message}")
                }
        } catch (e: SecurityException) {
            Log.e("EmergencyLocation", "SecurityException requesting fused location", e)
        } catch (e: Exception) {
            Log.e("EmergencyLocation", "Exception requesting fused location", e)
        }

        // 2. Fallback: Query native LocationManager last known location across all available providers
        try {
            val bestNativeLoc = getBestLastKnownNativeLocation()
            if (bestNativeLoc != null && !isCallbackDelivered) {
                Log.i("EmergencyLocation", "Native best last known location acquired: ${bestNativeLoc.latitude}, ${bestNativeLoc.longitude}")
                deliverResult(bestNativeLoc)
            }
        } catch (e: Exception) {
            Log.e("EmergencyLocation", "Error getting best native location", e)
        }

        // 3. Fallback: Request a single fresh update using native LocationManager with Looper.getMainLooper()
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

            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            for (p in providers) {
                if (locationManager?.isProviderEnabled(p) == true) {
                    locationManager.requestLocationUpdates(p, 0L, 0f, listener, Looper.getMainLooper())
                }
            }

            // Fallback timeout after 3.5 seconds if no callback delivered yet
            mainHandler.postDelayed({
                if (!isCallbackDelivered) {
                    try {
                        locationManager?.removeUpdates(listener)
                        cancellationTokenSource.cancel()
                    } catch (e: Exception) {}
                    
                    val fallback = getBestLastKnownNativeLocation()
                    Log.i("EmergencyLocation", "Timeout reached. Delivering fallback location: $fallback")
                    deliverResult(fallback)
                }
            }, 3500)
        } catch (e: Exception) {
            Log.e("EmergencyLocation", "Error setting native location listener", e)
            if (!isCallbackDelivered) {
                deliverResult(getBestLastKnownNativeLocation())
            }
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
                    if (bestLocation == null || loc.accuracy < bestLocation.accuracy || loc.time > bestLocation.time) {
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
