package com.example.acousticguard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat

class EmergencyLocationManager(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    fun getLastLocation(callback: (Location?) -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            callback(null)
            return
        }

        // Try to get a fresh location update first for better accuracy
        try {
            val providers = locationManager.getProviders(true)
            val provider = if (providers.contains(LocationManager.GPS_PROVIDER)) {
                LocationManager.GPS_PROVIDER
            } else if (providers.contains(LocationManager.NETWORK_PROVIDER)) {
                LocationManager.NETWORK_PROVIDER
            } else {
                null
            }

            if (provider != null) {
                locationManager.requestSingleUpdate(provider, object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        callback(location)
                    }
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }, null)
                
                // Fallback: If no update in 5 seconds, use last known location
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    val lastKnown = locationManager.getLastKnownLocation(provider)
                    callback(lastKnown)
                }, 5000)
            } else {
                callback(null)
            }
        } catch (e: Exception) {
            val lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            callback(lastKnown)
        }
    }
}
