package com.organizador.app.ui.navigation

import android.net.Uri
import com.organizador.domain.model.NavItem
import com.organizador.domain.model.Priority
import com.organizador.domain.model.StartDestination
import com.organizador.domain.model.TaskView

object Routes {
    const val DASHBOARD = "dashboard"
    const val TASKS = "tasks/{view}?id={id}"
    const val TASK = "task/{id}?projectId={projectId}&date={date}"
    const val PROJECTS = "projects?create={create}"
    const val PROJECT = "project/{id}"
    const val LABELS = "labels"
    const val CHECKLISTS = "checklists"
    const val ROUTINES = "routines"
    const val CHECKLIST_RUN = "checklist/run/{id}"
    const val CHECKLIST_EDIT = "checklist/edit/{id}?kind={kind}&routine={routine}&date={date}"
    const val CHECKLIST_HISTORY = "checklist/history/{id}"
    const val OCCURRENCE = "occurrence/{id}"
    const val NOTES = "notes?folder={folder}"
    const val NOTE = "note/{id}?folder={folder}"
    const val CALENDAR = "calendar"
    const val SEARCH = "search"
    const val STATISTICS = "statistics"
    const val SETTINGS = "settings"
    const val SETTINGS_SECTION = "settings/{section}"

    fun tasks(view: String, id: String? = null) = "tasks/$view" + (id?.let { "?id=${Uri.encode(it)}" } ?: "")
    fun task(id: String, projectId: String? = null, date: String? = null) =
        "task/$id" + listOfNotNull(projectId?.let { "projectId=$it" }, date?.let { "date=$it" }).joinToString("&").let { if (it.isEmpty()) "" else "?$it" }
    fun projects(create: Boolean = false) = if (create) "projects?create=true" else "projects"
    fun project(id: String) = "project/$id"
    fun checklistRun(id: String) = "checklist/run/$id"
    fun checklistEdit(id: String = "new", kind: String? = null, routine: Boolean = false, date: String? = null) =
        "checklist/edit/$id?routine=$routine" + (kind?.let { "&kind=$it" } ?: "") + (date?.let { "&date=$it" } ?: "")
    fun checklistHistory(id: String) = "checklist/history/$id"
    fun occurrence(id: String) = "occurrence/$id"
    fun notes(folder: String? = null) = "notes" + (folder?.let { "?folder=$it" } ?: "")
    fun note(id: String) = "note/$id"
    fun settings(section: String) = "settings/$section"

    fun taskView(view: String, id: String?): TaskView = when (view) {
        "inbox" -> TaskView.Inbox
        "today" -> TaskView.Today
        "upcoming" -> TaskView.Upcoming(7)
        "overdue" -> TaskView.Overdue
        "completed" -> TaskView.Completed
        "archived" -> TaskView.Archived
        "trash" -> TaskView.Trash
        "project" -> id?.let { TaskView.ByProject(it) } ?: TaskView.All
        "category" -> id?.let { TaskView.ByCategory(it) } ?: TaskView.All
        "tag" -> id?.let { TaskView.ByTag(it) } ?: TaskView.All
        "priority" -> TaskView.ByPriority(Priority.fromLevel(id?.toIntOrNull() ?: 4))
        else -> TaskView.All
    }

    fun forNav(item: NavItem): String = when (item) {
        NavItem.DASHBOARD -> DASHBOARD
        NavItem.TODAY -> tasks("today")
        NavItem.UPCOMING -> tasks("upcoming")
        NavItem.INBOX -> tasks("inbox")
        NavItem.CHECKLISTS -> CHECKLISTS
        NavItem.ROUTINES -> ROUTINES
        NavItem.NOTES -> notes()
        NavItem.PROJECTS -> projects()
        NavItem.CALENDAR -> CALENDAR
        NavItem.STATISTICS -> STATISTICS
        NavItem.SEARCH -> SEARCH
    }

    /**
     * Destino inicial como padrão de rota + valor padrão do argumento "view" (rotas de tarefas).
     * O NavHost usa o padrão como startDestination e o argumento padrão seleciona a visão.
     */
    fun forStart(start: StartDestination): Pair<String, String> = when (start) {
        StartDestination.DASHBOARD -> DASHBOARD to "today"
        StartDestination.TODAY -> TASKS to "today"
        StartDestination.INBOX -> TASKS to "inbox"
        StartDestination.UPCOMING -> TASKS to "upcoming"
        StartDestination.CHECKLISTS -> CHECKLISTS to "today"
        StartDestination.ROUTINES -> ROUTINES to "today"
        StartDestination.NOTES -> NOTES to "today"
        StartDestination.CALENDAR -> CALENDAR to "today"
    }
}
