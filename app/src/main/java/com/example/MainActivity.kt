package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.core.i18n.WafaLocalization
import com.example.feature.agents.AgentAndMemoryScreen
import com.example.feature.apimanager.ApiManagerScreen
import com.example.feature.audio.AudioStudioScreen
import com.example.feature.chat.ChatScreen
import com.example.feature.coding.CodingStudioScreen
import com.example.feature.documents.DocumentStudioScreen
import com.example.feature.files.FileWorkspaceScreen
import com.example.feature.home.HomeScreen
import com.example.feature.image.ImageStudioScreen
import com.example.feature.jarvis.JarvisAssistantScreen
import com.example.feature.main.MainViewModel
import com.example.feature.navigation.WafaRoutes
import com.example.feature.presentations.PresentationStudioScreen
import com.example.feature.research.ResearchEngineScreen
import com.example.feature.settings.SettingsScreen
import com.example.feature.spreadsheets.SpreadsheetStudioScreen
import com.example.feature.studios.StudiosHubScreen
import com.example.feature.tools.PersonalToolsScreen
import com.example.feature.video.VideoStudioScreen
import com.example.feature.writing.WritingStudioScreen
import com.example.ui.theme.WafaAiTheme

private data class TopNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as WafaApplication
        setContent {
            val mainViewModel: MainViewModel = viewModel(
                factory = MainViewModel.Factory(app.container.repository)
            )
            val settings by mainViewModel.settings.collectAsStateWithLifecycle()
            val strings = remember(settings.language) { WafaLocalization.forLang(settings.language) }

            WafaAiTheme(themeMode = settings.themeMode) {
                WafaRootApp(
                    viewModel = mainViewModel,
                    voiceEngine = app.container.voiceEngine,
                    strings = strings
                )
            }
        }
    }
}

@Composable
fun WafaRootApp(
    viewModel: MainViewModel,
    voiceEngine: com.example.core.voice.VoiceEngine,
    strings: com.example.core.i18n.AppStrings
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: WafaRoutes.HOME

    val topDestinations = remember(strings) {
        listOf(
            TopNavItem(WafaRoutes.HOME, strings.navHome, Icons.Filled.Home, Icons.Outlined.Home),
            TopNavItem(WafaRoutes.CHAT, strings.navChat, Icons.Filled.Forum, Icons.Outlined.Forum),
            TopNavItem(WafaRoutes.STUDIOS, strings.navStudios, Icons.Filled.AutoAwesomeMosaic, Icons.Outlined.AutoAwesomeMosaic),
            TopNavItem(WafaRoutes.AGENTS, strings.navAgents, Icons.Filled.Hub, Icons.Outlined.Hub),
            TopNavItem(WafaRoutes.API_MANAGER, strings.navApiSettings, Icons.Filled.Key, Icons.Outlined.Key)
        )
    }

    val showPrimaryNav = currentRoute in topDestinations.map { it.route }

    fun navigateTop(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (showPrimaryNav && !useNavRail) {
                    NavigationBar {
                        topDestinations.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTop(item.route) },
                                icon = {
                                    Icon(
                                        if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label, maxLines = 1) },
                                modifier = Modifier.testTag("nav_${item.route}")
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
                if (showPrimaryNav && useNavRail) {
                    NavigationRail {
                        Spacer(Modifier.height(12.dp))
                        topDestinations.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTop(item.route) },
                                icon = {
                                    Icon(
                                        if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) },
                                modifier = Modifier.testTag("rail_${item.route}")
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = WafaRoutes.HOME
                    ) {
                        composable(WafaRoutes.HOME) {
                            HomeScreen(
                                viewModel = viewModel,
                                voiceEngine = voiceEngine,
                                strings = strings,
                                onNavigate = { route ->
                                    if (route in topDestinations.map { it.route }) navigateTop(route)
                                    else navController.navigate(route)
                                }
                            )
                        }
                        composable(WafaRoutes.CHAT) {
                            ChatScreen(
                                viewModel = viewModel,
                                voiceEngine = voiceEngine,
                                strings = strings,
                                onOpenApiManager = { navigateTop(WafaRoutes.API_MANAGER) }
                            )
                        }
                        composable(WafaRoutes.STUDIOS) {
                            StudiosHubScreen(
                                viewModel = viewModel,
                                strings = strings,
                                onNavigate = { navController.navigate(it) }
                            )
                        }
                        composable(WafaRoutes.AGENTS) {
                            AgentAndMemoryScreen(viewModel = viewModel, initialTab = 0)
                        }
                        composable(WafaRoutes.MEMORY) {
                            AgentAndMemoryScreen(
                                viewModel = viewModel,
                                initialTab = 1,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(WafaRoutes.API_MANAGER) {
                            ApiManagerScreen(viewModel = viewModel, strings = strings)
                        }
                        composable(WafaRoutes.CODING) {
                            CodingStudioScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                        }
                        composable(WafaRoutes.WRITING) {
                            WritingStudioScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                        }
                        composable(WafaRoutes.DOCUMENTS) {
                            DocumentStudioScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                        }
                        composable(WafaRoutes.SPREADSHEET) {
                            SpreadsheetStudioScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                        }
                        composable(WafaRoutes.PRESENTATIONS) {
                            PresentationStudioScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                        }
                        composable(WafaRoutes.IMAGE) {
                            ImageStudioScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                        }
                        composable(WafaRoutes.AUDIO) {
                            AudioStudioScreen(viewModel = viewModel, voiceEngine = voiceEngine, onBack = { navController.popBackStack() })
                        }
                        composable(WafaRoutes.VIDEO) {
                            VideoStudioScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                        }
                        composable(WafaRoutes.RESEARCH) {
                            ResearchEngineScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                        }
                        composable(WafaRoutes.FILES) {
                            FileWorkspaceScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                        }
                        composable(WafaRoutes.JARVIS) {
                            JarvisAssistantScreen(viewModel = viewModel, voiceEngine = voiceEngine, onBack = { navController.popBackStack() })
                        }
                        composable(WafaRoutes.TOOLS) {
                            PersonalToolsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                        }
                        composable(WafaRoutes.SETTINGS) {
                            SettingsScreen(viewModel = viewModel, strings = strings, onBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}
