package com.stormalert.ui.weather

import android.Manifest
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Thunderstorm
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
import com.stormalert.data.model.HourlyData
import com.stormalert.data.model.DailyData
import com.stormalert.data.repository.StormRisk
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

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
    onRetry: () -> Unit
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
        
        Button(onClick = onRetry) {
            Text("Retry")
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
    
    // Use enhanced current data if available, otherwise fall back to currentWeather
    val currentData = weatherData.current
    val fallbackWeather = weatherData.currentWeather
    val temperature = currentData?.temperature ?: fallbackWeather?.temperature ?: 0.0
    val weatherCode = currentData?.weatherCode ?: fallbackWeather?.weatherCode ?: 0
    val windSpeed = currentData?.windSpeed10m ?: fallbackWeather?.windSpeed ?: 0.0
    val condition = getWeatherCondition(weatherCode)
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
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
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Current Weather",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "${temperature.toInt()}°C",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        condition.description,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Wind: ${windSpeed.toInt()} km/h")
                    
                    // Show enhanced data if available
                    if (currentData != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Humidity: ${currentData.relativeHumidity.toInt()}%")
                        Text("Pressure: ${currentData.pressureMsl.toInt()} hPa")
                        Text("Feels like: ${currentData.apparentTemperature.toInt()}°C")
                    }
                    
                    stormRisk?.let {
                        Text("Precipitation: ${it.precipitationProbability}%")
                    }
                }
                
                // Weather Icon
                Icon(
                    imageVector = when (condition.code) {
                        0 -> Icons.Default.WbSunny
                        1, 2, 3 -> Icons.Default.WbCloudy
                        45, 48 -> Icons.Default.Cloud
                        51, 53, 55, 61, 63, 65, 80, 81, 82 -> Icons.Default.WaterDrop
                        71, 73, 75, 77, 85, 86 -> Icons.Default.AcUnit
                        95, 96, 99 -> Icons.Default.Thunderstorm
                        else -> Icons.Default.WbSunny
                    },
                    contentDescription = condition.description,
                    modifier = Modifier.size(80.dp),
                    tint = if (condition.isStormy) 
                        MaterialTheme.colorScheme.error 
                    else 
                        MaterialTheme.colorScheme.primary
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Hourly Forecast
        weatherData.hourly?.let { hourlyData ->
            HourlyForecastCard(hourlyData)
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Daily Forecast
        weatherData.daily?.let { dailyData ->
            DailyForecastCard(dailyData)
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Storm Risk Card with enhanced information
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
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Radar Analysis: Available",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Radar Analysis Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    "Advanced Radar Features",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("• Historical storm movement analysis", style = MaterialTheme.typography.bodySmall)
                Text("• Multi-radius risk assessment", style = MaterialTheme.typography.bodySmall)
                Text("• 1-hour and 5-hour storm forecasts", style = MaterialTheme.typography.bodySmall)
                Text("• Acceleration and trend detection", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "View Radar tab for detailed visualization",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
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
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Check Alerts Button
        Button(
            onClick = onCheckAlerts,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Check for Storm Alerts")
        }
        
        Spacer(modifier = Modifier.height(32.dp))
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

@Composable
fun HourlyForecastCard(hourlyData: HourlyData) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val hourFormat = remember { SimpleDateFormat("HH", Locale.getDefault()) }
    val isoFormat = remember { SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault()) }
    val hoursToShow = 24 // Show next 24 hours
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                "24-Hour Forecast",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            // Horizontal scrollable hourly forecast
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                for (i in 0 until minOf(hoursToShow, hourlyData.time.size)) {
                    val timeStr = hourlyData.time[i]
                    val time = if (timeStr.contains("T")) {
                        isoFormat.parse(timeStr)
                    } else {
                        timeFormat.parse(timeStr)
                    }
                    
                    val temp = hourlyData.temperature?.get(i)
                    val precipProb = hourlyData.precipitationProbability?.get(i)
                    
                    Column(
                        modifier = Modifier.width(60.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            time?.let { hourFormat.format(it) } ?: timeStr.takeLast(5).take(2),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        temp?.let {
                            Text(
                                "${it.toInt()}°",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        precipProb?.let {
                            Text(
                                "${it}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (it > 50) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DailyForecastCard(dailyData: DailyData) {
    val dayFormat = remember { SimpleDateFormat("EEE", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("MMM dd", Locale.getDefault()) }
    val inputDateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                "7-Day Forecast",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            dailyData.time.forEachIndexed { index, dateStr ->
                val date = try {
                    inputDateFormat.parse(dateStr)
                } catch (e: Exception) {
                    null
                }
                
                val weatherCode = dailyData.weatherCode.getOrNull(index)
                val maxTemp = dailyData.temperatureMax.getOrNull(index)
                val minTemp = dailyData.temperatureMin.getOrNull(index)
                val precipProb = dailyData.precipitationProbabilityMax?.getOrNull(index)
                val windSpeed = dailyData.windSpeedMax?.getOrNull(index)
                val condition = weatherCode?.let { getWeatherCondition(it) }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Day name
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            date?.let { dayFormat.format(it) } ?: "N/A",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            date?.let { dateFormat.format(it) } ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Weather condition
                    Column(
                        modifier = Modifier.weight(1.5f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            condition?.description ?: "Unknown",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    
                    // Temperature range
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        minTemp?.let {
                            Text(
                                "${it.toInt()}°",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(" - ")
                        maxTemp?.let {
                            Text(
                                "${it.toInt()}°",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    // Precipitation and wind
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End
                    ) {
                        precipProb?.let {
                            Text(
                                "${it}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (it > 50) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        windSpeed?.let {
                            Text(
                                "${it.toInt()} km/h",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                if (index < dailyData.time.size - 1) {
                    Divider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }
    }
}
