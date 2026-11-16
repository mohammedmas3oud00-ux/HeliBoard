package com.example.heliboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.heliboard.theme.HeliBoardTheme
import com.example.heliboard.ui.HeliBoardViewModel
import com.example.heliboard.ui.screens.AboutScreen
import com.example.heliboard.ui.screens.AppearanceScreen
import com.example.heliboard.ui.screens.BackupScreen
import com.example.heliboard.ui.screens.LanguagesScreen
import com.example.heliboard.ui.screens.PersonalDictionaryScreen
import com.example.heliboard.ui.screens.PlaygroundScreen
import com.example.heliboard.ui.screens.PreferencesScreen
import com.example.heliboard.ui.screens.SettingsHubScreen
import com.example.heliboard.ui.screens.TextCorrectionScreen
import com.example.heliboard.ui.screens.ToolbarScreen
import kotlinx.serialization.Serializable

@Serializable object PlaygroundRoute
@Serializable object AppearanceRoute
@Serializable object LanguagesRoute
@Serializable object SettingsRoute
@Serializable object PreferencesRoute
@Serializable object ToolbarRoute
@Serializable object TextCorrectionRoute
@Serializable object PersonalDictionaryRoute
@Serializable object BackupRoute
@Serializable object AboutRoute

private data class TopTab(
    val route: Any,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HeliBoardTheme {
                HeliBoardRoot()
            }
        }
    }
}

@Composable
fun HeliBoardRoot(viewModel: HeliBoardViewModel = viewModel()) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination

    LaunchedEffect(navController) {
        viewModel.onNavigateToSettings = {
            navController.navigate(SettingsRoute) {
                launchSingleTop = true
            }
        }
    }

    val topTabs = listOf(
        TopTab(PlaygroundRoute, "Keyboard", Icons.Filled.Keyboard, Icons.Outlined.Keyboard),
        TopTab(AppearanceRoute, "Themes", Icons.Filled.Palette, Icons.Outlined.Palette),
        TopTab(LanguagesRoute, "Languages", Icons.Filled.Language, Icons.Outlined.Language),
        TopTab(SettingsRoute, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
    )

    val showTopLevelNav = currentDest?.let { dest ->
        dest.hasRoute<PlaygroundRoute>() ||
            dest.hasRoute<AppearanceRoute>() ||
            dest.hasRoute<LanguagesRoute>() ||
            dest.hasRoute<SettingsRoute>()
    } ?: true

    fun navigateTab(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (showTopLevelNav && !useRail) {
                    NavigationBar {
                        topTabs.forEach { tab ->
                            val selected = currentDest?.hasRoute(tab.route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("nav_${tab.label.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showTopLevelNav && useRail) {
                    NavigationRail {
                        topTabs.forEach { tab ->
                            val selected = currentDest?.hasRoute(tab.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) }
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = PlaygroundRoute,
                    modifier = Modifier.weight(1f)
                ) {
                    composable<PlaygroundRoute> {
                        PlaygroundScreen(
                            viewModel = viewModel,
                            onOpenThemes = { navigateTab(AppearanceRoute) },
                            onOpenLanguages = { navigateTab(LanguagesRoute) }
                        )
                    }
                    composable<AppearanceRoute> {
                        AppearanceScreen(viewModel = viewModel)
                    }
                    composable<LanguagesRoute> {
                        LanguagesScreen(viewModel = viewModel)
                    }
                    composable<SettingsRoute> {
                        SettingsHubScreen(
                            onOpenPreferences = { navController.navigate(PreferencesRoute) },
                            onOpenToolbar = { navController.navigate(ToolbarRoute) },
                            onOpenTextCorrection = { navController.navigate(TextCorrectionRoute) },
                            onOpenPersonalDictionary = { navController.navigate(PersonalDictionaryRoute) },
                            onOpenBackup = { navController.navigate(BackupRoute) },
                            onOpenAbout = { navController.navigate(AboutRoute) }
                        )
                    }
                    composable<PreferencesRoute> {
                        PreferencesScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                    composable<ToolbarRoute> {
                        ToolbarScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                    composable<TextCorrectionRoute> {
                        TextCorrectionScreen(
                            viewModel = viewModel,
                            onOpenPersonalDictionary = { navController.navigate(PersonalDictionaryRoute) },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable<PersonalDictionaryRoute> {
                        PersonalDictionaryScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                    composable<BackupRoute> {
                        BackupScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                    composable<AboutRoute> {
                        AboutScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
