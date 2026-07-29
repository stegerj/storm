package com.stormalert.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationManager @Inject constructor(
    private val context: Context
) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    
    /**
     * Get the last known location
     */
    suspend fun getLastKnownLocation(): Location? {
        return if (hasLocationPermission()) {
            try {
                fusedLocationClient.lastLocation.await()
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }
    
    /**
     * Stream location updates
     */
    fun getLocationUpdates(intervalMillis: Long = 15000): Flow<Location> {
        return callbackFlow {
            if (!hasLocationPermission()) {
                close(IllegalStateException("Location permission not granted"))
                return@callbackFlow
            }
            
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                intervalMillis
            )
                .setMinUpdateIntervalMillis(intervalMillis / 2)
                .build()
            
            val locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { location ->
                        trySend(location)
                    }
                }
            }
            
            try {
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    context.mainLooper
                )
            } catch (e: Exception) {
                close(e)
                return@callbackFlow
            }
            
            awaitClose {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            }
        }.distinctUntilChanged { old, new ->
            old.latitude == new.latitude && old.longitude == new.longitude
        }
    }
    
    /**
     * Check if location permissions are granted
     */
    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Get the best available location permission
     */
    fun getBestLocationPermission(): String {
        return if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            Manifest.permission.ACCESS_FINE_LOCATION
        } else {
            Manifest.permission.ACCESS_COARSE_LOCATION
        }
    }
}

// Extension function to convert Task to suspend function
private suspend fun com.google.android.gms.tasks.Task<Location>.await(): Location? {
    return try {
        Tasks.await(this)
    } catch (e: Exception) {
        null
    }
}
