package com.yeonsik.fitnessapp.core.database

import androidx.room.*

/** Private review records; never included in native fitness aggregates or legacy sync. */
@Entity(tableName = "fitness_information_inbox", primaryKeys = ["user_id", "project", "record_id"])
data class InformationInboxEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    val project: String,
    @ColumnInfo(name = "record_id") val recordId: String,
    val title: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    val fingerprint: String,
    val status: String,
    @ColumnInfo(name = "received_at") val receivedAt: String
)

@Entity(tableName = "fitness_information_drafts", primaryKeys = ["user_id", "project"])
data class InformationDraftEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    val project: String,
    @ColumnInfo(name = "draft_json") val draftJson: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String
)

@Dao
interface InformationInboxDao {
    @Query("SELECT record_id, title, status, received_at FROM fitness_information_inbox WHERE user_id = :userId AND project = :project ORDER BY received_at DESC, record_id")
    fun list(userId: String, project: String): List<InformationInboxSummary>

    @Query("SELECT * FROM fitness_information_inbox WHERE user_id = :userId AND project = :project AND record_id = :recordId")
    fun find(userId: String, project: String, recordId: String): InformationInboxEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(record: InformationInboxEntity)

    @Query("UPDATE fitness_information_inbox SET status = :status WHERE user_id = :userId AND project = :project AND record_id = :recordId")
    fun updateStatus(userId: String, project: String, recordId: String, status: String): Int

    @Query("SELECT * FROM fitness_information_drafts WHERE user_id = :userId AND project = :project")
    fun draft(userId: String, project: String): InformationDraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveDraft(draft: InformationDraftEntity)

    @Query("DELETE FROM fitness_information_drafts WHERE user_id = :userId AND project = :project")
    fun clearDraft(userId: String, project: String)
}

data class InformationInboxSummary(
    @ColumnInfo(name = "record_id") val recordId: String,
    val title: String,
    val status: String,
    @ColumnInfo(name = "received_at") val receivedAt: String
)
