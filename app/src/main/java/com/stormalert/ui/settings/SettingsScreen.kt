package com.stormalert.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.stormalert.ui.settings.SettingsViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Alert Settings Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Alert Settings",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable Storm Alerts")
                        Switch(
                            checked = uiState.enableAlerts,
                            onCheckedChange = { viewModel.toggleAlerts(it) }
                        )
                    }
                    
                    Text(
                        "Alert Threshold",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = uiState.alertThreshold.toFloat(),
                        onValueChange = { viewModel.updateAlertThreshold(it.toInt()) },
                        valueRange = 0f..100f,
                        steps = 10
                    )
                    Text("${uiState.alertThreshold}%")
                }
            }
            
            // Check Interval Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Check Interval",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Text(
                        "Weather check frequency",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    val intervals = listOf(5, 10, 15, 30, 60)
                    var expanded by remember { mutableStateOf(false) }
                    
                    Box {
                        Button(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${uiState.checkInterval} minutes")
                        }
                        
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            intervals.forEach { interval ->
                                DropdownMenuItem(
                                    text = { Text("$interval minutes") },
                                    onClick = {
                                        viewModel.updateCheckInterval(interval)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
            
            // Service Control Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Background Service",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Button(
                        onClick = { viewModel.startBackgroundService() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Start Service")
                    }
                    
                    Button(
                        onClick = { viewModel.stopBackgroundService() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Stop Service")
                    }
                }
            }
            
            // Info Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "About",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Storm Alert Service")
                    Text("Version 1.0")
                    Text("Data provided by Open-Meteo and MET Norway")
                }
            }
        }
    }
}