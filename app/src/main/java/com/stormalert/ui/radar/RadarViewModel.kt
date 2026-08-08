package com.stormalert.ui.radar

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
                val response = stormApiService.predictStorm(request)
                if (response.isSuccessful && response.body() != null) {
                    _uiState.value = RadarUiState.Success(response.body()!!)
                } else {
                    _uiState.value = RadarUiState.Error("API error: ${response.code()}")
                }
            } catch (e: Exception) {
                _uiState.value = RadarUiState.Error("Network error: ${e.message}")
            }
        }
    }
    
    fun loadStormPredictionWithLocation() {
        viewModelScope.launch {
            _uiState.value = RadarUiState.Loading
            
            val location = locationManager.getLastKnownLocation()
            if (location != null) {
                loadStormPrediction(location.latitude, location.longitude)
            } else {
                // Fallback to default coordinates if location not available
                loadStormPrediction(44.5, 11.34)
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
