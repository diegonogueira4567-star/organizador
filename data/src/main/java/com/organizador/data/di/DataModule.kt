package com.organizador.data.di

import android.content.Context
import com.organizador.data.local.OrganizadorDatabase
import com.organizador.data.prefs.SettingsRepositoryImpl
import com.organizador.data.repository.ActivityRepositoryImpl
import com.organizador.data.repository.AttachmentRepositoryImpl
import com.organizador.data.repository.ChecklistRepositoryImpl
import com.organizador.data.repository.LabelRepositoryImpl
import com.organizador.data.repository.NoteRepositoryImpl
import com.organizador.data.repository.ProjectRepositoryImpl
import com.organizador.data.repository.RecurrenceRepositoryImpl
import com.organizador.data.repository.ReminderRepositoryImpl
import com.organizador.data.repository.RoomTransactionRunner
import com.organizador.data.repository.StatisticsRepositoryImpl
import com.organizador.data.repository.TaskRepositoryImpl
import com.organizador.domain.model.SystemTimeProvider
import com.organizador.domain.model.TimeProvider
import com.organizador.domain.repository.ActivityRepository
import com.organizador.domain.repository.AttachmentRepository
import com.organizador.domain.repository.ChecklistRepository
import com.organizador.domain.repository.LabelRepository
import com.organizador.domain.repository.NoteRepository
import com.organizador.domain.repository.ProjectRepository
import com.organizador.domain.repository.RecurrenceRepository
import com.organizador.domain.repository.ReminderRepository
import com.organizador.domain.repository.SettingsRepository
import com.organizador.domain.repository.StatisticsRepository
import com.organizador.domain.repository.TaskRepository
import com.organizador.domain.repository.TransactionRunner
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): OrganizadorDatabase = OrganizadorDatabase.build(context)

    @Provides
    @Singleton
    fun timeProvider(): TimeProvider = SystemTimeProvider()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun tasks(impl: TaskRepositoryImpl): TaskRepository
    @Binds abstract fun projects(impl: ProjectRepositoryImpl): ProjectRepository
    @Binds abstract fun labels(impl: LabelRepositoryImpl): LabelRepository
    @Binds abstract fun recurrence(impl: RecurrenceRepositoryImpl): RecurrenceRepository
    @Binds abstract fun checklists(impl: ChecklistRepositoryImpl): ChecklistRepository
    @Binds abstract fun notes(impl: NoteRepositoryImpl): NoteRepository
    @Binds abstract fun attachments(impl: AttachmentRepositoryImpl): AttachmentRepository
    @Binds abstract fun reminders(impl: ReminderRepositoryImpl): ReminderRepository
    @Binds abstract fun activity(impl: ActivityRepositoryImpl): ActivityRepository
    @Binds abstract fun statistics(impl: StatisticsRepositoryImpl): StatisticsRepository
    @Binds abstract fun settings(impl: SettingsRepositoryImpl): SettingsRepository
    @Binds abstract fun transactions(impl: RoomTransactionRunner): TransactionRunner
}
