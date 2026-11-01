package com.organizador.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.organizador.app.ui.calendar.CalendarScreen
import com.organizador.app.ui.checklists.ChecklistEditorScreen
import com.organizador.app.ui.checklists.ChecklistHistoryScreen
import com.organizador.app.ui.checklists.ChecklistListScreen
import com.organizador.app.ui.checklists.ChecklistRunScreen
import com.organizador.app.ui.checklists.OccurrenceScreen
import com.organizador.app.ui.dashboard.DashboardScreen
import com.organizador.app.ui.notes.NoteEditorScreen
import com.organizador.app.ui.notes.NotesScreen
import com.organizador.app.ui.projects.LabelsScreen
import com.organizador.app.ui.projects.ProjectScreen
import com.organizador.app.ui.projects.ProjectsScreen
import com.organizador.app.ui.search.SearchScreen
import com.organizador.app.ui.settings.SettingsScreen
import com.organizador.app.ui.settings.SettingsSectionScreen
import com.organizador.app.ui.statistics.StatisticsScreen
import com.organizador.app.ui.tasks.TaskEditorScreen
import com.organizador.app.ui.tasks.TaskListScreen
import java.time.LocalDate

private fun optionalString(name: String) = navArgument(name) {
    type = NavType.StringType
    nullable = true
    defaultValue = null
}

@Composable
fun AppNavHost(navController: NavHostController, start: Pair<String, String>, modifier: Modifier = Modifier) {
    NavHost(navController = navController, startDestination = start.first, modifier = modifier) {
        composable(Routes.DASHBOARD) { DashboardScreen() }

        composable(
            Routes.TASKS,
            arguments = listOf(navArgument("view") { type = NavType.StringType; defaultValue = start.second }, optionalString("id")),
        ) { entry ->
            val view = entry.arguments?.getString("view") ?: start.second
            val id = entry.arguments?.getString("id")
            TaskListScreen(viewKey = view, viewId = id)
        }

        composable(Routes.TASK, arguments = listOf(navArgument("id") { type = NavType.StringType }, optionalString("projectId"), optionalString("date"))) { entry ->
            TaskEditorScreen(
                taskId = entry.arguments?.getString("id")?.takeIf { it != "new" },
                presetProjectId = entry.arguments?.getString("projectId"),
                presetDate = entry.arguments?.getString("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            )
        }

        composable(Routes.PROJECTS, arguments = listOf(navArgument("create") { type = NavType.BoolType; defaultValue = false })) { entry ->
            ProjectsScreen(startCreating = entry.arguments?.getBoolean("create") == true)
        }
        composable(Routes.PROJECT) { entry -> ProjectScreen(projectId = entry.arguments?.getString("id").orEmpty()) }
        composable(Routes.LABELS) { LabelsScreen() }

        composable(Routes.CHECKLISTS) { ChecklistListScreen(routines = false) }
        composable(Routes.ROUTINES) { ChecklistListScreen(routines = true) }
        composable(Routes.CHECKLIST_RUN) { entry -> ChecklistRunScreen(templateId = entry.arguments?.getString("id").orEmpty()) }
        composable(
            Routes.CHECKLIST_EDIT,
            arguments = listOf(
                navArgument("id") { type = NavType.StringType },
                optionalString("kind"),
                navArgument("routine") { type = NavType.BoolType; defaultValue = false },
                optionalString("date"),
            ),
        ) { entry ->
            ChecklistEditorScreen(
                templateId = entry.arguments?.getString("id")?.takeIf { it != "new" },
                presetKind = entry.arguments?.getString("kind"),
                presetRoutine = entry.arguments?.getBoolean("routine") == true,
                presetDate = entry.arguments?.getString("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            )
        }
        composable(Routes.CHECKLIST_HISTORY) { entry -> ChecklistHistoryScreen(templateId = entry.arguments?.getString("id").orEmpty()) }
        composable(Routes.OCCURRENCE) { entry -> OccurrenceScreen(occurrenceId = entry.arguments?.getString("id").orEmpty()) }

        composable(Routes.NOTES, arguments = listOf(optionalString("folder"))) { entry -> NotesScreen(folderId = entry.arguments?.getString("folder")) }
        composable(Routes.NOTE, arguments = listOf(navArgument("id") { type = NavType.StringType }, optionalString("folder"))) { entry ->
            NoteEditorScreen(noteId = entry.arguments?.getString("id")?.takeIf { it != "new" }, folderId = entry.arguments?.getString("folder"))
        }

        composable(Routes.CALENDAR) { CalendarScreen() }
        composable(Routes.SEARCH) { SearchScreen() }
        composable(Routes.STATISTICS) { StatisticsScreen() }
        composable(Routes.SETTINGS) { SettingsScreen() }
        composable(Routes.SETTINGS_SECTION) { entry -> SettingsSectionScreen(section = entry.arguments?.getString("section").orEmpty()) }
    }
}
