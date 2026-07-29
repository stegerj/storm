package com.stormalert.ui.radar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stormalert.data.repository.RadarRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class RadarUiState {
    object Loading : RadarUiState()
    data class Success(val imageUrl: String) : RadarUiState()
    data class Error(val message: String) : RadarUiState()
}

@HiltViewModel
class RadarViewModel @Inject constructor(
    private val radarRepository: RadarRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<RadarUiState>(RadarUiState.Loading)
    val uiState: StateFlow<RadarUiState> = _uiState.asStateFlow()
    
    fun loadLatestRadar(area: String = "nordic") {
        viewModelScope.launch {
            _uiState.value = RadarUiState.Loading
            
            radarRepository.getLatestRadarImage(area).fold(
                onSuccess = { imageUrl ->
                    _uiState.value = RadarUiState.Success(imageUrl)
                },
                onFailure = { error ->
                    _uiState.value = RadarUiState.Error(error.message ?: "Unknown error")
                }
            )
        }
    }
    
    fun loadRainViewerMaps() {
        viewModelScope.launch {
            _uiState.value = RadarUiState.Loading
            
            radarRepository.getRadarMaps().fold(
                onSuccess = { mapsResponse ->
                    // For now, we'll just use the latest radar image
                    val latestRadar = mapsResponse.radar.firstOrNull()
                    if (latestRadar != null) {
                        val imageUrl = radarRepository.getRainViewerTileUrl(
                            mapsResponse.host,
                            latestRadar.path,
                            0, 0, 1 // Default tile coordinates
                        )
                        _uiState.value = RadarUiState.Success(imageUrl)
                    } else {
                        _uiState.value = RadarUiState.Error("No radar data available")
                    }
                },
                onFailure = { error ->
                    _uiState.value = RadarUiState.Error(error.message ?: "Unknown error")
                }
            )
        }
    }
}
