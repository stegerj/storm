package com.stormalert.ui.radar

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import java.util.Base64
import android.graphics.BitmapFactory
import com.stormalert.location.LocationManager
import androidx.hilt.navigation.compose.hiltViewModel as hiltViewModelLocation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarScreen(
    viewModel: RadarViewModel = hiltViewModel(),
    locationManager: LocationManager = hiltViewModelLocation()
) {
    val uiState by viewModel.uiState.collectAsState()
    val overlayMode by viewModel.overlayMode.collectAsState()
    
    var selectedOverlayMode by remember { mutableStateOf("all") }
    var userLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    
    LaunchedEffect(Unit) {
        // Get user's actual location
        val location = locationManager.getLastKnownLocation()
        if (location != null) {
            userLocation = Pair(location.latitude, location.longitude)
            viewModel.loadStormPrediction(location.latitude, location.longitude)
        } else {
            // Fallback to default coordinates if location not available
            viewModel.loadStormPrediction(44.5, 11.34)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Weather Radar") },
                actions = {
                    IconButton(onClick = { 
                        viewModel.setOverlayMode(selectedOverlayMode)
                        val (lat, lon) = userLocation ?: (44.5 to 11.34)
                        viewModel.refreshWithCurrentLocation(lat, lon)
                    }) {
                        Text("Refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Overlay Mode Selection
            OverlayModeSelector(
                selectedMode = selectedOverlayMode,
                onModeSelected = { mode ->
                    selectedOverlayMode = mode
                    viewModel.setOverlayMode(mode)
                    val (lat, lon) = userLocation ?: (44.5 to 11.34)
                    viewModel.refreshWithCurrentLocation(lat, lon)
                }
            )
            
            // Storm Prediction Display
            when (uiState) {
                is RadarUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                
                is RadarUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Error: ${(uiState as RadarUiState.Error).message}",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                
                is RadarUiState.Success -> {
                    val prediction = (uiState as RadarUiState.Success).prediction
                    StormPredictionContent(prediction)
                }
            }
        }
    }
}

@Composable
fun OverlayModeSelector(
    selectedMode: String,
    onModeSelected: (String) -> Unit
) {
    val modes = listOf("map", "radar", "arrows", "all")
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        modes.forEach { mode ->
            FilterChip(
                selected = selectedMode == mode,
                onClick = { onModeSelected(mode) },
                label = { Text(mode.capitalize()) }
            )
        }
    }
}

@Composable
fun StormPredictionContent(
    prediction: com.stormalert.data.model.StormPredictionResponse
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Storm Probability Card
        StormProbabilityCard(prediction.stormProbability)
        
        // Radar Image Display
        prediction.radarImage?.let { base64Image ->
            val bitmap = remember(base64Image) {
                val bytes = Base64.decode(base64Image, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
            
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Radar Image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentScale = ContentScale.Fit
                )
            }
        }
        
        // Risk Analysis Card
        prediction.riskAnalysis?.let { riskAnalysis ->
            RiskAnalysisCard(riskAnalysis)
        }
        
        // Forecast Data Card
        prediction.forecastData?.let { forecastData ->
            ForecastDataCard(forecastData)
        }
    }
}

@Composable
fun StormProbabilityCard(
    probability: com.stormalert.data.model.StormProbability
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (probability.stormApproaching) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                "Storm Probability",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Probability: ${(probability.probability * 100).toInt()}%")
            Text("Confidence: ${probability.confidenceRange[0].toInt()}% - ${probability.confidenceRange[1].toInt()}%")
            Text(
                "Storm Approaching: ${if (probability.stormApproaching) "Yes" else "No"}",
                color = if (probability.stormApproaching) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

@Composable
fun RiskAnalysisCard(
    riskAnalysis: com.stormalert.data.model.MultiRadiusAnalysis
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (riskAnalysis.current.riskLevel.uppercase()) {
                "CRITICAL", "HIGH" -> MaterialTheme.colorScheme.errorContainer
                "MEDIUM" -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                "Storm Risk Analysis",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            RiskLevelRow("Current Location", riskAnalysis.current)
            RiskLevelRow("20km Radius", riskAnalysis.radius20km)
            RiskLevelRow("100km Radius", riskAnalysis.radius100km)
        }
    }
}

@Composable
fun RiskLevelRow(
    label: String,
    analysis: com.stormalert.data.model.RiskAnalysis
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        Text(
            analysis.riskLevel.uppercase(),
            color = when (analysis.riskLevel.uppercase()) {
                "CRITICAL", "HIGH" -> MaterialTheme.colorScheme.error
                "MEDIUM" -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
fun ForecastDataCard(
    forecastData: com.stormalert.data.model.ForecastData
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                "Movement Forecast",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Avg Speed X: ${String.format("%.2f", forecastData.avgSpeedX)}")
            Text("Avg Speed Y: ${String.format("%.2f", forecastData.avgSpeedY)}")
            Text("1h Forecast: (${String.format("%.1f", forecastData.forecast1h[0])}, ${String.format("%.1f", forecastData.forecast1h[1])})")
            Text("5h Forecast: (${String.format("%.1f", forecastData.forecast5h[0])}, ${String.format("%.1f", forecastData.forecast5h[1])})")
            Text("Storm Centroids: ${forecastData.stormCentroids.size}")
        }
    }
}
