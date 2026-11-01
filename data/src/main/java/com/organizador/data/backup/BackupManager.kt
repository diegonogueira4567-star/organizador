package com.organizador.data.backup

import android.content.Context
import androidx.room.withTransaction
import com.organizador.data.local.OrganizadorDatabase
import com.organizador.data.prefs.SettingsRepositoryImpl
import com.organizador.data.storage.AttachmentStorage
import com.organizador.domain.model.ActivityType
import com.organizador.domain.model.EntityType
import com.organizador.domain.model.TimeProvider
import com.organizador.domain.repository.ActivityRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: OrganizadorDatabase,
    private val storage: AttachmentStorage,
    private val settings: SettingsRepositoryImpl,
    private val activity: ActivityRepository,
    private val time: TimeProvider,
) {
    private val dao = db.backupDao()
    val preventiveDir: File get() = File(context.filesDir, "backups").apply { mkdirs() }

    suspend fun export(out: OutputStream, sections: Set<BackupSection>, password: CharArray?): BackupManifest = withContext(Dispatchers.IO) {
        val data = db.withTransaction { collect(sections) } // instantâneo consistente
        val entries = ArrayList<BackupArchive.Entry>()
        entries += BackupArchive.Entry(BackupArchive.DATA) {
            BackupArchive.json.encodeToString(BackupData.serializer(), data).toByteArray()
        }
        if (BackupSection.SETTINGS in sections) {
            val raw = settings.exportRaw()
            entries += BackupArchive.Entry(BackupArchive.SETTINGS) {
                BackupArchive.json.encodeToString(MapSerializer(String.serializer(), String.serializer()), raw).toByteArray()
            }
        }
        if (BackupSection.ATTACHMENTS in sections) {
            (data.taskAttachments.map { it.relativePath } + data.noteAttachments.map { it.relativePath }).distinct().forEach { path ->
                val file = runCatching { storage.resolve(path) }.getOrNull()
                if (file != null && file.exists() && BackupArchive.isAllowedName(BackupArchive.ATTACHMENTS_DIR + path)) {
                    entries += BackupArchive.Entry(BackupArchive.ATTACHMENTS_DIR + path) { file.readBytes() }
                }
            }
        }
        val header = BackupManifest(
            appVersion = appVersion(),
            schemaVersion = OrganizadorDatabase.VERSION,
            createdAt = time.now().toEpochMilli(),
            sections = sections.sortedBy { it.ordinal },
            counts = data.counts(),
        )
        val manifest = BackupArchive.write(out, header, entries, password)
        activity.log(ActivityType.BACKUP_EXPORTED, EntityType.SYSTEM, null, "Backup exportado", null, time.now())
        manifest
    }

    /** Valida o pacote inteiro (integridade, nomes, tamanhos, senha) e retorna o que seria importado. */
    suspend fun inspect(open: () -> InputStream, password: CharArray?): BackupPreview = withContext(Dispatchers.IO) {
        val result = open().use { BackupArchive.read(it, password, staging = null) }
        BackupPreview(result.manifest, result.data?.counts() ?: result.manifest.counts, needsPassword = result.data == null)
    }

    suspend fun restore(
        open: () -> InputStream,
        password: CharArray?,
        mode: RestoreMode,
        sections: Set<BackupSection>,
    ): RestoreReport = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "restore-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            // 1. Validação completa antes de tocar em qualquer dado.
            val result = open().use { BackupArchive.read(it, password, staging) }
            val data = result.data ?: throw BackupException.PasswordRequired()
            val filtered = filter(data, sections)

            // 2. Backup preventivo do estado atual.
            val preventive = createPreventiveBackup()

            // 3. Anexos primeiro (nomes únicos por UUID), depois o banco em uma única transação.
            if (BackupSection.ATTACHMENTS in sections) {
                val wanted = (filtered.taskAttachments.map { it.relativePath } + filtered.noteAttachments.map { it.relativePath }).toSet()
                result.attachments.forEach { (path, file) ->
                    val relative = path.removePrefix(BackupArchive.ATTACHMENTS_DIR)
                    if (relative in wanted) file.copyTo(storage.resolve(relative), overwrite = true)
                }
            }
            db.withTransaction {
                if (mode == RestoreMode.REPLACE) clearAll()
                insert(sanitize(filtered, mode))
            }
            if (mode == RestoreMode.REPLACE) removeOrphanAttachments()
            if (BackupSection.SETTINGS in sections) result.settings?.let { settings.importRaw(it) }

            activity.log(ActivityType.BACKUP_RESTORED, EntityType.SYSTEM, null, "Backup restaurado", null, time.now())
            RestoreReport(filtered.counts(), preventive.name)
        } finally {
            staging.deleteRecursively()
        }
    }

    /** Cópia completa e sem senha no armazenamento privado; mantém as 3 mais recentes. */
    suspend fun createPreventiveBackup(): File = withContext(Dispatchers.IO) {
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(time.now().atZone(ZoneId.systemDefault()))
        val file = File(preventiveDir, "preventivo-$stamp.zip")
        val tmp = File(preventiveDir, file.name + ".part")
        tmp.outputStream().use { export(it, BackupSection.entries.toSet(), null) }
        if (!tmp.renameTo(file)) throw java.io.IOException("Falha ao gravar backup preventivo")
        preventiveDir.listFiles { f -> f.name.startsWith("preventivo-") && f.name.endsWith(".zip") }
            ?.sortedByDescending { it.name }?.drop(KEEP_PREVENTIVE)?.forEach { it.delete() }
        file
    }

    fun preventiveBackups(): List<File> =
        preventiveDir.listFiles { f -> f.name.endsWith(".zip") }?.sortedByDescending { it.name }.orEmpty()

    private suspend fun collect(sections: Set<BackupSection>): BackupData {
        val tasks = BackupSection.TASKS in sections
        val notes = BackupSection.NOTES in sections
        val checklists = BackupSection.CHECKLISTS in sections
        val any = tasks || notes || checklists
        return BackupData(
            projects = if (any) dao.projects() else emptyList(),
            categories = if (any) dao.categories() else emptyList(),
            tags = if (any) dao.tags() else emptyList(),
            rules = if (tasks || checklists) dao.rules() else emptyList(),
            tasks = if (tasks) dao.tasks() else emptyList(),
            subtasks = if (tasks) dao.subtasks() else emptyList(),
            taskTags = if (tasks) dao.taskTags() else emptyList(),
            taskAttachments = if (tasks) dao.taskAttachments() else emptyList(),
            noteFolders = if (notes) dao.noteFolders() else emptyList(),
            notes = if (notes) dao.notes() else emptyList(),
            noteTags = if (notes) dao.noteTags() else emptyList(),
            noteAttachments = if (notes) dao.noteAttachments() else emptyList(),
            noteDrafts = if (notes) dao.noteDrafts() else emptyList(),
            links = if (notes) dao.links() else emptyList(),
            checklistTemplates = if (checklists) dao.templates() else emptyList(),
            checklistItems = if (checklists) dao.items() else emptyList(),
            occurrences = if (checklists) dao.occurrences() else emptyList(),
            itemStates = if (checklists) dao.states() else emptyList(),
            reminders = dao.reminders().filter { (tasks && it.taskId != null) || (checklists && it.checklistId != null) },
            activity = if (BackupSection.HISTORY in sections) dao.activity() else emptyList(),
        )
    }

    private fun filter(data: BackupData, sections: Set<BackupSection>): BackupData {
        val tasks = BackupSection.TASKS in sections
        val notes = BackupSection.NOTES in sections
        val checklists = BackupSection.CHECKLISTS in sections
        return data.copy(
            tasks = if (tasks) data.tasks else emptyList(),
            subtasks = if (tasks) data.subtasks else emptyList(),
            taskTags = if (tasks) data.taskTags else emptyList(),
            taskAttachments = if (tasks && BackupSection.ATTACHMENTS in sections) data.taskAttachments else emptyList(),
            noteFolders = if (notes) data.noteFolders else emptyList(),
            notes = if (notes) data.notes else emptyList(),
            noteTags = if (notes) data.noteTags else emptyList(),
            noteAttachments = if (notes && BackupSection.ATTACHMENTS in sections) data.noteAttachments else emptyList(),
            noteDrafts = if (notes) data.noteDrafts else emptyList(),
            links = if (notes) data.links else emptyList(),
            checklistTemplates = if (checklists) data.checklistTemplates else emptyList(),
            checklistItems = if (checklists) data.checklistItems else emptyList(),
            occurrences = if (checklists) data.occurrences else emptyList(),
            itemStates = if (checklists) data.itemStates else emptyList(),
            reminders = data.reminders.filter { (tasks && it.taskId != null) || (checklists && it.checklistId != null) },
            activity = if (BackupSection.HISTORY in sections) data.activity else emptyList(),
        )
    }

    /** Remove referências que violariam chaves estrangeiras (ex.: tarefa ligada a nota não importada). */
    private suspend fun sanitize(data: BackupData, mode: RestoreMode): BackupData {
        val existingNotes = if (mode == RestoreMode.MERGE) dao.notes().map { it.id }.toSet() else emptySet()
        val existingTasks = if (mode == RestoreMode.MERGE) dao.tasks().map { it.id }.toSet() else emptySet()
        val existingTemplates = if (mode == RestoreMode.MERGE) dao.templates().map { it.id }.toSet() else emptySet()
        val existingFolders = if (mode == RestoreMode.MERGE) dao.noteFolders().map { it.id }.toSet() else emptySet()
        val noteIds = existingNotes + data.notes.map { it.id }
        val taskIds = existingTasks + data.tasks.map { it.id }
        val templateIds = existingTemplates + data.checklistTemplates.map { it.id }
        val folderIds = existingFolders + data.noteFolders.map { it.id }
        val itemIds = data.checklistItems.map { it.id }.toSet() + if (mode == RestoreMode.MERGE) dao.items().map { it.id } else emptyList()
        return data.copy(
            tasks = data.tasks.map { if (it.linkedNoteId != null && it.linkedNoteId !in noteIds) it.copy(linkedNoteId = null) else it },
            notes = data.notes.map { if (it.folderId != null && it.folderId !in folderIds) it.copy(folderId = null) else it },
            noteFolders = data.noteFolders.map { if (it.parentId != null && it.parentId !in folderIds) it.copy(parentId = null) else it },
            subtasks = data.subtasks.filter { it.taskId in taskIds },
            taskTags = data.taskTags.filter { it.taskId in taskIds },
            itemStates = data.itemStates.map { if (it.itemId != null && it.itemId !in itemIds) it.copy(itemId = null) else it },
            reminders = data.reminders.filter { (it.taskId == null || it.taskId in taskIds) && (it.checklistId == null || it.checklistId in templateIds) },
        )
    }

    private suspend fun clearAll() {
        dao.clearActivity(); dao.clearReminders(); dao.clearStates(); dao.clearOccurrences(); dao.clearItems()
        dao.clearTemplates(); dao.clearLinks(); dao.clearDrafts(); dao.clearNoteAttachments(); dao.clearNoteTags()
        dao.clearNotes(); dao.clearNoteFolders(); dao.clearTaskAttachments(); dao.clearTaskTags(); dao.clearSubtasks()
        dao.clearTasks(); dao.clearRules(); dao.clearTags(); dao.clearCategories(); dao.clearProjects()
    }

    private suspend fun insert(d: BackupData) {
        dao.insertProjects(d.projects); dao.insertCategories(d.categories); dao.insertTags(d.tags); dao.insertRules(d.rules)
        // Pastas: pais antes dos filhos.
        val folders = d.noteFolders.toMutableList()
        val inserted = HashSet<String>()
        while (folders.isNotEmpty()) {
            val ready = folders.filter { it.parentId == null || it.parentId in inserted || folders.none { f -> f.id == it.parentId } }
            if (ready.isEmpty()) { dao.insertNoteFolders(folders.map { it.copy(parentId = null) }); break }
            dao.insertNoteFolders(ready); inserted += ready.map { it.id }; folders.removeAll(ready)
        }
        dao.insertNotes(d.notes); dao.insertNoteTags(d.noteTags); dao.insertNoteAttachments(d.noteAttachments)
        dao.insertNoteDrafts(d.noteDrafts); dao.insertLinks(d.links)
        dao.insertTasks(d.tasks); dao.insertSubtasks(d.subtasks); dao.insertTaskTags(d.taskTags); dao.insertTaskAttachments(d.taskAttachments)
        dao.insertTemplates(d.checklistTemplates); dao.insertItems(d.checklistItems)
        dao.insertOccurrences(d.occurrences); dao.insertStates(d.itemStates)
        dao.insertReminders(d.reminders); dao.insertActivity(d.activity)
    }

    private suspend fun removeOrphanAttachments() {
        val referenced = (dao.taskAttachments().map { it.relativePath } + dao.noteAttachments().map { it.relativePath }).toSet()
        storage.root.listFiles()?.filter { it.isFile && it.name !in referenced }?.forEach { it.delete() }
    }

    private fun appVersion(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "?"

    companion object {
        const val KEEP_PREVENTIVE = 3
    }
}
