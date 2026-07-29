package com.stormalert.ui.weather

import android.Manifest
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.shouldShowRationale
import com.stormalert.data.model.getWeatherCondition
import com.stormalert.data.repository.StormRisk
import kotlinx.coroutines.launch

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(
    viewModel: WeatherViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val stormRisk by viewModel.stormRisk.collectAsState()
    
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )
    
    // Check permissions and request location
    LaunchedEffect(permissionsState.allPermissionsGranted) {
        if (permissionsState.allPermissionsGranted) {
            viewModel.fetchWeatherWithCurrentLocation()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Storm Alert") },
                actions = {
                    IconButton(onClick = {
                        viewModel.fetchWeatherWithCurrentLocation()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                !permissionsState.allPermissionsGranted -> {
                    PermissionRequestContent(
                        onRequestPermission = { 
                            permissionsState.launchMultiplePermissionRequest()
                        }
                    )
                }
                
                uiState is WeatherUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                
                uiState is WeatherUiState.Error -> {
                    ErrorContent(
                        message = (uiState as WeatherUiState.Error).message,
                        onRetry = {
                            viewModel.fetchWeatherWithCurrentLocation()
                        },
                        onUseDefaultLocation = {
                            viewModel.fetchWeatherWithDefaultLocation()
                        }
                    )
                }
                
                uiState is WeatherUiState.Success -> {
                    val weatherData = (uiState as WeatherUiState.Success).weatherData
                    WeatherContent(
                        weatherData = weatherData,
                        stormRisk = stormRisk,
                        onCheckAlerts = { viewModel.checkStormAlert() }
                    )
                }
                
                uiState is WeatherUiState.StormAlert -> {
                    val risk = (uiState as WeatherUiState.StormAlert).stormRisk
                    StormAlertContent(
                        stormRisk = risk,
                        onDismiss = { 
                            // Dismiss alert and return to weather view
                            viewModel.fetchWeatherWithCurrentLocation()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionRequestContent(
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.LocationOn,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Location Permission Required",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Storm Alert needs your location to provide accurate weather alerts and radar data for your area.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRequestPermission) {
            Text("Grant Permission")
        }
    }
}

@Composable
fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    onUseDefaultLocation: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Error",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onRetry) {
                Text("Retry")
            }
            
            if (message.contains("location", ignoreCase = true)) {
                Button(
                    onClick = onUseDefaultLocation,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("Use Default Location")
                }
            }
        }
    }
}

@Composable
fun WeatherContent(
    weatherData: com.stormalert.data.model.WeatherResponse,
    stormRisk: StormRisk?,
    onCheckAlerts: () -> Unit
) {
    val scrollState = rememberScrollState()
    val condition = getWeatherCondition(weatherData.currentWeather.weatherCode)
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Current Weather Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (condition.isStormy) 
                    MaterialTheme.colorScheme.errorContainer 
                else 
                    MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    "Current Weather",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "${weatherData.currentWeather.temperature.toInt()}°C",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    condition.description,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Wind: ${weatherData.currentWeather.windSpeed.toInt()} km/h")
                stormRisk?.let {
                    Text("Precipitation: ${it.precipitationProbability}%")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Storm Risk Card
        stormRisk?.let { risk ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (risk.isCurrentlyStormy || risk.isStormApproaching)
                        MaterialTheme.colorScheme.errorContainer
                    else
                        MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (risk.isCurrentlyStormy || risk.isStormApproaching)
                                Icons.Default.Warning
                            else
                                Icons.Default.LocationOn,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Storm Risk",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Status: ${if (risk.isCurrentlyStormy) "Storm in progress" else if (risk.isStormApproaching) "Storm approaching" else "No storm risk"}")
                    Text("Probability: ${(risk.stormProbability * 100).toInt()}%")
                    if (risk.estimatedTimeToStorm > 0) {
                        Text("Estimated time: ${risk.estimatedTimeToStorm} minutes")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Check Alerts Button
        Button(
            onClick = onCheckAlerts,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Check for Storm Alerts")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Location Info
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    "Location",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Lat: ${weatherData.latitude}")
                Text("Lon: ${weatherData.longitude}")
            }
        }
    }
}

@Composable
fun StormAlertContent(
    stormRisk: StormRisk,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.Center),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Storm Alert!",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (stormRisk.isCurrentlyStormy)
                        "A storm is currently in your area."
                    else
                        "A storm is approaching your area.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Probability: ${(stormRisk.stormProbability * 100).toInt()}%")
                if (stormRisk.estimatedTimeToStorm > 0) {
                    Text("Estimated arrival: ${stormRisk.estimatedTimeToStorm} minutes")
                }
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onDismiss) {
                    Text("I Understand")
                }
            }
        }
    }
}
