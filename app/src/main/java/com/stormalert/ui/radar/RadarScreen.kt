package com.stormalert.ui.radar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import com.stormalert.data.analysis.StormRiskAnalyzer
import com.stormalert.ui.radar.RadarUiState
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarScreen(
    viewModel: RadarViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val forecastData by viewModel.forecastData.collectAsState()
    val riskAnalysis by viewModel.riskAnalysis.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.loadRadarWithForecast(44.5, 11.34) // Default coordinates
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Weather Radar") },
                actions = {
                    IconButton(onClick = { viewModel.loadRadarWithForecast(44.5, 11.34) }) {
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
            // Risk Analysis Card
            riskAnalysis?.let { analysis ->
                RiskAnalysisCard(analysis)
            }
            
            // Radar Display
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (uiState) {
                    is RadarUiState.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    
                    is RadarUiState.Error -> {
                        Text(
                            "Error loading radar: ${(uiState as RadarUiState.Error).message}",
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    
                    is RadarUiState.Success -> {
                        val imageUrl = (uiState as RadarUiState.Success).imageUrl
                        Image(
                            painter = rememberAsyncImagePainter(imageUrl),
                            contentDescription = "Weather Radar",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                    
                    is RadarUiState.SuccessWithTiles -> {
                        val tiles = (uiState as RadarUiState.SuccessWithTiles).tiles
                        RadarCompositeView(
                            tiles = tiles,
                            forecastData = forecastData,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RiskAnalysisCard(analysis: com.stormalert.data.analysis.StormRiskAnalyzer.MultiRadiusAnalysis) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (analysis.current.riskLevel) {
                com.stormalert.data.analysis.StormRiskAnalyzer.RiskLevel.CRITICAL -> 
                    MaterialTheme.colorScheme.errorContainer
                com.stormalert.data.analysis.StormRiskAnalyzer.RiskLevel.HIGH -> 
                    MaterialTheme.colorScheme.errorContainer
                com.stormalert.data.analysis.StormRiskAnalyzer.RiskLevel.MEDIUM -> 
                    MaterialTheme.colorScheme.secondaryContainer
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
            
            // Current location risk
            RiskLevelRow(
                label = "Current Location",
                analysis = analysis.current
            )
            
            // 20km radius risk
            RiskLevelRow(
                label = "20km Radius",
                analysis = analysis.radius20km
            )
            
            // 100km radius risk
            RiskLevelRow(
                label = "100km Radius",
                analysis = analysis.radius100km
            )
        }
    }
}

@Composable
fun RiskLevelRow(
    label: String,
    analysis: com.stormalert.data.analysis.StormRiskAnalyzer.RiskAnalysis
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        Text(
            when (analysis.riskLevel) {
                com.stormalert.data.analysis.StormRiskAnalyzer.RiskLevel.CRITICAL -> "CRITICAL"
                com.stormalert.data.analysis.StormRiskAnalyzer.RiskLevel.HIGH -> "HIGH"
                com.stormalert.data.analysis.StormRiskAnalyzer.RiskLevel.MEDIUM -> "MEDIUM"
                com.stormalert.data.analysis.StormRiskAnalyzer.RiskLevel.LOW -> "LOW"
            },
            color = when (analysis.riskLevel) {
                com.stormalert.data.analysis.StormRiskAnalyzer.RiskLevel.CRITICAL -> MaterialTheme.colorScheme.error
                com.stormalert.data.analysis.StormRiskAnalyzer.RiskLevel.HIGH -> MaterialTheme.colorScheme.error
                com.stormalert.data.analysis.StormRiskAnalyzer.RiskLevel.MEDIUM -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
fun RadarCompositeView(
    tiles: List<com.stormalert.data.repository.TileUrl>,
    forecastData: com.stormalert.data.analysis.StormMovementAnalyzer.ForecastData?,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        // Load and display radar tiles
        tiles.forEach { tile ->
            Image(
                painter = rememberAsyncImagePainter(tile.url),
                contentDescription = "Radar Tile",
                modifier = Modifier
                    .offset(
                        x = (tile.dx * 256).dp,
                        y = (tile.dy * 256).dp
                    )
                    .size(256.dp),
                contentScale = ContentScale.Fit
            )
        }
        
        // Draw forecast markers if available
        forecastData?.let { forecast ->
            ForecastOverlay(
                forecastData = forecast,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun ForecastOverlay(
    forecastData: com.stormalert.data.analysis.StormMovementAnalyzer.ForecastData,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val centroids = forecastData.stormCentroids
        if (centroids.isEmpty()) return@Canvas
        
        val latestCentroid = centroids.last()
        val stormX = latestCentroid.x.toFloat()
        val stormY = latestCentroid.y.toFloat()
        
        val forecast1h = forecastData.forecast1h
        val forecast1hX = stormX + forecast1h.first.toFloat()
        val forecast1hY = stormY + forecast1h.second.toFloat()
        
        val forecast5h = forecastData.forecast5h
        val forecast5hX = stormX + forecast5h.first.toFloat()
        val forecast5hY = stormY + forecast5h.second.toFloat()
        
        // Draw storm current position (blue circle)
        drawCircle(
            color = Color.Blue,
            radius = 10f,
            center = Offset(stormX, stormY),
            style = Stroke(width = 2f)
        )
        
        // Draw 1h forecast position (green circle)
        drawCircle(
            color = Color.Green,
            radius = 8f,
            center = Offset(forecast1hX, forecast1hY),
            style = Stroke(width = 2f)
        )
        
        // Draw 5h forecast position (purple circle)
        drawCircle(
            color = Color(0xFF800080), // Purple
            radius = 6f,
            center = Offset(forecast5hX, forecast5hY),
            style = Stroke(width = 2f)
        )
        
        // Draw arrow from storm to 1h forecast
        drawLine(
            color = Color(0xFFFFA500), // Orange
            start = Offset(stormX, stormY),
            end = Offset(forecast1hX, forecast1hY),
            strokeWidth = 3f
        )
        
        // Draw line from 1h to 5h forecast
        drawLine(
            color = Color(0xFF800080), // Purple
            start = Offset(forecast1hX, forecast1hY),
            end = Offset(forecast5hX, forecast5hY),
            strokeWidth = 2f
        )
    }
}
