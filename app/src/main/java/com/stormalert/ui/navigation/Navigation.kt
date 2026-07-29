package com.stormalert.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.stormalert.ui.weather.WeatherViewModel

data class BottomNavItem(
    val screen: Screen,
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

sealed class Screen(val route: String) {
    object Weather : Screen("weather")
    object Radar : Screen("radar")
    object Settings : Screen("settings")
}

@Composable
fun StormAlertNavigation(
    weatherViewModel: WeatherViewModel
) {
    val navController = rememberNavController()
    
    val bottomNavItems = listOf(
        BottomNavItem(Screen.Weather, "Weather", Icons.Default.Home),
        BottomNavItem(Screen.Radar, "Radar", Icons.Default.Map),
        BottomNavItem(Screen.Settings, "Settings", Icons.Default.Settings)
    )
    
    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                
                bottomNavItems.forEach { item ->
                    NavigationBarItem(
                        icon = { androidx.compose.material3.Icon(item.icon, contentDescription = item.title) },
                        label = { androidx.compose.material3.Text(item.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == item.screen.route } == true,
                        onClick = {
                            navController.navigate(item.screen.route) {
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
    ) { _ ->
        NavHost(
            navController = navController,
            startDestination = Screen.Weather.route,
            modifier = Modifier
        ) {
            composable(Screen.Weather.route) {
                WeatherScreen(
                    viewModel = weatherViewModel
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
