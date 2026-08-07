package com.stormalert.data.model

import com.google.gson.annotations.SerializedName

data class WeatherResponse(
    @SerializedName("latitude")
    val latitude: Double,
    @SerializedName("longitude")
    val longitude: Double,
    @SerializedName("current_weather")
    val currentWeather: CurrentWeather,
    @SerializedName("hourly")
    val hourly: HourlyData? = null,
    @SerializedName("daily")
    val daily: DailyData? = null
)

data class CurrentWeather(
    @SerializedName("temperature")
    val temperature: Double,
    @SerializedName("windspeed")
    val windSpeed: Double,
    @SerializedName("winddirection")
    val windDirection: Double,
    @SerializedName("weathercode")
    val weatherCode: Int,
    @SerializedName("time")
    val time: String
)

data class HourlyData(
    @SerializedName("time")
    val time: List<String>,
    @SerializedName("temperature_2m")
    val temperature: List<Double>? = null,
    @SerializedName("precipitation_probability")
    val precipitationProbability: List<Int>? = null,
    @SerializedName("precipitation")
    val precipitation: List<Double>? = null,
    @SerializedName("windspeed_10m")
    val windSpeed: List<Double>? = null
)

data class DailyData(
    @SerializedName("time")
    val time: List<String>,
    @SerializedName("weathercode")
    val weatherCode: List<Int>,
    @SerializedName("temperature_2m_max")
    val temperatureMax: List<Double>,
    @SerializedName("temperature_2m_min")
    val temperatureMin: List<Double>,
    @SerializedName("precipitation_probability_max")
    val precipitationProbabilityMax: List<Int>? = null,
    @SerializedName("windspeed_10m_max")
    val windSpeedMax: List<Double>? = null
)

// Weather code interpretation
data class WeatherCondition(
    val code: Int,
    val description: String,
    val isStormy: Boolean
)

fun getWeatherCondition(code: Int): WeatherCondition {
    return when (code) {
        0 -> WeatherCondition(code, "Clear sky", false)
        1 -> WeatherCondition(code, "Mainly clear", false)
        2 -> WeatherCondition(code, "Partly cloudy", false)
        3 -> WeatherCondition(code, "Overcast", false)
        45, 48 -> WeatherCondition(code, "Foggy", false)
        51, 53, 55 -> WeatherCondition(code, "Drizzle", false)
        56, 57 -> WeatherCondition(code, "Freezing drizzle", false)
        61, 63, 65 -> WeatherCondition(code, "Rain", false)
        66, 67 -> WeatherCondition(code, "Freezing rain", false)
        71, 73, 75 -> WeatherCondition(code, "Snow", false)
        77 -> WeatherCondition(code, "Snow grains", false)
        80, 81, 82 -> WeatherCondition(code, "Rain showers", false)
        85, 86 -> WeatherCondition(code, "Snow showers", false)
        95 -> WeatherCondition(code, "Thunderstorm", true)
        96, 99 -> WeatherCondition(code, "Thunderstorm with hail", true)
        else -> WeatherCondition(code, "Unknown", false)
    }
}

// FastAPI Storm Prediction Response Models
data class StormPredictionResponse(
    @SerializedName("latitude")
    val latitude: Double,
    @SerializedName("longitude")
    val longitude: Double,
    @SerializedName("current_weather")
    val currentWeather: CurrentWeather,
    @SerializedName("storm_probability")
    val stormProbability: StormProbability,
    @SerializedName("time_to_storm")
    val timeToStorm: TimeToStorm,
    @SerializedName("precipitation_forecast")
    val precipitationForecast: PrecipitationForecast,
    @SerializedName("radar_analysis")
    val radarAnalysis: RadarAnalysis,
    @SerializedName("forecast_data")
    val forecastData: ForecastData?,
    @SerializedName("risk_analysis")
    val riskAnalysis: MultiRadiusAnalysis?,
    @SerializedName("radar_image")
    val radarImage: String?,
    @SerializedName("analysis_time")
    val analysisTime: String
)

data class StormProbability(
    @SerializedName("probability")
    val probability: Double,
    @SerializedName("confidence_range")
    val confidenceRange: List<Double>,
    @SerializedName("storm_approaching")
    val stormApproaching: Boolean
)

data class TimeToStorm(
    @SerializedName("estimated_minutes")
    val estimatedMinutes: Double?,
    @SerializedName("confidence")
    val confidence: Double
)

data class PrecipitationForecast(
    @SerializedName("current_probability")
    val currentProbability: Int,
    @SerializedName("max_wind_next_6h")
    val maxWindNext6h: Double
)

data class RadarAnalysis(
    @SerializedName("intensity")
    val intensity: Double
)

data class ForecastData(
    @SerializedName("avg_speed_x")
    val avgSpeedX: Double,
    @SerializedName("avg_speed_y")
    val avgSpeedY: Double,
    @SerializedName("forecast_1h")
    val forecast1h: List<Double>,
    @SerializedName("forecast_5h")
    val forecast5h: List<Double>,
    @SerializedName("storm_centroids")
    val stormCentroids: List<StormCentroid>,
    @SerializedName("movements")
    val movements: List<MovementVector>,
    @SerializedName("acceleration")
    val acceleration: List<Double>
)

data class StormCentroid(
    @SerializedName("timestamp")
    val timestamp: Long,
    @SerializedName("x")
    val x: Double,
    @SerializedName("y")
    val y: Double,
    @SerializedName("pixel_count")
    val pixelCount: Int
)

data class MovementVector(
    @SerializedName("speed_x")
    val speedX: Double,
    @SerializedName("speed_y")
    val speedY: Double,
    @SerializedName("time_diff")
    val timeDiff: Double
)

data class MultiRadiusAnalysis(
    @SerializedName("current")
    val current: RiskAnalysis,
    @SerializedName("radius_20km")
    val radius20km: RiskAnalysis,
    @SerializedName("radius_100km")
    val radius100km: RiskAnalysis
)

data class RiskAnalysis(
    @SerializedName("radius_km")
    val radiusKm: Double,
    @SerializedName("distance_to_user_km")
    val distanceToUserKm: Double,
    @SerializedName("storm_speed_km_per_min")
    val stormSpeedKmPerMin: Double,
    @SerializedName("alignment")
    val alignment: Double,
    @SerializedName("is_approaching")
    val isApproaching: Boolean,
    @SerializedName("time_to_impact")
    val timeToImpact: Double?,
    @SerializedName("risk_level")
    val riskLevel: String,
    @SerializedName("forecast_1h_px")
    val forecast1hPx: List<Double>?,
    @SerializedName("forecast_5h_px")
    val forecast5hPx: List<Double>?
)
