package com.stormalert.ui.radar

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import android.util.Base64
import android.graphics.BitmapFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarScreen(
    viewModel: RadarViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    var selectedOverlayMode by remember { mutableStateOf("all") }
    
    LaunchedEffect(Unit) {
        viewModel.loadStormPredictionWithLocation()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Weather Radar") },
                actions = {
                    IconButton(onClick = { 
                        viewModel.setOverlayMode(selectedOverlayMode)
                        viewModel.refreshWithCurrentLocation()
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
                    viewModel.refreshWithCurrentLocation()
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

@OptIn(ExperimentalMaterial3Api::class)
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
                label = { Text(mode.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }) }
            )
        }
    }
}

@Composable
fun StormPredictionContent(
    prediction: com.stormalert.data.model.StormPredictionResponse
) {
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var bitmapError by remember { mutableStateOf<String?>(null) }
    
    // Debug info
    val hasRadarImage = prediction.radarImage != null && prediction.radarImage.isNotEmpty()
    val radarImageLength = prediction.radarImage?.length ?: 0
    
    // Load bitmap asynchronously to prevent UI freezing
    LaunchedEffect(prediction.radarImage) {
        if (hasRadarImage) {
            isLoading = true
            bitmapError = null
            try {
                val bytes = android.util.Base64.decode(prediction.radarImage, android.util.Base64.DEFAULT)
                bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bitmap == null) {
                    bitmapError = "Failed to decode bitmap from base64 data (bytes: ${bytes.size})"
                }
            } catch (e: Exception) {
                bitmapError = "Failed to decode radar image: ${e.message}"
                bitmap = null
            }
            isLoading = false
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(8.dp)
    ) {
        // Debug info card
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
                    "API Response Info",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("Has radar image: $hasRadarImage", style = MaterialTheme.typography.bodySmall)
                Text("Image data length: $radarImageLength", style = MaterialTheme.typography.bodySmall)
                Text("Has risk analysis: ${prediction.riskAnalysis != null}", style = MaterialTheme.typography.bodySmall)
                Text("Has forecast data: ${prediction.forecastData != null}", style = MaterialTheme.typography.bodySmall)
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Storm Probability Card
        StormProbabilityCard(prediction.stormProbability)
        
        // Radar Image Display
        if (hasRadarImage) {
            if (isLoading) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Loading radar image...")
                        }
                    }
                }
            } else if (bitmap != null) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = "Radar Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        bitmapError ?: "Failed to load radar image",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        } else {
            // Show message when radar image is not available
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
                        "Radar Image",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "No radar image available from API",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
            if (probability.confidenceRange.isNotEmpty() && probability.confidenceRange.size >= 2) {
                Text("Confidence: ${probability.confidenceRange[0].toInt()}% - ${probability.confidenceRange[1].toInt()}%")
            } else if (probability.confidenceRange.isNotEmpty()) {
                Text("Confidence: ${probability.confidenceRange[0].toInt()}%")
            }
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
            containerColor = when (riskAnalysis.current.riskLevel?.uppercase()) {
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
            analysis.riskLevel?.uppercase() ?: "UNKNOWN",
            color = when (analysis.riskLevel?.uppercase()) {
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
            if (forecastData.forecast1h.size >= 2) {
                Text("1h Forecast: (${String.format("%.1f", forecastData.forecast1h[0])}, ${String.format("%.1f", forecastData.forecast1h[1])})")
            }
            if (forecastData.forecast5h.size >= 2) {
                Text("5h Forecast: (${String.format("%.1f", forecastData.forecast5h[0])}, ${String.format("%.1f", forecastData.forecast5h[1])})")
            }
            Text("Storm Centroids: ${forecastData.stormCentroids.size}")
        }
    }
}
