package com.stormalert.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.stormalert.ui.radar.RadarScreen
import com.stormalert.ui.settings.SettingsScreen
import com.stormalert.ui.weather.WeatherScreen
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.stormalert.ui.weather.WeatherViewModel

sealed class Screen(val route: String) {
    object Weather : Screen("weather")
    object Radar : Screen("radar")
    object Settings : Screen("settings")
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun StormAlertNavigation(
    weatherViewModel: WeatherViewModel
) {
    val navController = rememberNavController()
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )
    
    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                
                listOf(
                    Screen.Weather to "Weather" to Icons.Default.Home,
                    Screen.Radar to "Radar" to Icons.Default.Map,
                    Screen.Settings to "Settings" to Icons.Default.Settings
                ).forEach { (screen, title, icon) ->
                    NavigationBarItem(
                        icon = { Icon(icon, contentDescription = title) },
                        label = { Text(title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Weather.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.Weather.route) {
                WeatherScreen(
                    viewModel = weatherViewModel,
                    permissionsState = permissionsState
                )
            }
            composable(Screen.Radar.route) {
                RadarScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
        }
    }
}
