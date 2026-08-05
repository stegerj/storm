package com.stormalert.ui.radar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stormalert.data.analysis.StormMovementAnalyzer
import com.stormalert.data.analysis.StormRiskAnalyzer
import com.stormalert.data.repository.RadarRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RadarViewModel @Inject constructor(
    private val radarRepository: RadarRepository,
    private val stormMovementAnalyzer: StormMovementAnalyzer,
    private val stormRiskAnalyzer: StormRiskAnalyzer
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<RadarUiState>(RadarUiState.Loading)
    val uiState: StateFlow<RadarUiState> = _uiState.asStateFlow()
    
    private val _forecastData = MutableStateFlow<StormMovementAnalyzer.ForecastData?>(null)
    val forecastData: StateFlow<StormMovementAnalyzer.ForecastData?> = _forecastData.asStateFlow()
    
    private val _riskAnalysis = MutableStateFlow<StormRiskAnalyzer.MultiRadiusAnalysis?>(null)
    val riskAnalysis: StateFlow<StormRiskAnalyzer.MultiRadiusAnalysis?> = _riskAnalysis.asStateFlow()
    
    fun loadLatestRadar(area: String = "nordic") {
        viewModelScope.launch {
            _uiState.value = RadarUiState.Loading
            
            // Try MET Norway first, fall back to RainViewer
            radarRepository.getLatestRadarImage(area).fold(
                onSuccess = { imageUrl ->
                    _uiState.value = RadarUiState.Success(imageUrl)
                },
                onFailure = { error ->
                    // Fall back to RainViewer
                    loadRainViewerMaps()
                }
            )
        }
    }
    
    fun loadRainViewerMaps(latitude: Double = 44.5, longitude: Double = 11.34) {
        viewModelScope.launch {
            _uiState.value = RadarUiState.Loading
            
            radarRepository.getRadarMaps().fold(
                onSuccess = { mapsResponse ->
                    // Load historical frames for analysis
                    loadHistoricalAnalysis(latitude, longitude, mapsResponse)
                },
                onFailure = { error ->
                    _uiState.value = RadarUiState.Error("Maps error: ${error.message}")
                }
            )
        }
    }
    
    private fun loadHistoricalAnalysis(
        latitude: Double,
        longitude: Double,
        mapsResponse: com.stormalert.data.network.RadarMapsResponse
    ) {
        viewModelScope.launch {
            radarRepository.getHistoricalRadarFrames(10).fold(
                onSuccess = { historicalFrames ->
                    // Analyze storm movement
                    val forecast = stormMovementAnalyzer.calculateStormMovement(historicalFrames)
                    _forecastData.value = forecast
                    
                    // Analyze storm risk at multiple radii
                    if (forecast != null) {
                        val riskAnalysis = stormRiskAnalyzer.analyzeStormAtRadii(
                            latitude, longitude, forecast
                        )
                        _riskAnalysis.value = riskAnalysis
                    }
                    
                    // Load current radar for display
                    val latestRadar = mapsResponse.radar.now ?: mapsResponse.radar.past.lastOrNull()
                    if (latestRadar != null) {
                        val (centerTileX, centerTileY) = radarRepository.latLonToTileCoords(latitude, longitude)
                        val tiles = radarRepository.getCompositeRadarTiles(
                            mapsResponse.host,
                            latestRadar.path,
                            centerTileX,
                            centerTileY
                        )
                        _uiState.value = RadarUiState.SuccessWithTiles(tiles, mapsResponse.host, latestRadar.path)
                    } else {
                        _uiState.value = RadarUiState.Error("No radar data available")
                    }
                },
                onFailure = { error ->
                    _uiState.value = RadarUiState.Error("Historical analysis error: ${error.message}")
                }
            )
        }
    }
    
    fun loadRadarWithForecast(latitude: Double, longitude: Double) {
        loadRainViewerMaps(latitude, longitude)
    }
}

sealed class RadarUiState {
    object Loading : RadarUiState()
    data class Success(val imageUrl: String) : RadarUiState()
    data class SuccessWithTiles(
        val tiles: List<com.stormalert.data.repository.TileUrl>,
        val host: String,
        val path: String
    ) : RadarUiState()
    data class Error(val message: String) : RadarUiState()
}
