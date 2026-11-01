package com.organizador.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.organizador.app.ui.common.ColorDot
import com.organizador.app.ui.navigation.AppNavHost
import com.organizador.app.ui.navigation.Routes
import com.organizador.app.ui.quick.QuickAddTaskSheet
import com.organizador.app.ui.quick.QuickCreateSheet
import com.organizador.domain.model.AppSettings
import com.organizador.domain.model.NavItem
import com.organizador.domain.model.Project
import com.organizador.domain.repository.ProjectRepository
import com.organizador.domain.repository.TaskCounts
import com.organizador.domain.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Ações globais acessíveis por qualquer tela (abrir menu, navegar, criar rápido, mostrar mensagens). */
class AppActions(
    val navController: NavHostController,
    val openDrawer: () -> Unit,
    val showMessage: (String, String?, (() -> Unit)?) -> Unit,
    val quickAddTask: (QuickAddPrefill) -> Unit,
) {
    // Sem launchSingleTop: destinos com argumentos diferentes (ex.: duas tarefas) não podem reaproveitar a entrada.
    fun navigate(route: String) = navController.navigate(route)
    fun back() = navController.popBackStack()
}

data class QuickAddPrefill(val projectId: String? = null, val date: java.time.LocalDate? = null)

val LocalAppActions = staticCompositionLocalOf<AppActions> { error("AppActions não fornecido") }

@HiltViewModel
class MainViewModel @Inject constructor(tasks: TaskRepository, projects: ProjectRepository) : ViewModel() {
    val counts: StateFlow<TaskCounts?> = tasks.observeCounts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val projects: StateFlow<List<Project>> = projects.observeProjects().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

private data class NavSpec(val label: String, val icon: ImageVector)

private fun spec(item: NavItem): NavSpec = when (item) {
    NavItem.DASHBOARD -> NavSpec("Painel", Icons.Outlined.Dashboard)
    NavItem.TODAY -> NavSpec("Hoje", Icons.Outlined.Today)
    NavItem.UPCOMING -> NavSpec("Próximos", Icons.Outlined.DateRange)
    NavItem.INBOX -> NavSpec("Entrada", Icons.Outlined.Inbox)
    NavItem.CHECKLISTS -> NavSpec("Checklists", Icons.Outlined.Checklist)
    NavItem.ROUTINES -> NavSpec("Rotinas", Icons.Outlined.Repeat)
    NavItem.NOTES -> NavSpec("Notas", Icons.AutoMirrored.Outlined.EventNote)
    NavItem.PROJECTS -> NavSpec("Projetos", Icons.Outlined.Folder)
    NavItem.CALENDAR -> NavSpec("Calendário", Icons.Outlined.CalendarMonth)
    NavItem.STATISTICS -> NavSpec("Estatísticas", Icons.Outlined.BarChart)
    NavItem.SEARCH -> NavSpec("Pesquisar", Icons.Outlined.Search)
}

private val topLevelPrefixes = listOf("dashboard", "tasks/", "checklists", "routines", "notes", "projects", "calendar", "statistics", "search")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(settings: AppSettings, pendingRoute: MutableStateFlow<String?>) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val vm: MainViewModel = hiltViewModel()
    val counts by vm.counts.collectAsStateWithLifecycle()
    val projects by vm.projects.collectAsStateWithLifecycle()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route.orEmpty()
    val currentPath = backStack?.let { entry ->
        // Reconstrói o caminho concreto para destacar o item ativo (ex.: tasks/today).
        val view = entry.arguments?.getString("view")
        if (route.startsWith("tasks/") && view != null) "tasks/$view" else route.substringBefore("?")
    }.orEmpty()
    var quickCreate by remember { mutableStateOf(false) }
    var quickAdd by remember { mutableStateOf<QuickAddPrefill?>(null) }

