package com.stormalert.data.repository

import com.stormalert.data.network.RadarApiService
import com.stormalert.data.network.RainViewerApiService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RadarRepository @Inject constructor(
    private val radarApiService: RadarApiService,
    private val rainViewerApiService: RainViewerApiService
) {
    
    /**
     * Get latest radar image URL from MET Norway
     */
    suspend fun getLatestRadarImage(area: String = "nordic"): Result<String> {
        return try {
            val response = radarApiService.getRadarImage(area = area)
            
            if (response.isSuccessful) {
                val responseBody = response.body()
                if (responseBody != null && responseBody.uri != null) {
                    Result.success(responseBody.uri!!)
                } else {
                    Result.failure(Exception("No URI in response: ${responseBody}"))
                }
            } else {
                Result.failure(Exception("Failed to fetch radar image: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }
    
    /**
     * Get radar map tiles from RainViewer
     */
    suspend fun getRadarMaps(): Result<com.stormalert.data.network.RadarMapsResponse> {
        return try {
            val response = rainViewerApiService.getRadarMaps()
            
            if (response.isSuccessful && response.body() != null) {
                val mapsList = response.body()!!
                if (mapsList.isNotEmpty()) {
                    Result.success(mapsList[0]) // Use the first map in the list
                } else {
                    Result.failure(Exception("No radar maps available"))
                }
            } else {
                Result.failure(Exception("Failed to fetch radar maps: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }
    
    /**
     * Get RainViewer tile URL for a specific position and time
     */
    fun getRainViewerTileUrl(host: String, path: String, x: Int, y: Int, zoom: Int): String {
        return "https://$host$path/$zoom/$x/$y/1/0_0.png"
    }
}
