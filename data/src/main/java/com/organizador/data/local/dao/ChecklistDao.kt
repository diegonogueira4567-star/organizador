package com.organizador.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.organizador.data.local.entity.ChecklistItemEntity
import com.organizador.data.local.entity.ChecklistItemStateEntity
import com.organizador.data.local.entity.ChecklistOccurrenceEntity
import com.organizador.data.local.entity.ChecklistTemplateEntity
import kotlinx.coroutines.flow.Flow

data class TemplateWithCount(@Embedded val template: ChecklistTemplateEntity, val itemCount: Int)

data class TemplateOccurrenceRow(
    @Embedded(prefix = "t_") val template: ChecklistTemplateEntity,
    @Embedded(prefix = "o_") val occurrence: ChecklistOccurrenceEntity,
)

@Dao
interface ChecklistDao {
    @Query(
        """SELECT t.*, (SELECT COUNT(*) FROM checklist_items i WHERE i.templateId = t.id) AS itemCount
           FROM checklist_templates t
           WHERE t.deletedAt IS NULL AND (t.archived = 0 OR :includeArchived)
             AND (:routine IS NULL OR t.isRoutine = :routine)
           ORDER BY t.archived, t.sortOrder, t.title COLLATE NOCASE""",
    )
    fun observeTemplates(routine: Boolean?, includeArchived: Boolean): Flow<List<TemplateWithCount>>

