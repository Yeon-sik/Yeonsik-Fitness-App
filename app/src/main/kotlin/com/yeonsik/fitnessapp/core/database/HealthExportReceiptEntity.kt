package com.yeonsik.fitnessapp.core.database

import androidx.room.*

/** Outgoing integration receipt, not a health fact or a Fitness inbox entry. */
@Entity(tableName = "fitness_health_export_receipts", primaryKeys = ["user_id", "project", "snapshot_key"])
data class HealthExportReceiptEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    val project: String,
    @ColumnInfo(name = "snapshot_key") val snapshotKey: String,
    @ColumnInfo(name = "record_id") val recordId: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "created_at") val createdAt: String
)

@Dao
interface HealthExportReceiptDao {
    @Query("SELECT * FROM fitness_health_export_receipts WHERE user_id = :userId AND project = :project AND snapshot_key = :snapshotKey")
    fun find(userId: String, project: String, snapshotKey: String): HealthExportReceiptEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(receipt: HealthExportReceiptEntity)
}