    val actions = remember(navController) {
        AppActions(
            navController = navController,
            openDrawer = { scope.launch { drawerState.open() } },
            showMessage = { message, actionLabel, action ->
                scope.launch {
                    val result = snackbar.showSnackbar(message, actionLabel, withDismissAction = actionLabel == null)
                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) action?.invoke()
                }
            },
            quickAddTask = { quickAdd = it },
        )
    }

    val pending by pendingRoute.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        pending?.let {
            runCatching { navController.navigate(it) { launchSingleTop = true } }
            pendingRoute.value = null
        }
    }

    val isTopLevel = topLevelPrefixes.any { route.startsWith(it) }
    val bottomItems = settings.navOrder.filter { it != NavItem.SEARCH }.take(4)

    fun go(item: NavItem) {
        val target = Routes.forNav(item)
        navController.navigate(target) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    CompositionLocalProvider(LocalAppActions provides actions) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = isTopLevel,
            drawerContent = {
                ModalDrawerSheet {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                        Text("Organizador", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
                        settings.navOrder.forEach { item ->
                            val s = spec(item)
                            val badge = when (item) {
                                NavItem.INBOX -> counts?.inbox
                                NavItem.TODAY -> counts?.today
                                NavItem.UPCOMING -> counts?.upcoming
                                else -> null
                            }
                            NavigationDrawerItem(
                                label = { Text(s.label) },
                                icon = { Icon(s.icon, null) },
                                badge = { if (badge != null && badge > 0) Text("$badge") },
                                selected = currentPath == Routes.forNav(item).substringBefore("?"),
                                onClick = { scope.launch { drawerState.close() }; go(item) },
                            )
                        }
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Text("Projetos", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp))
                        projects.forEach { p ->
                            NavigationDrawerItem(
                                label = { Text(p.name, maxLines = 1) },
                                icon = { ColorDot(p.color, size = 12) },
                                selected = route.startsWith("project/") && backStack?.arguments?.getString("id") == p.id,
                                onClick = { scope.launch { drawerState.close() }; actions.navigate(Routes.project(p.id)) },
                            )
                        }
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        NavigationDrawerItem(
                            label = { Text("Etiquetas e categorias") }, icon = { Icon(Icons.AutoMirrored.Outlined.Label, null) },
                            selected = route == Routes.LABELS, onClick = { scope.launch { drawerState.close() }; actions.navigate(Routes.LABELS) },
                        )
                        NavigationDrawerItem(
                            label = { Text("Configurações") }, icon = { Icon(Icons.Outlined.Settings, null) },
                            selected = route.startsWith("settings"), onClick = { scope.launch { drawerState.close() }; actions.navigate(Routes.SETTINGS) },
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                }
            },
        ) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbar) },
                floatingActionButton = {
                    if (isTopLevel && !route.startsWith("search") && !route.startsWith("statistics")) {
                        FloatingActionButton(
                            onClick = {
                                when {
                                    route.startsWith("tasks/") -> {
                                        val view = backStack?.arguments?.getString("view")
                                        val id = backStack?.arguments?.getString("id")
                                        quickAdd = QuickAddPrefill(
                                            projectId = id.takeIf { view == "project" },
                                            date = java.time.LocalDate.now().takeIf { view == "today" },
                                        )
                                    }
                                    route.startsWith("notes") -> actions.navigate(Routes.note("new") + (backStack?.arguments?.getString("folder")?.let { "?folder=$it" } ?: ""))
                                    route.startsWith("checklists") -> actions.navigate(Routes.checklistEdit())
                                    route.startsWith("routines") -> actions.navigate(Routes.checklistEdit(kind = "RECURRING", routine = true))
                                    else -> quickCreate = true
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.primary,
                        ) { Icon(Icons.Default.Add, contentDescription = "Criar") }
                    }
                },
                bottomBar = {
                    if (isTopLevel) {
                        NavigationBar {
                            bottomItems.forEach { item ->
                                val s = spec(item)
                                NavigationBarItem(
                                    selected = currentPath == Routes.forNav(item).substringBefore("?"),
                                    onClick = { go(item) },
                                    icon = { Icon(s.icon, null) },
                                    label = { Text(s.label, maxLines = 1) },
                                )
                            }
                            NavigationBarItem(
                                selected = false,
                                onClick = { scope.launch { drawerState.open() } },
                                icon = { Icon(Icons.Outlined.Menu, null) },
                                label = { Text("Menu") },
                            )
                        }
                    }
                },
            ) { padding ->
                AppNavHost(navController, Routes.forStart(settings.startDestination), Modifier.padding(bottom = padding.calculateBottomPadding()))
            }
        }

        if (quickCreate) {
            QuickCreateSheet(
                onDismiss = { quickCreate = false },
                onTask = { quickCreate = false; quickAdd = QuickAddPrefill() },
                onNote = { quickCreate = false; actions.navigate(Routes.note("new")) },
                onChecklist = { quickCreate = false; actions.navigate(Routes.checklistEdit()) },
                onRoutine = { quickCreate = false; actions.navigate(Routes.checklistEdit(kind = "RECURRING", routine = true)) },
                onProject = { quickCreate = false; actions.navigate(Routes.projects(create = true)) },
            )
        }
        quickAdd?.let { prefill ->
            QuickAddTaskSheet(prefill = prefill, onDismiss = { quickAdd = null }, onOpenFull = { id -> quickAdd = null; actions.navigate(Routes.task(id)) })
        }
    }
}

