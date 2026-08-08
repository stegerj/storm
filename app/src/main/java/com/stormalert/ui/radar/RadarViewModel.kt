package com.stormalert.ui.radar

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stormalert.data.network.StormApiService
import com.stormalert.data.network.StormPredictionRequest
import com.stormalert.location.LocationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RadarViewModel @Inject constructor(
    private val stormApiService: StormApiService,
    private val locationManager: LocationManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<RadarUiState>(RadarUiState.Loading)
    val uiState: StateFlow<RadarUiState> = _uiState.asStateFlow()
    
    private val _overlayMode = MutableStateFlow("all")
    val overlayMode: StateFlow<String> = _overlayMode.asStateFlow()
    
    fun loadStormPrediction(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            _uiState.value = RadarUiState.Loading
            Log.d("RadarViewModel", "Loading storm prediction for lat=$latitude, lon=$longitude")
            
            val request = StormPredictionRequest(
                latitude = latitude,
                longitude = longitude,
                include_radar = true,
                include_forecast = true,
                include_radar_image = true,
                overlay_mode = _overlayMode.value,
                historical_frames = 10
            )
            
            try {
                Log.d("RadarViewModel", "Making API request")
                val response = stormApiService.predictStorm(request)
                Log.d("RadarViewModel", "API response code: ${response.code()}, successful: ${response.isSuccessful}")
                
                if (response.isSuccessful && response.body() != null) {
                    val prediction = response.body()!!
                    Log.d("RadarViewModel", "Prediction received, radarImage: ${prediction.radarImage != null}, stormProbability: ${prediction.stormProbability != null}")
                    _uiState.value = RadarUiState.Success(prediction)
                } else {
                    Log.e("RadarViewModel", "API error: ${response.code()}")
                    _uiState.value = RadarUiState.Error("API error: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("RadarViewModel", "Network error", e)
                _uiState.value = RadarUiState.Error("Network error: ${e.message}")
            }
        }
    }
    
    fun loadStormPredictionWithLocation() {
        viewModelScope.launch {
            _uiState.value = RadarUiState.Loading
            Log.d("RadarViewModel", "Loading storm prediction with location")
            
            try {
                val location = locationManager.getLastKnownLocation()
                Log.d("RadarViewModel", "Location received: $location")
                if (location != null) {
                    loadStormPrediction(location.latitude, location.longitude)
                } else {
                    Log.e("RadarViewModel", "Location is null")
                    _uiState.value = RadarUiState.Error("Location not available. Please enable location services.")
                }
            } catch (e: Exception) {
                Log.e("RadarViewModel", "Failed to get location", e)
                _uiState.value = RadarUiState.Error("Failed to get location: ${e.message}")
            }
        }
    }
    
    fun setOverlayMode(mode: String) {
        _overlayMode.value = mode
    }
    
    fun refreshWithCurrentLocation() {
        loadStormPredictionWithLocation()
    }
}

sealed class RadarUiState {
    object Loading : RadarUiState()
    data class Success(val prediction: com.stormalert.data.model.StormPredictionResponse) : RadarUiState()
    data class Error(val message: String) : RadarUiState()
}
