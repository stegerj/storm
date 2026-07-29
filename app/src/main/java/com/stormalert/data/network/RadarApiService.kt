package com.stormalert.data.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface RadarApiService {
    
    /**
     * Get radar composite image from MET Norway
     * Documentation: https://api.met.no/weatherapi/radar/2.0/documentation
     */
    @GET("weatherapi/radar/2.0/")
    suspend fun getRadarImage(
        @Query("area") area: String = "nordic",
        @Query("type") type: String = "reflectivity",
        @Query("content") content: String = "image",
        @Query("time") time: String? = null // ISO 8601 format, null for latest
    ): Response<RadarResponse>
    
    companion object {
        const val BASE_URL = "https://api.met.no/"
    }
}

data class RadarResponse(
    val uri: String? = null,
    val created: String? = null,
    val expires: String? = null,
    val area: String? = null,
    val type: String? = null,
    val time: String? = null
)
