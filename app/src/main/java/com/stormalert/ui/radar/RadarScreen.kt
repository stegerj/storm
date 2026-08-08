package com.stormalert.ui.radar

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import android.util.Base64
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarScreen(
    viewModel: RadarViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    var selectedOverlayMode by remember { mutableStateOf("all") }
    
    LaunchedEffect(Unit) {
        Log.d("RadarScreen", "RadarScreen initialized, loading storm prediction")
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
                    Log.d("RadarScreen", "Success state, prediction: $prediction")
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
    prediction: com.stormalert.data.model.StormPredictionResponse?
) {
    Log.d("StormPredictionContent", "StormPredictionContent called, prediction: ${prediction != null}")
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(8.dp)
    ) {
        // Simple debug info first
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
                    "Debug Info",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("Prediction loaded: ${prediction != null}", style = MaterialTheme.typography.bodySmall)
                if (prediction != null) {
                    Text("Has radar image: ${prediction.radarImage != null}", style = MaterialTheme.typography.bodySmall)
                    Text("Image length: ${prediction.radarImage?.length ?: 0}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Storm Probability Card
        prediction?.stormProbability?.let { stormProbability ->
            StormProbabilityCard(stormProbability)
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Radar Image Display with multiple variants
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Radar Images", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                
                // Use new radar_images field if available
                if (prediction?.radarImages != null && prediction.radarImages.isNotEmpty()) {
                    prediction.radarImages.forEach { variant ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            variant.description,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        val imageBytes = Base64.decode(variant.image, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = variant.description,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp),
                                contentScale = ContentScale.FillWidth
                            )
                        } else {
                            Text("Failed to decode radar image", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                } 
                // Fallback to old radar_image field for backward compatibility
                else if (prediction?.radarImage != null && prediction.radarImage.isNotEmpty()) {
                    val imageBytes = Base64.decode(prediction.radarImage, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Radar Image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            contentScale = ContentScale.FillWidth
                        )
                    } else {
                        Text("Failed to decode radar image", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    Text("No radar image available from API", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        
        // Risk Analysis Card
        prediction?.riskAnalysis?.let { riskAnalysis ->
            RiskAnalysisCard(riskAnalysis)
        }
        
        // Forecast Data Card
        prediction?.forecastData?.let { forecastData ->
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
