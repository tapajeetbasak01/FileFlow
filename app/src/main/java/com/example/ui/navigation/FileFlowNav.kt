package com.example.ui.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.R
import com.example.data.FileFlowContainer
import com.example.model.ToolId
import com.example.ui.components.FileFlowLogoMark
import com.example.ui.screens.ConverterWorkspaceScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ToolsScreen
import com.example.ui.viewmodel.ConverterViewModel
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.UiMessageEvent
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
object HomeRoute

@Serializable
data class ToolsRoute(val categoryId: String? = null)

@Serializable
object HistoryRoute

@Serializable
data class WorkspaceRoute(val toolId: String)

private data class TopLevelDestination(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun FileFlowApp(
    container: FileFlowContainer,
    mainViewModel: MainViewModel
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val converterViewModel: ConverterViewModel = viewModel(
        factory = ConverterViewModel.Factory(container)
    )

    val themeMode by mainViewModel.themeMode.collectAsStateWithLifecycle()
    val recentHistory by mainViewModel.recentHistory.collectAsStateWithLifecycle()
    val allHistory by mainViewModel.allHistory.collectAsStateWithLifecycle()
    val quickPickState by mainViewModel.quickPickState.collectAsStateWithLifecycle()

    var pendingSafSource by remember { mutableStateOf<Pair<File, String>?>(null) }
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { targetUri ->
        val pending = pendingSafSource
        pendingSafSource = null
        if (targetUri != null && pending != null) {
            mainViewModel.completeSafSave(pending.first, targetUri)
        }
    }

    LaunchedEffect(mainViewModel, converterViewModel) {
        mainViewModel.events.collect { event ->
            when (event) {
                is UiMessageEvent.Toast -> snackbarHostState.showSnackbar(event.message)
                is UiMessageEvent.RequestSafSave -> {
                    pendingSafSource = event.sourceFile to event.mimeType
                    createDocumentLauncher.launch(event.sourceFile.name)
                }
            }
        }
    }

    LaunchedEffect(converterViewModel) {
        converterViewModel.events.collect { event ->
            when (event) {
                is UiMessageEvent.Toast -> snackbarHostState.showSnackbar(event.message)
                is UiMessageEvent.RequestSafSave -> {
                    pendingSafSource = event.sourceFile to event.mimeType
                    createDocumentLauncher.launch(event.sourceFile.name)
                }
            }
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = navBackStackEntry?.destination
    val isWorkspace = currentDest?.hasRoute<WorkspaceRoute>() == true

    val isHomeSelected = currentDest?.hasRoute<HomeRoute>() == true
    val isToolsSelected = currentDest?.hasRoute<ToolsRoute>() == true
    val isHistorySelected = currentDest?.hasRoute<HistoryRoute>() == true

    fun navigateTopLevel(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val topDestinations = listOf(
        Triple(
            HomeRoute,
            TopLevelDestination(R.string.nav_home, Icons.Rounded.Home, Icons.Outlined.Home, "nav_home"),
            isHomeSelected
        ),
        Triple(
            ToolsRoute(null),
            TopLevelDestination(R.string.nav_tools, Icons.Rounded.Build, Icons.Outlined.Build, "nav_tools"),
            isToolsSelected
        ),
        Triple(
            HistoryRoute,
            TopLevelDestination(R.string.nav_history, Icons.Rounded.History, Icons.Outlined.History, "nav_history"),
            isHistorySelected
        )
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavigationRail && !isWorkspace) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        topDestinations.forEach { (route, dest, selected) ->
                            val label = stringResource(dest.labelRes)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag(dest.testTag)
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
                if (useNavigationRail && !isWorkspace) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        header = {
                            FileFlowLogoMark(modifier = Modifier.padding(vertical = 12.dp))
                        },
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        topDestinations.forEach { (route, dest, selected) ->
                            val label = stringResource(dest.labelRes)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = HomeRoute,
                    enterTransition = { fadeIn(animationSpec = tween(220)) },
                    exitTransition = { fadeOut(animationSpec = tween(180)) },
                    modifier = Modifier.weight(1f)
                ) {
                    composable<HomeRoute> {
                        HomeScreen(
                            themeMode = themeMode,
                            recentHistory = recentHistory,
                            quickPickState = quickPickState,
                            onToggleTheme = mainViewModel::cycleThemeMode,
                            onFilesPicked = mainViewModel::onHomeFilesPicked,
                            onDismissQuickPick = mainViewModel::dismissQuickPick,
                            onSelectSuggestedTool = { tool ->
                                val staged = quickPickState?.files.orEmpty()
                                mainViewModel.stageFilesForTool(staged)
                                navController.navigate(WorkspaceRoute(tool.toolId.id))
                            },
                            onCategoryClick = { category ->
                                navController.navigate(ToolsRoute(categoryId = category.id))
                            },
                            onToolClick = { tool ->
                                navController.navigate(WorkspaceRoute(tool.toolId.id))
                            },
                            onSeeAllHistory = { navigateTopLevel(HistoryRoute) },
                            onSeeAllTools = { navigateTopLevel(ToolsRoute(null)) },
                            onSaveRecord = mainViewModel::saveRecordToDownloads,
                            onShareRecord = mainViewModel::shareRecord,
                            onOpenRecord = mainViewModel::openRecord
                        )
                    }

                    composable<ToolsRoute> { backStackEntry ->
                        val args = backStackEntry.toRoute<ToolsRoute>()
                        ToolsScreen(
                            initialCategoryId = args.categoryId,
                            onToolClick = { tool ->
                                navController.navigate(WorkspaceRoute(tool.toolId.id))
                            }
                        )
                    }

                    composable<HistoryRoute> {
                        HistoryScreen(
                            records = allHistory,
                            onSaveRecord = mainViewModel::saveRecordToDownloads,
                            onShareRecord = mainViewModel::shareRecord,
                            onOpenRecord = mainViewModel::openRecord,
                            onDeleteRecord = mainViewModel::deleteHistoryRecord,
                            onClearAll = mainViewModel::clearAllHistory
                        )
                    }

                    composable<WorkspaceRoute> { backStackEntry ->
                        val args = backStackEntry.toRoute<WorkspaceRoute>()
                        val resolvedToolId = ToolId.fromId(args.toolId) ?: ToolId.PNG_TO_JPG

                        LaunchedEffect(resolvedToolId) {
                            val preloaded = mainViewModel.consumePreloadedFiles()
                            converterViewModel.initForTool(resolvedToolId, preloaded)
                        }

                        val tool by converterViewModel.tool.collectAsStateWithLifecycle()
                        val selectedFiles by converterViewModel.selectedFiles.collectAsStateWithLifecycle()
                        val options by converterViewModel.options.collectAsStateWithLifecycle()
                        val conversionState by converterViewModel.conversionState.collectAsStateWithLifecycle()

                        ConverterWorkspaceScreen(
                            tool = tool,
                            selectedFiles = selectedFiles,
                            options = options,
                            conversionState = conversionState,
                            onBack = { navController.popBackStack() },
                            onAddUris = converterViewModel::addUris,
                            onRemoveFileAt = converterViewModel::removeFileAt,
                            onMoveFile = converterViewModel::moveFile,
                            onClearFiles = converterViewModel::clearFiles,
                            onUpdateOptions = converterViewModel::updateOptions,
                            onStartConversion = converterViewModel::startConversion,
                            onCancelConversion = converterViewModel::cancelConversion,
                            onResetToIdle = converterViewModel::resetToIdle,
                            onSaveOutput = converterViewModel::saveOutputToDownloads,
                            onSaveAllOutputs = converterViewModel::saveAllOutputsToDownloads,
                            onBundleZip = converterViewModel::bundleAndSaveZip,
                            onShareOutput = converterViewModel::shareOutput,
                            onShareAllOutputs = converterViewModel::shareAllOutputs,
                            onOpenOutput = converterViewModel::openOutput
                        )
                    }
                }
            }
        }
    }
}
