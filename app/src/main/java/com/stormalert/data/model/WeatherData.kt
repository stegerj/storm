package com.stormalert.data.model

import com.google.gson.annotations.SerializedName

data class WeatherResponse(
    @SerializedName("latitude")
    val latitude: Double,
    @SerializedName("longitude")
    val longitude: Double,
    @SerializedName("current")
    val current: CurrentWeatherData? = null,
    @SerializedName("current_weather")
    val currentWeather: CurrentWeather? = null,
    @SerializedName("hourly")
    val hourly: HourlyData? = null,
    @SerializedName("daily")
    val daily: DailyData? = null
)

data class CurrentWeatherData(
    @SerializedName("temperature_2m")
    val temperature: Double,
    @SerializedName("relative_humidity_2m")
    val relativeHumidity: Double,
    @SerializedName("apparent_temperature")
    val apparentTemperature: Double,
    @SerializedName("is_day")
    val isDay: Int,
    @SerializedName("precipitation")
    val precipitation: Double,
    @SerializedName("rain")
    val rain: Double,
    @SerializedName("showers")
    val showers: Double,
    @SerializedName("snowfall")
    val snowfall: Double,
    @SerializedName("weather_code")
    val weatherCode: Int,
    @SerializedName("cloud_cover")
    val cloudCover: Double,
    @SerializedName("pressure_msl")
    val pressureMsl: Double,
    @SerializedName("surface_pressure")
    val surfacePressure: Double,
    @SerializedName("wind_speed_10m")
    val windSpeed10m: Double,
    @SerializedName("wind_direction_10m")
    val windDirection10m: Double,
    @SerializedName("time")
    val time: String
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
    @SerializedName("relative_humidity_2m")
    val relativeHumidity: List<Double>? = null,
    @SerializedName("dew_point_2m")
    val dewPoint: List<Double>? = null,
    @SerializedName("apparent_temperature")
    val apparentTemperature: List<Double>? = null,
    @SerializedName("precipitation_probability")
    val precipitationProbability: List<Int>? = null,
    @SerializedName("precipitation")
    val precipitation: List<Double>? = null,
    @SerializedName("rain")
    val rain: List<Double>? = null,
    @SerializedName("showers")
    val showers: List<Double>? = null,
    @SerializedName("snowfall")
    val snowfall: List<Double>? = null,
    @SerializedName("snow_depth")
    val snowDepth: List<Double>? = null,
    @SerializedName("weather_code")
    val weatherCode: List<Int>? = null,
    @SerializedName("cloud_cover")
    val cloudCover: List<Double>? = null,
    @SerializedName("cloud_cover_low")
    val cloudCoverLow: List<Double>? = null,
    @SerializedName("cloud_cover_mid")
    val cloudCoverMid: List<Double>? = null,
    @SerializedName("cloud_cover_high")
    val cloudCoverHigh: List<Double>? = null,
    @SerializedName("visibility")
    val visibility: List<Double>? = null,
    @SerializedName("evapotranspiration")
    val evapotranspiration: List<Double>? = null,
    @SerializedName("wind_speed_10m")
    val windSpeed10m: List<Double>? = null,
    @SerializedName("wind_speed_80m")
    val windSpeed80m: List<Double>? = null,
    @SerializedName("wind_direction_10m")
    val windDirection10m: List<Double>? = null,
    @SerializedName("wind_direction_80m")
    val windDirection80m: List<Double>? = null,
    @SerializedName("wind_gusts_10m")
    val windGusts10m: List<Double>? = null,
    @SerializedName("uv_index")
    val uvIndex: List<Double>? = null
)

data class DailyData(
    @SerializedName("time")
    val time: List<String>,
    @SerializedName("weather_code")
    val weatherCode: List<Int>,
    @SerializedName("temperature_2m_max")
    val temperatureMax: List<Double>,
    @SerializedName("temperature_2m_min")
    val temperatureMin: List<Double>,
    @SerializedName("apparent_temperature_max")
    val apparentTemperatureMax: List<Double>? = null,
    @SerializedName("apparent_temperature_min")
    val apparentTemperatureMin: List<Double>? = null,
    @SerializedName("sunrise")
    val sunrise: List<String>? = null,
    @SerializedName("sunset")
    val sunset: List<String>? = null,
    @SerializedName("daylight_duration")
    val daylightDuration: List<Double>? = null,
    @SerializedName("sunshine_duration")
    val sunshineDuration: List<Double>? = null,
    @SerializedName("uv_index_max")
    val uvIndexMax: List<Double>? = null,
    @SerializedName("uv_index_clear_sky_max")
    val uvIndexClearSkyMax: List<Double>? = null,
    @SerializedName("precipitation_sum")
    val precipitationSum: List<Double>? = null,
    @SerializedName("rain_sum")
    val rainSum: List<Double>? = null,
    @SerializedName("showers_sum")
    val showersSum: List<Double>? = null,
    @SerializedName("snowfall_sum")
    val snowfallSum: List<Double>? = null,
    @SerializedName("precipitation_hours")
    val precipitationHours: List<Double>? = null,
    @SerializedName("precipitation_probability_max")
    val precipitationProbabilityMax: List<Int>? = null,
    @SerializedName("wind_speed_10m_max")
    val windSpeedMax: List<Double>? = null,
    @SerializedName("wind_gusts_10m_max")
    val windGustsMax: List<Double>? = null,
    @SerializedName("wind_direction_10m_dominant")
    val windDirectionDominant: List<Double>? = null
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
