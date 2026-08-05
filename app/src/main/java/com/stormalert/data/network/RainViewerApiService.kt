package com.stormalert.data.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface RainViewerApiService {
    
    /**
     * Get radar map tiles from RainViewer
     * Documentation: https://www.rainviewer.com/api.html
     */
    @GET("public/weather-maps.json")
    suspend fun getRadarMaps(): Response<RadarMapsResponse>
    
    companion object {
        const val BASE_URL = "https://api.rainviewer.com/"
    }
}

data class RadarMapsResponse(
    val version: String,
    val generated: Long,
    val host: String,
    val radar: RadarData
)

data class RadarData(
    val past: List<RadarFrame>,
    val now: RadarFrame?,
    val future: List<RadarFrame>
)

data class RadarFrame(
    val time: Long,
    val path: String
)