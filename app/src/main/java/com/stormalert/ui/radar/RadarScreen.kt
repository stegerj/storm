package com.stormalert.ui.radar

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import com.stormalert.ui.radar.RadarUiState

@Composable
fun RadarScreen(
    viewModel: RadarViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.loadLatestRadar()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Weather Radar") }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
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
                        contentScale = ContentScale.FitWidth
                    )
                }
            }
        }
    }
}
