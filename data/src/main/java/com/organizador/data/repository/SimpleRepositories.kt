package com.organizador.data.repository

import androidx.room.withTransaction
import com.organizador.data.local.OrganizadorDatabase
import com.organizador.data.local.invalidationFlow
import com.organizador.data.local.entity.ActivityEntity
import com.organizador.data.local.entity.NoteAttachmentEntity
import com.organizador.data.local.entity.TaskAttachmentEntity
import com.organizador.data.mapper.toDomain
import com.organizador.data.mapper.toEntity
import com.organizador.data.storage.AttachmentStorage
import com.organizador.domain.model.ActivityEntry
import com.organizador.domain.model.ActivityType
import com.organizador.domain.model.Attachment
import com.organizador.domain.model.AttachmentOwner
import com.organizador.domain.model.Category
import com.organizador.domain.model.EntityType
import com.organizador.domain.model.Ids
import com.organizador.domain.model.Palette
import com.organizador.domain.model.Project
import com.organizador.domain.model.Reminder
import com.organizador.domain.model.Tag
import com.organizador.domain.model.TimeProvider
import com.organizador.domain.recurrence.RecurrenceRule
import com.organizador.domain.repository.ActivityRepository
import com.organizador.domain.repository.AttachmentRepository
import com.organizador.domain.repository.LabelRepository
import com.organizador.domain.repository.ProjectRepository
import com.organizador.domain.repository.RecurrenceRepository
import com.organizador.domain.repository.ReminderRepository
import com.organizador.domain.repository.TransactionRunner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.InputStream
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomTransactionRunner @Inject constructor(private val db: OrganizadorDatabase) : TransactionRunner {
    override suspend fun <T> inTransaction(block: suspend () -> T): T = db.withTransaction { block() }
}

@Singleton
class ProjectRepositoryImpl @Inject constructor(
    private val db: OrganizadorDatabase,
    private val activity: ActivityRepository,
    private val time: TimeProvider,
) : ProjectRepository {
    private val dao = db.projectDao()

    override fun observeProjects(includeArchived: Boolean): Flow<List<Project>> =
        dao.observeAll(includeArchived).map { list -> list.map { it.toDomain() } }

    override fun observeProject(id: String): Flow<Project?> = dao.observe(id).map { it?.toDomain() }
    override suspend fun getProject(id: String): Project? = dao.get(id)?.toDomain()

    override suspend fun save(project: Project) = db.withTransaction {
        val existing = dao.get(project.id)
        val order = existing?.sortOrder ?: (dao.maxOrder() + 1)
        dao.upsert(project.copy(sortOrder = order, updatedAt = time.now()).toEntity())
        if (existing == null) activity.log(ActivityType.PROJECT_CREATED, EntityType.PROJECT, project.id, project.name, project.id, time.now())
    }

    override suspend fun setArchived(id: String, archived: Boolean) = dao.setArchived(id, archived, time.now().toEpochMilli())

    /** Tarefas, notas e checklists do projeto não são apagados: voltam para a Caixa de entrada (FK SET NULL). */
    override suspend fun delete(id: String) = dao.delete(id)

    override suspend fun reorder(orderedIds: List<String>) =
        db.withTransaction { orderedIds.forEachIndexed { i, id -> dao.setOrder(id, i) } }
}

@Singleton
class LabelRepositoryImpl @Inject constructor(
    private val db: OrganizadorDatabase,
    private val time: TimeProvider,
) : LabelRepository {
    private val dao = db.labelDao()

    override fun observeCategories(): Flow<List<Category>> = dao.observeCategories().map { l -> l.map { it.toDomain() } }
    override fun observeTags(): Flow<List<Tag>> = dao.observeTags().map { l -> l.map { it.toDomain() } }
    override suspend fun saveCategory(category: Category) = dao.upsertCategory(category.toEntity())
    override suspend fun deleteCategory(id: String) = dao.deleteCategory(id)
    override suspend fun reorderCategories(orderedIds: List<String>) =
        db.withTransaction { orderedIds.forEachIndexed { i, id -> dao.setCategoryOrder(id, i) } }

    override suspend fun saveTag(tag: Tag) {
        val clash = dao.tagByName(tag.name.trim())
        require(clash == null || clash.id == tag.id) { "Já existe uma etiqueta com esse nome" }
        dao.upsertTag(tag.copy(name = tag.name.trim()).toEntity())
    }

    override suspend fun deleteTag(id: String) = dao.deleteTag(id)

    override suspend fun findOrCreateTag(name: String): Tag = db.withTransaction {
        val clean = name.trim().removePrefix("#")
        dao.tagByName(clean)?.toDomain() ?: Tag(Ids.new(), clean, Palette.colors[Math.floorMod(clean.hashCode(), Palette.colors.size)], time.now())
            .also { dao.upsertTag(it.toEntity()) }
    }
}

