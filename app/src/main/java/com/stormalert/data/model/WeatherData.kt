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
