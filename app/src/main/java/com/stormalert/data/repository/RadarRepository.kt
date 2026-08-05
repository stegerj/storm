package com.stormalert.data.repository

import com.stormalert.data.network.RadarApiService
import com.stormalert.data.network.RainViewerApiService
import com.stormalert.data.network.RadarMapsResponse
import com.stormalert.data.network.RadarFrame
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
    suspend fun getRadarMaps(): Result<RadarMapsResponse> {
        return try {
            val response = rainViewerApiService.getRadarMaps()
            
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch radar maps: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }
    
    /**
     * Get historical radar frames for storm movement analysis
     */
    suspend fun getHistoricalRadarFrames(numFrames: Int = 10): Result<List<RadarFrame>> {
        return try {
            val response = rainViewerApiService.getRadarMaps()
            
            if (response.isSuccessful && response.body() != null) {
                val radarData = response.body()!!.radar
                val pastFrames = radarData.past.takeLast(numFrames)
                Result.success(pastFrames)
            } else {
                Result.failure(Exception("Failed to fetch historical frames: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }
    
    /**
     * Get RainViewer tile URL for a specific position and time
     */
    fun getRainViewerTileUrl(host: String, path: String, x: Int, y: Int, zoom: Int, size: Int = 256): String {
        return "https://$host$path/$size/$zoom/$x/$y/0/1_0.png"
    }
    
    /**
     * Get composite radar tiles (2x2 grid) for better centering
     */
    fun getCompositeRadarTiles(host: String, path: String, centerTileX: Int, centerTileY: Int, zoom: Int = 7): List<TileUrl> {
        val tiles = mutableListOf<TileUrl>()
        val size = 256
        
        for (dx in listOf(-1, 0)) {
            for (dy in listOf(-1, 0)) {
                val x = centerTileX + dx
                val y = centerTileY + dy
                val url = getRainViewerTileUrl(host, path, x, y, zoom, size)
                tiles.add(TileUrl(url, dx, dy))
            }
        }
        
        return tiles
    }
    
    /**
     * Calculate tile coordinates from lat/lon
     */
    fun latLonToTileCoords(latitude: Double, longitude: Double, zoom: Int = 7): Pair<Int, Int> {
        val n = 1 shl zoom
        val x = ((longitude + 180) / 360 * n).toInt()
        val latRad = Math.toRadians(latitude)
        // asinh is not available in Kotlin Math, implement manually: asinh(x) = ln(x + sqrt(x^2 + 1))
        val tanLat = Math.tan(latRad)
        val asinhTanLat = Math.log(tanLat + Math.sqrt(tanLat * tanLat + 1))
        val y = ((1 - asinhTanLat / Math.PI) / 2 * n).toInt()
        return Pair(x, y)
    }
}

data class TileUrl(
    val url: String,
    val dx: Int,
    val dy: Int
)
