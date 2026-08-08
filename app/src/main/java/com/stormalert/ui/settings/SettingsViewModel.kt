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
        // Load settings from SharedPreferences
        val prefs = context.getSharedPreferences("storm_alert_settings", Context.MODE_PRIVATE)
        _uiState.value = SettingsUiState(
            enableAlerts = prefs.getBoolean("enable_alerts", true),
            alertThreshold = prefs.getInt("alert_threshold", 50),
            checkInterval = prefs.getInt("check_interval", 15)
        )
    }
    
    fun toggleAlerts(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(enableAlerts = enabled)
        saveSettings()
    }
    
    fun updateAlertThreshold(threshold: Int) {
        _uiState.value = _uiState.value.copy(alertThreshold = threshold)
        saveSettings()
    }
    
    fun updateCheckInterval(interval: Int) {
        _uiState.value = _uiState.value.copy(checkInterval = interval)
        saveSettings()
    }
    
    private fun saveSettings() {
        val prefs = context.getSharedPreferences("storm_alert_settings", Context.MODE_PRIVATE)
        with(prefs.edit()) {
            putBoolean("enable_alerts", _uiState.value.enableAlerts)
            putInt("alert_threshold", _uiState.value.alertThreshold)
            putInt("check_interval", _uiState.value.checkInterval)
            apply()
        }
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
