package com.stormalert.data.repository

import com.stormalert.data.model.WeatherResponse
import com.stormalert.data.model.getWeatherCondition
import com.stormalert.data.network.WeatherApiService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeatherRepository @Inject constructor(
    private val weatherApiService: WeatherApiService
) {
    
    suspend fun getCurrentWeather(latitude: Double, longitude: Double): Result<WeatherResponse> {
        return try {
            val response = weatherApiService.getCurrentWeather(
                latitude = latitude,
                longitude = longitude
            )
            
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch weather data: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun analyzeStormRisk(weatherResponse: WeatherResponse): StormRisk {
        // Use enhanced current data if available, otherwise fall back to currentWeather
        val currentData = weatherResponse.current
        val fallbackWeather = weatherResponse.currentWeather
        
        val weatherCode = currentData?.weatherCode ?: fallbackWeather?.weatherCode ?: 0
        val windSpeed = currentData?.windSpeed10m ?: fallbackWeather?.windSpeed ?: 0.0
        
        val currentCondition = getWeatherCondition(weatherCode)
        val isCurrentlyStormy = currentCondition.isStormy
        
        // Check hourly data for approaching storms
        val hourlyData = weatherResponse.hourly
        val nextHoursStormRisk = hourlyData?.let { data ->
            val next6Hours = data.precipitationProbability?.take(6) ?: emptyList()
            val highPrecipitationHours = next6Hours.count { it > 70 }
            val moderatePrecipitationHours = next6Hours.count { it > 50 }
            val maxWindSpeed = data.windSpeed10m?.take(6)?.maxOrNull() ?: 0.0
            val currentPrecipitation = data.precipitation?.firstOrNull() ?: 0.0
            
            // Enhanced storm detection logic
            val isStormApproaching = when {
                isCurrentlyStormy -> true
                highPrecipitationHours >= 2 -> true
                moderatePrecipitationHours >= 3 && maxWindSpeed > 40 -> true
                maxWindSpeed > 60 -> true
                currentPrecipitation > 5.0 -> true
                else -> false
            }
            
            // Calculate storm probability
            val stormProbability = when {
                isCurrentlyStormy -> 1.0
                highPrecipitationHours >= 3 -> 0.9
                highPrecipitationHours >= 2 -> 0.8
                moderatePrecipitationHours >= 3 && maxWindSpeed > 40 -> 0.7
                maxWindSpeed > 60 -> 0.6
                currentPrecipitation > 5.0 -> 0.5
                else -> 0.2
            }
            
            // Estimate time to storm
            val estimatedTimeToStorm = if (isStormApproaching && !isCurrentlyStormy) {
                val firstHighPrecipIndex = next6Hours.indexOfFirst { it > 70 }
                val firstModeratePrecipIndex = next6Hours.indexOfFirst { it > 50 }
                
                when {
                    firstHighPrecipIndex >= 0 -> firstHighPrecipIndex * 15 // 15-minute intervals
                    firstModeratePrecipIndex >= 0 -> firstModeratePrecipIndex * 15
                    else -> 60 // Default to 1 hour if wind is high
                }
            } else {
                -1
            }
            
            StormRisk(
                isCurrentlyStormy = isCurrentlyStormy,
                isStormApproaching = isStormApproaching,
                stormProbability = stormProbability,
                estimatedTimeToStorm = estimatedTimeToStorm,
                currentCondition = currentCondition.description,
                windSpeed = windSpeed,
                precipitationProbability = hourlyData.precipitationProbability?.firstOrNull() ?: 0,
                currentPrecipitation = currentPrecipitation,
                maxWindSpeedNext6Hours = maxWindSpeed
            )
        } ?: StormRisk(
            isCurrentlyStormy = isCurrentlyStormy,
            isStormApproaching = false,
            stormProbability = if (isCurrentlyStormy) 1.0 else 0.0,
            estimatedTimeToStorm = -1,
            currentCondition = currentCondition.description,
            windSpeed = windSpeed,
            precipitationProbability = 0,
            currentPrecipitation = 0.0,
            maxWindSpeedNext6Hours = windSpeed
        )
        
        return nextHoursStormRisk
    }
}

data class StormRisk(
    val isCurrentlyStormy: Boolean,
    val isStormApproaching: Boolean,
    val stormProbability: Double,
    val estimatedTimeToStorm: Int, // in minutes, -1 if no storm approaching
    val currentCondition: String,
    val windSpeed: Double,
    val precipitationProbability: Int,
    val currentPrecipitation: Double = 0.0,
    val maxWindSpeedNext6Hours: Double = 0.0
)