    @Query("SELECT * FROM checklist_templates WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeDeleted(): Flow<List<ChecklistTemplateEntity>>

    @Query("SELECT * FROM checklist_templates WHERE id = :id")
    fun observeTemplate(id: String): Flow<ChecklistTemplateEntity?>

    @Query("SELECT * FROM checklist_templates WHERE id = :id")
    suspend fun template(id: String): ChecklistTemplateEntity?

    @Query("SELECT * FROM checklist_templates WHERE kind = 'RECURRING' AND deletedAt IS NULL AND archived = 0 AND recurrenceRuleId IS NOT NULL")
    suspend fun recurringTemplates(): List<ChecklistTemplateEntity>

    @Upsert
    suspend fun upsertTemplate(template: ChecklistTemplateEntity)

    @Query("UPDATE checklist_templates SET archived = :archived, updatedAt = :at WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean, at: Long)

    @Query("UPDATE checklist_templates SET deletedAt = :deletedAt, updatedAt = :at WHERE id = :id")
    suspend fun setDeleted(id: String, deletedAt: Long?, at: Long)

    @Query("UPDATE checklist_templates SET trackingFrom = :trackingFrom, generatedThrough = :generatedThrough WHERE id = :id")
    suspend fun setTracking(id: String, trackingFrom: Long?, generatedThrough: Long?)

    @Query("UPDATE checklist_templates SET generatedThrough = :generatedThrough WHERE id = :id")
    suspend fun setGeneratedThrough(id: String, generatedThrough: Long?)

    @Query("DELETE FROM checklist_templates WHERE id = :id")
    suspend fun deleteTemplate(id: String)

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM checklist_templates")
    suspend fun maxOrder(): Int

    // Itens do modelo
    @Query("SELECT * FROM checklist_items WHERE templateId = :templateId ORDER BY sortOrder")
    suspend fun items(templateId: String): List<ChecklistItemEntity>

    @Query("SELECT * FROM checklist_items WHERE templateId = :templateId ORDER BY sortOrder")
    fun observeItems(templateId: String): Flow<List<ChecklistItemEntity>>

    @Upsert
    suspend fun upsertItem(item: ChecklistItemEntity)

    @Query("DELETE FROM checklist_items WHERE templateId = :templateId AND id NOT IN (:keep)")
    suspend fun deleteItemsExcept(templateId: String, keep: List<String>)

    // Execuções
    /** IGNORE + índice único (templateId, date) = geração idempotente. Retorna -1 quando já existia. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOccurrence(occurrence: ChecklistOccurrenceEntity): Long

    @Query("SELECT * FROM checklist_occurrences WHERE id = :id")
    suspend fun occurrence(id: String): ChecklistOccurrenceEntity?

    @Query("SELECT * FROM checklist_occurrences WHERE id = :id")
    fun observeOccurrence(id: String): Flow<ChecklistOccurrenceEntity?>

    @Query("SELECT * FROM checklist_occurrences WHERE templateId = :templateId AND date = :date")
    suspend fun occurrenceAt(templateId: String, date: Long): ChecklistOccurrenceEntity?

    @Query("SELECT * FROM checklist_occurrences WHERE templateId = :templateId ORDER BY date LIMIT 1")
    suspend fun firstOccurrence(templateId: String): ChecklistOccurrenceEntity?

    @Query("SELECT * FROM checklist_occurrences WHERE templateId = :templateId AND closedAt IS NULL ORDER BY date DESC LIMIT 1")
    fun observeCurrentOccurrence(templateId: String): Flow<ChecklistOccurrenceEntity?>

    @Query("SELECT * FROM checklist_occurrences WHERE templateId = :templateId AND closedAt IS NULL ORDER BY date")
    suspend fun openOccurrences(templateId: String): List<ChecklistOccurrenceEntity>

    @Query("SELECT * FROM checklist_occurrences WHERE closedAt IS NULL")
    fun observeAllOpen(): Flow<List<ChecklistOccurrenceEntity>>

    @Query("SELECT * FROM checklist_occurrences WHERE templateId = :templateId ORDER BY date DESC LIMIT :limit")
    fun observeHistory(templateId: String, limit: Int): Flow<List<ChecklistOccurrenceEntity>>

    @Query(
        """SELECT t.id AS t_id, t.title AS t_title, t.description AS t_description, t.kind AS t_kind, t.isRoutine AS t_isRoutine,
                  t.projectId AS t_projectId, t.color AS t_color, t.icon AS t_icon, t.recurrenceRuleId AS t_recurrenceRuleId,
                  t.trackingFrom AS t_trackingFrom, t.generatedThrough AS t_generatedThrough, t.archived AS t_archived,
                  t.deletedAt AS t_deletedAt, t.sortOrder AS t_sortOrder, t.createdAt AS t_createdAt, t.updatedAt AS t_updatedAt,
                  o.id AS o_id, o.templateId AS o_templateId, o.date AS o_date, o.status AS o_status, o.totalItems AS o_totalItems,
                  o.checkedItems AS o_checkedItems, o.dueAt AS o_dueAt, o.closedAt AS o_closedAt, o.completedAt AS o_completedAt,
                  o.createdAt AS o_createdAt, o.updatedAt AS o_updatedAt
           FROM checklist_occurrences o JOIN checklist_templates t ON t.id = o.templateId
           WHERE t.deletedAt IS NULL AND o.date BETWEEN :from AND :to ORDER BY o.date""",
    )
    fun observeOccurrencesBetween(from: Long, to: Long): Flow<List<TemplateOccurrenceRow>>

    @Query(
        """SELECT t.id AS t_id, t.title AS t_title, t.description AS t_description, t.kind AS t_kind, t.isRoutine AS t_isRoutine,
                  t.projectId AS t_projectId, t.color AS t_color, t.icon AS t_icon, t.recurrenceRuleId AS t_recurrenceRuleId,
                  t.trackingFrom AS t_trackingFrom, t.generatedThrough AS t_generatedThrough, t.archived AS t_archived,
                  t.deletedAt AS t_deletedAt, t.sortOrder AS t_sortOrder, t.createdAt AS t_createdAt, t.updatedAt AS t_updatedAt,
                  o.id AS o_id, o.templateId AS o_templateId, o.date AS o_date, o.status AS o_status, o.totalItems AS o_totalItems,
                  o.checkedItems AS o_checkedItems, o.dueAt AS o_dueAt, o.closedAt AS o_closedAt, o.completedAt AS o_completedAt,
                  o.createdAt AS o_createdAt, o.updatedAt AS o_updatedAt
           FROM checklist_occurrences o JOIN checklist_templates t ON t.id = o.templateId
           WHERE t.deletedAt IS NULL AND t.archived = 0 AND o.closedAt IS NULL AND o.dueAt IS NOT NULL AND o.status = 'OPEN'""",
    )
    suspend fun openWithDue(): List<TemplateOccurrenceRow>

    @Query("UPDATE checklist_occurrences SET status = :status, closedAt = :closedAt, updatedAt = :at WHERE id = :id")
    suspend fun closeOccurrence(id: String, status: String, closedAt: Long, at: Long)

    @Query("UPDATE checklist_occurrences SET status = :status, closedAt = :closedAt, completedAt = :completedAt, updatedAt = :at WHERE id = :id")
    suspend fun setOccurrenceStatus(id: String, status: String, closedAt: Long?, completedAt: Long?, at: Long)

    @Query(
        """UPDATE checklist_occurrences SET
             totalItems = (SELECT COUNT(*) FROM checklist_item_states WHERE occurrenceId = :id),
             checkedItems = (SELECT COUNT(*) FROM checklist_item_states WHERE occurrenceId = :id AND checked = 1),
             updatedAt = :at
           WHERE id = :id""",
    )
    suspend fun refreshCounts(id: String, at: Long)

    @Query("DELETE FROM checklist_occurrences WHERE id = :id")
    suspend fun deleteOccurrence(id: String)

    // Estados dos itens
    @Query("SELECT * FROM checklist_item_states WHERE occurrenceId = :occurrenceId ORDER BY sortOrder")
    fun observeStates(occurrenceId: String): Flow<List<ChecklistItemStateEntity>>

    @Query("SELECT * FROM checklist_item_states WHERE occurrenceId = :occurrenceId ORDER BY sortOrder")
    suspend fun states(occurrenceId: String): List<ChecklistItemStateEntity>

    @Query("SELECT * FROM checklist_item_states WHERE id = :id")
    suspend fun state(id: String): ChecklistItemStateEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStates(states: List<ChecklistItemStateEntity>)

    @Query("UPDATE checklist_item_states SET checked = :checked, checkedAt = :checkedAt WHERE id = :id")
    suspend fun setChecked(id: String, checked: Boolean, checkedAt: Long?)

    @Query("UPDATE checklist_item_states SET title = :title, sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateState(id: String, title: String, sortOrder: Int)

    @Query("DELETE FROM checklist_item_states WHERE id = :id")
    suspend fun deleteState(id: String)
}
