package com.example.heliboard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
import com.example.heliboard.ui.screens.AiSettingsScreen
import com.example.heliboard.ui.screens.AppearanceScreen
import com.example.heliboard.ui.screens.BackupScreen
import com.example.heliboard.ui.screens.LanguagesScreen
import com.example.heliboard.ui.screens.PersonalDictionaryScreen
import com.example.heliboard.ui.screens.PlaygroundScreen
import com.example.heliboard.ui.screens.PreferencesScreen
import com.example.heliboard.ui.screens.SettingsHubScreen
import com.example.heliboard.ui.screens.TextCorrectionScreen
import com.example.heliboard.ui.screens.ToolbarScreen
import com.example.heliboard.util.LocaleHelper
import kotlinx.serialization.Serializable

@Serializable object PlaygroundRoute
@Serializable object AppearanceRoute
@Serializable object LanguagesRoute
@Serializable object SettingsRoute
@Serializable object PreferencesRoute
@Serializable object AiSettingsRoute
@Serializable object ToolbarRoute
@Serializable object TextCorrectionRoute
@Serializable object PersonalDictionaryRoute
@Serializable object BackupRoute
@Serializable object AboutRoute

private data class TopTab(
    val route: Any,
    val tag: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

class MainActivity : ComponentActivity() {

    private var onPermissionCallback: ((Boolean) -> Unit)? = null
    private var launchedForImePermission: Boolean = false

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val fromIme = launchedForImePermission
        launchedForImePermission = false
        (application as? HeliBoardApp)?.notifyVoicePermissionResult(granted, fromImeRequest = fromIme)
        onPermissionCallback?.invoke(granted)
        if (fromIme) {
            finish()
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val savedLang = LocaleHelper.readSavedAppLanguage(newBase)
        val (localizedCtx, _) = LocaleHelper.createLocalizedContext(newBase, savedLang)
        super.attachBaseContext(localizedCtx)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: HeliBoardViewModel = viewModel()
            val settings by vm.settings.collectAsState()
            val baseContext = LocalContext.current

            val (localizedCtx, localizedConfig) = remember(settings.appLanguage, baseContext) {
                LocaleHelper.createLocalizedContext(baseContext, settings.appLanguage)
            }
            val layoutDir = remember(settings.appLanguage) {
                LocaleHelper.resolveLayoutDirection(settings.appLanguage)
            }

            LaunchedEffect(vm) {
                onPermissionCallback = { granted ->
                    vm.sandboxController.onVoicePermissionResult(granted)
                }
                if (intent?.getBooleanExtra(EXTRA_REQUEST_AUDIO_PERMISSION, false) == true) {
                    intent.removeExtra(EXTRA_REQUEST_AUDIO_PERMISSION)
                    launchedForImePermission = true
                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }

            val registryOwner = LocalActivityResultRegistryOwner.current ?: this@MainActivity
            val backDispatcherOwner = LocalOnBackPressedDispatcherOwner.current ?: this@MainActivity

            CompositionLocalProvider(
                LocalContext provides localizedCtx,
                LocalConfiguration provides localizedConfig,
                LocalLayoutDirection provides layoutDir,
                LocalActivityResultRegistryOwner provides registryOwner,
                LocalOnBackPressedDispatcherOwner provides backDispatcherOwner
            ) {
                HeliBoardTheme {
                    HeliBoardRoot(
                        viewModel = vm,
                        initialOpenAiSettings = intent?.getBooleanExtra(EXTRA_OPEN_AI_SETTINGS, false) == true
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_REQUEST_AUDIO_PERMISSION, false)) {
            intent.removeExtra(EXTRA_REQUEST_AUDIO_PERMISSION)
            launchedForImePermission = true
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    companion object {
        const val EXTRA_REQUEST_AUDIO_PERMISSION = "com.example.heliboard.REQUEST_AUDIO_PERMISSION"
        const val EXTRA_OPEN_AI_SETTINGS = "com.example.heliboard.OPEN_AI_SETTINGS"
    }
}

@Composable
fun HeliBoardRoot(
    viewModel: HeliBoardViewModel = viewModel(),
    initialOpenAiSettings: Boolean = false
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination

    LaunchedEffect(navController, initialOpenAiSettings) {
        viewModel.onNavigateToSettings = {
            navController.navigate(SettingsRoute) {
                launchSingleTop = true
            }
        }
        viewModel.onNavigateToAiSettings = {
            navController.navigate(AiSettingsRoute) {
                launchSingleTop = true
            }
        }
        if (initialOpenAiSettings) {
            navController.navigate(AiSettingsRoute) {
                launchSingleTop = true
            }
        }
    }

    val topTabs = listOf(
        TopTab(PlaygroundRoute, "keyboard", R.string.nav_keyboard, Icons.Filled.Keyboard, Icons.Outlined.Keyboard),
        TopTab(AppearanceRoute, "themes", R.string.nav_themes, Icons.Filled.Palette, Icons.Outlined.Palette),
        TopTab(LanguagesRoute, "languages", R.string.nav_languages, Icons.Filled.Language, Icons.Outlined.Language),
        TopTab(SettingsRoute, "settings", R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings)
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
                            val label = stringResource(tab.labelRes)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag("nav_${tab.tag}")
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
                            val label = stringResource(tab.labelRes)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag("nav_${tab.tag}")
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
                            viewModel = viewModel,
                            onOpenPreferences = { navController.navigate(PreferencesRoute) },
                            onOpenAiSettings = { navController.navigate(AiSettingsRoute) },
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
                    composable<AiSettingsRoute> {
                        AiSettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
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
