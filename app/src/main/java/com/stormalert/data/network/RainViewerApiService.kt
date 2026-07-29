package com.stormalert.data.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface RainViewerApiService {
    
    /**
     * Get radar map tiles from RainViewer
     * Documentation: https://www.rainviewer.com/api.html
     */
    @GET("api/maps.json")
    suspend fun getRadarMaps(): Response<List<RadarMapsResponse>>
    
    companion object {
        const val BASE_URL = "https://tilecache.rainviewer.com/"
    }
}

data class RadarMapsResponse(
    val version: Int,
    val generated: Long,
    val host: String,
    val radar: List<RadarPast>,
    val satellite: List<RadarPast>,
    val future: List<RadarFuture>
)

data class RadarPast(
    val time: Long,
    val path: String
)

data class RadarFuture(
    val time: Long,
    val path: String
)