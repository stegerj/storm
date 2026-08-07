package com.stormalert.data.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface StormApiService {
    
    /**
     * Get storm prediction from FastAPI service
     */
    @POST("api/v2/storm/predict")
    suspend fun predictStorm(
        @Body request: StormPredictionRequest
    ): Response<com.stormalert.data.model.StormPredictionResponse>
    
    companion object {
        const val BASE_URL = "https://storm-n3iw.onrender.com/"
        const val LOCAL_BASE_URL = "http://10.0.2.2:8002/" // For Android emulator
    }
}

data class StormPredictionRequest(
    val latitude: Double,
    val longitude: Double,
    val include_radar: Boolean = true,
    val include_forecast: Boolean = true,
    val include_radar_image: Boolean = true,
    val overlay_mode: String = "all",
    val historical_frames: Int = 10
)
