package com.stormalert.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.stormalert.data.repository.WeatherRepository
import com.stormalert.location.LocationManager
import com.stormalert.notification.StormNotificationManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class WeatherCheckService : Service() {
    
    @Inject
    lateinit var weatherRepository: WeatherRepository
    
    @Inject
    lateinit var locationManager: LocationManager
    
    @Inject
    lateinit var notificationManager: StormNotificationManager
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var checkJob: Job? = null
    private var isRunning = false
    
    companion object {
        private const val CHANNEL_ID = "weather_check_service"
        private const val NOTIFICATION_ID = 1002
        private const val CHECK_INTERVAL = 15 * 60 * 1000L // 15 minutes
        
        fun startService(context: Context) {
            val intent = Intent(context, WeatherCheckService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, WeatherCheckService::class.java)
            context.stopService(intent)
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            startForegroundNotification()
            startWeatherChecks()
            isRunning = true
        }
        return START_STICKY
    }
    
    private fun startForegroundNotification() {
        val notificationIntent = Intent(this, com.stormalert.ui.MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Storm Alert Service")
            .setContentText("Monitoring weather conditions...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
        
        startForeground(NOTIFICATION_ID, notification)
    }
    
    private fun startWeatherChecks() {
        checkJob = serviceScope.launch {
            while (isRunning) {
                try {
                    checkWeather()
                } catch (e: Exception) {
                    // Handle error, continue checking
                }
                delay(CHECK_INTERVAL)
            }
        }
    }
    
    private suspend fun checkWeather() {
        val location = locationManager.getLastKnownLocation()
        if (location != null) {
            weatherRepository.getCurrentWeather(location.latitude, location.longitude)
                .fold(
                    onSuccess = { weatherResponse ->
                        val stormRisk = weatherRepository.analyzeStormRisk(weatherResponse)
                        
                        if (stormRisk.isCurrentlyStormy || 
                            (stormRisk.isStormApproaching && stormRisk.stormProbability > 0.5)) {
                            notificationManager.showStormAlert(stormRisk)
                        }
                    },
                    onFailure = { /* Handle error */ }
                )
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Weather Check Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background service for weather monitoring"
            }
            
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        checkJob?.cancel()
    }
}
