package com.stormalert.ui.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stormalert.data.repository.StormRisk
import com.stormalert.data.repository.WeatherRepository
import com.stormalert.location.LocationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val weatherRepository: WeatherRepository,
    private val locationManager: LocationManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()
    
    private val _stormRisk = MutableStateFlow<StormRisk?>(null)
    val stormRisk: StateFlow<StormRisk?> = _stormRisk.asStateFlow()
    
    fun fetchWeather(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            _uiState.value = WeatherUiState.Loading
            
            weatherRepository.getCurrentWeather(latitude, longitude).fold(
                onSuccess = { weatherResponse ->
                    val stormRisk = weatherRepository.analyzeStormRisk(weatherResponse)
                    _stormRisk.value = stormRisk
                    _uiState.value = WeatherUiState.Success(weatherResponse)
                },
                onFailure = { error ->
                    _uiState.value = WeatherUiState.Error(error.message ?: "Unknown error")
                }
            )
        }
    }
    
    fun fetchWeatherWithCurrentLocation() {
        viewModelScope.launch {
            _uiState.value = WeatherUiState.Loading
            
            val location = locationManager.getLastKnownLocation()
            if (location != null) {
                fetchWeather(location.latitude, location.longitude)
            } else {
                // Try with manual location input or show helpful error
                _uiState.value = WeatherUiState.Error("Could not get your location. Please ensure location services are enabled and you've granted location permissions to the app.")
            }
        }
    }
    
    fun fetchWeatherWithDefaultLocation() {
        // Fallback method with known coordinates
        fetchWeather(59.91, 10.75) // Oslo, Norway
    }
    
    fun checkStormAlert() {
        val risk = _stormRisk.value
        if (risk != null && (risk.isCurrentlyStormy || risk.isStormApproaching)) {
            _uiState.value = WeatherUiState.StormAlert(risk)
        }
    }
}

sealed class WeatherUiState {
    object Loading : WeatherUiState()
    data class Success(val weatherData: com.stormalert.data.model.WeatherResponse) : WeatherUiState()
    data class Error(val message: String) : WeatherUiState()
    data class StormAlert(val stormRisk: StormRisk) : WeatherUiState()
}
