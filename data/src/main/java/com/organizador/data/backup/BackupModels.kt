package com.organizador.data.backup

import com.organizador.data.local.entity.ActivityEntity
import com.organizador.data.local.entity.CategoryEntity
import com.organizador.data.local.entity.ChecklistItemEntity
import com.organizador.data.local.entity.ChecklistItemStateEntity
import com.organizador.data.local.entity.ChecklistOccurrenceEntity
import com.organizador.data.local.entity.ChecklistTemplateEntity
import com.organizador.data.local.entity.EntityLinkEntity
import com.organizador.data.local.entity.NoteAttachmentEntity
import com.organizador.data.local.entity.NoteDraftEntity
import com.organizador.data.local.entity.NoteEntity
import com.organizador.data.local.entity.NoteFolderEntity
import com.organizador.data.local.entity.NoteTagEntity
import com.organizador.data.local.entity.ProjectEntity
import com.organizador.data.local.entity.RecurrenceRuleEntity
import com.organizador.data.local.entity.ReminderEntity
import com.organizador.data.local.entity.SubtaskEntity
import com.organizador.data.local.entity.TagEntity
import com.organizador.data.local.entity.TaskAttachmentEntity
import com.organizador.data.local.entity.TaskEntity
import com.organizador.data.local.entity.TaskTagEntity
import kotlinx.serialization.Serializable

enum class BackupSection { TASKS, NOTES, CHECKLISTS, HISTORY, SETTINGS, ATTACHMENTS }

enum class RestoreMode {
    /** Apaga os dados atuais e importa o backup (após backup preventivo automático). */
    REPLACE,

    /** Mantém os dados atuais e adiciona apenas os registros que ainda não existem (por id). */
    MERGE,
}

/** Exportação lógica do banco: independente do arquivo SQLite e do modo WAL, compatível entre versões. */
@Serializable
data class BackupData(
    val projects: List<ProjectEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val rules: List<RecurrenceRuleEntity> = emptyList(),
    val tasks: List<TaskEntity> = emptyList(),
    val subtasks: List<SubtaskEntity> = emptyList(),
    val taskTags: List<TaskTagEntity> = emptyList(),
    val taskAttachments: List<TaskAttachmentEntity> = emptyList(),
    val noteFolders: List<NoteFolderEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val noteTags: List<NoteTagEntity> = emptyList(),
    val noteAttachments: List<NoteAttachmentEntity> = emptyList(),
    val noteDrafts: List<NoteDraftEntity> = emptyList(),
    val links: List<EntityLinkEntity> = emptyList(),
    val checklistTemplates: List<ChecklistTemplateEntity> = emptyList(),
    val checklistItems: List<ChecklistItemEntity> = emptyList(),
    val occurrences: List<ChecklistOccurrenceEntity> = emptyList(),
    val itemStates: List<ChecklistItemStateEntity> = emptyList(),
    val reminders: List<ReminderEntity> = emptyList(),
    val activity: List<ActivityEntity> = emptyList(),
) {
    fun counts(): Map<String, Int> = mapOf(
        "projetos" to projects.size,
        "tarefas" to tasks.size,
        "subtarefas" to subtasks.size,
        "notas" to notes.size,
        "pastas" to noteFolders.size,
        "checklists" to checklistTemplates.size,
        "execuções" to occurrences.size,
        "etiquetas" to tags.size,
        "categorias" to categories.size,
        "anexos" to taskAttachments.size + noteAttachments.size,
        "histórico" to activity.size,
    )
}

@Serializable
data class ManifestFile(val path: String, val size: Long, val sha256: String)

@Serializable
data class BackupManifest(
    val format: String = BackupArchive.FORMAT,
    val formatVersion: Int = BackupArchive.FORMAT_VERSION,
    val appVersion: String,
    val schemaVersion: Int,
    val createdAt: Long,
    val sections: List<BackupSection>,
    val counts: Map<String, Int>,
    val encrypted: Boolean = false,
    val kdf: String? = null,
    val kdfIterations: Int? = null,
    val kdfSalt: String? = null,
    /** Constante cifrada com a chave derivada: permite validar a senha antes de qualquer restauração. */
    val verifier: String? = null,
    val files: List<ManifestFile> = emptyList(),
)

data class BackupPreview(
    val manifest: BackupManifest,
    /** Contagens reais lidas do conteúdo (ou do manifesto, se cifrado e sem senha). */
    val counts: Map<String, Int>,
    val needsPassword: Boolean,
)

data class RestoreReport(val counts: Map<String, Int>, val preventiveBackup: String?)

sealed class BackupException(message: String) : Exception(message) {
    class NotABackup : BackupException("O arquivo não é um backup do Organizador")
    class Corrupted(detail: String) : BackupException("Backup corrompido: $detail")
    class Malicious(detail: String) : BackupException("Backup rejeitado por segurança: $detail")
    class WrongPassword : BackupException("Senha incorreta")
    class PasswordRequired : BackupException("Este backup é protegido por senha")
    class Incompatible(detail: String) : BackupException("Backup incompatível: $detail")
}
