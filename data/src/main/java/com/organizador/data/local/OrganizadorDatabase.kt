package com.organizador.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.organizador.data.local.dao.ActivityDao
import com.organizador.data.local.dao.BackupDao
import com.organizador.data.local.dao.ChecklistDao
import com.organizador.data.local.dao.LabelDao
import com.organizador.data.local.dao.NoteDao
import com.organizador.data.local.dao.ProjectDao
import com.organizador.data.local.dao.RecurrenceDao
import com.organizador.data.local.dao.ReminderDao
import com.organizador.data.local.dao.StatsDao
import com.organizador.data.local.dao.TaskDao
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

@Database(
    entities = [
        ProjectEntity::class, CategoryEntity::class, TagEntity::class, RecurrenceRuleEntity::class,
        TaskEntity::class, SubtaskEntity::class, TaskTagEntity::class, TaskAttachmentEntity::class,
        NoteFolderEntity::class, NoteEntity::class, NoteTagEntity::class, NoteAttachmentEntity::class, NoteDraftEntity::class,
        EntityLinkEntity::class,
        ChecklistTemplateEntity::class, ChecklistItemEntity::class, ChecklistOccurrenceEntity::class, ChecklistItemStateEntity::class,
        ReminderEntity::class, ActivityEntity::class,
    ],
    version = OrganizadorDatabase.VERSION,
    exportSchema = true,
)
abstract class OrganizadorDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun projectDao(): ProjectDao
    abstract fun labelDao(): LabelDao
    abstract fun recurrenceDao(): RecurrenceDao
    abstract fun checklistDao(): ChecklistDao
    abstract fun noteDao(): NoteDao
    abstract fun reminderDao(): ReminderDao
    abstract fun activityDao(): ActivityDao
    abstract fun statsDao(): StatsDao
    abstract fun backupDao(): BackupDao

    companion object {
        const val VERSION = 1
        const val NAME = "organizador.db"

        /**
         * Migrações versionadas. NUNCA usar fallbackToDestructiveMigration: uma atualização sem migração
         * deve falhar de forma visível em desenvolvimento, e não apagar os dados do usuário.
         */
        val MIGRATIONS: Array<Migration> = arrayOf()

        fun build(context: Context, name: String = NAME): OrganizadorDatabase =
            Room.databaseBuilder(context, OrganizadorDatabase::class.java, name)
                .addMigrations(*MIGRATIONS)
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        db.execSQL("PRAGMA foreign_keys = ON")
                    }
                })
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}
