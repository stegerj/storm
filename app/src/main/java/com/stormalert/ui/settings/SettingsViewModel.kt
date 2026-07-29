package com.stormalert.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stormalert.service.WeatherCheckService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    init {
        loadSettings()
    }
    
    private fun loadSettings() {
        // In a real app, load from SharedPreferences or DataStore
        _uiState.value = SettingsUiState(
            enableAlerts = true,
            alertThreshold = 50,
            checkInterval = 15
        )
    }
    
    fun toggleAlerts(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(enableAlerts = enabled)
        // Save to SharedPreferences in real app
    }
    
    fun updateAlertThreshold(threshold: Int) {
        _uiState.value = _uiState.value.copy(alertThreshold = threshold)
        // Save to SharedPreferences in real app
    }
    
    fun updateCheckInterval(interval: Int) {
        _uiState.value = _uiState.value.copy(checkInterval = interval)
        // Save to SharedPreferences in real app
    }
    
    fun startBackgroundService() {
        WeatherCheckService.startService(context)
    }
    
    fun stopBackgroundService() {
        WeatherCheckService.stopService(context)
    }
}

data class SettingsUiState(
    val enableAlerts: Boolean = true,
    val alertThreshold: Int = 50,
    val checkInterval: Int = 15
)