@Singleton
class RecurrenceRepositoryImpl @Inject constructor(db: OrganizadorDatabase) : RecurrenceRepository {
    private val dao = db.recurrenceDao()
    override suspend fun get(id: String): RecurrenceRule? = dao.get(id)?.toDomain()
    override suspend fun save(rule: RecurrenceRule) = dao.upsert(rule.toEntity())
    override suspend fun delete(id: String) = dao.deleteIfOrphan(id)
}

@Singleton
class ReminderRepositoryImpl @Inject constructor(db: OrganizadorDatabase) : ReminderRepository {
    private val dao = db.reminderDao()
    private val database = db

    override fun observeFor(ownerId: String): Flow<List<Reminder>> = dao.observeFor(ownerId).map { l -> l.map { it.toDomain() } }

    override fun observePending(): Flow<List<Reminder>> =
        database.invalidationFlow("reminders").map { pending() }

    override suspend fun pending(): List<Reminder> = dao.pending().map { it.toDomain() }
    override suspend fun get(id: String): Reminder? = dao.get(id)?.toDomain()
    override suspend fun forOwner(ownerId: String): List<Reminder> = dao.forOwner(ownerId).map { it.toDomain() }
    override suspend fun save(reminder: Reminder) = dao.upsert(reminder.toEntity())
    override suspend fun markFired(id: String) = dao.markFired(id)
    override suspend fun snooze(id: String, until: Instant) = dao.snooze(id, until.toEpochMilli())
    override suspend fun delete(id: String) = dao.delete(id)
    override suspend fun deleteForOwner(ownerId: String) = dao.deleteForOwner(ownerId)
}

@Singleton
class ActivityRepositoryImpl @Inject constructor(db: OrganizadorDatabase) : ActivityRepository {
    private val dao = db.activityDao()

    override fun observeRecent(limit: Int): Flow<List<ActivityEntry>> = dao.observeRecent(limit).map { l -> l.map { it.toDomain() } }

    override suspend fun log(type: ActivityType, entityType: EntityType, entityId: String?, title: String, projectId: String?, at: Instant) {
        dao.insert(ActivityEntity(Ids.new(), type.name, entityType.name, entityId, title.take(120), projectId, at.toEpochMilli()))
    }
}

@Singleton
class AttachmentRepositoryImpl @Inject constructor(
    private val db: OrganizadorDatabase,
    private val storage: AttachmentStorage,
) : AttachmentRepository {
    override fun observe(owner: AttachmentOwner, ownerId: String): Flow<List<Attachment>> = when (owner) {
        AttachmentOwner.TASK -> db.taskDao().observeAttachments(ownerId).map { l -> l.map { it.toDomain() } }
        AttachmentOwner.NOTE -> db.noteDao().observeAttachments(ownerId).map { l -> l.map { it.toDomain() } }
    }

    override suspend fun get(id: String): Attachment? =
        db.noteDao().attachment(id)?.toDomain() ?: db.taskDao().attachment(id)?.toDomain()

    override suspend fun add(owner: AttachmentOwner, ownerId: String, fileName: String, mimeType: String, source: InputStream, now: Instant): Attachment {
        val id = Ids.new()
        val extension = fileName.substringAfterLast('.', "").filter { it.isLetterOrDigit() }.take(8)
        val relative = if (extension.isEmpty()) id else "$id.$extension"
        val size = storage.write(relative, source)
        val safeName = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(120).ifBlank { "anexo" }
        try {
            when (owner) {
                AttachmentOwner.TASK -> db.taskDao().insertAttachment(TaskAttachmentEntity(id, ownerId, safeName, mimeType, size, relative, now.toEpochMilli()))
                AttachmentOwner.NOTE -> db.noteDao().insertAttachment(NoteAttachmentEntity(id, ownerId, safeName, mimeType, size, relative, now.toEpochMilli()))
            }
        } catch (e: Exception) {
            storage.delete(relative) // não deixar arquivo órfão se o registro falhar
            throw e
        }
        return Attachment(id, owner, ownerId, safeName, mimeType, size, relative, now)
    }

    override suspend fun delete(id: String) {
        db.noteDao().attachment(id)?.let {
            db.noteDao().deleteAttachment(it)
            storage.delete(it.relativePath)
            return
        }
        db.taskDao().attachment(id)?.let {
            db.taskDao().deleteAttachment(it)
            storage.delete(it.relativePath)
        }
    }

    override fun fileFor(attachment: Attachment): File = storage.resolve(attachment.relativePath)
}
