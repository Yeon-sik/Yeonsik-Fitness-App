package com.yeonsik.fitnessapp.core.database

import androidx.room.*

/** Receipt is committed with native facts; it never replaces the original information payload. */
@Entity(tableName = "fitness_information_applications", primaryKeys = ["user_id", "project", "record_id"],
    indices = [Index(name = "fitness_information_applications_owner_record", value = ["user_id", "record_id"], unique = true),
        Index(name = "fitness_information_applications_owner_lineage", value = ["user_id", "lineage_root_id"], unique = true)])
data class InformationApplicationEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    val project: String,
    @ColumnInfo(name = "record_id") val recordId: String,
    @ColumnInfo(name = "source_fingerprint") val sourceFingerprint: String,
    @ColumnInfo(name = "lineage_root_id", defaultValue = "''") val lineageRootId: String,
    val kind: String,
    @ColumnInfo(name = "native_id") val nativeId: String,
    @ColumnInfo(name = "mapping_json") val mappingJson: String,
    @ColumnInfo(name = "applied_at") val appliedAt: String
)

@Entity(tableName = "fitness_information_deliveries", primaryKeys = ["user_id", "project", "record_id", "source_project", "source_user_id"])
data class InformationDeliveryEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    val project: String,
    @ColumnInfo(name = "record_id") val recordId: String,
    @ColumnInfo(name = "source_project") val sourceProject: String,
    @ColumnInfo(name = "source_user_id") val sourceUserId: String,
    @ColumnInfo(name = "content_fingerprint") val contentFingerprint: String,
    @ColumnInfo(name = "remote_received_at") val remoteReceivedAt: String
)

@Dao
interface InformationProjectionDao {
    @Query("SELECT * FROM fitness_information_applications WHERE user_id=:userId AND project=:project AND record_id=:recordId")
    fun application(userId: String, project: String, recordId: String): InformationApplicationEntity?

    // Native tables are user-scoped, so delivery/import namespaces cannot permit a second application.
    @Query("SELECT * FROM fitness_information_applications WHERE user_id=:userId AND record_id=:recordId")
    fun applicationForOwner(userId: String, recordId: String): InformationApplicationEntity?

    @Query("SELECT * FROM fitness_information_applications WHERE user_id=:userId AND lineage_root_id=:rootId")
    fun applicationForLineage(userId: String, rootId: String): InformationApplicationEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertApplication(receipt: InformationApplicationEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertDelivery(delivery: InformationDeliveryEntity)
}
