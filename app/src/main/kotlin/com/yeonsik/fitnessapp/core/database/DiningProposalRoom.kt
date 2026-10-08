package com.yeonsik.fitnessapp.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yeonsik.fitnessapp.integration.nutrition.DiningProposal
import com.yeonsik.fitnessapp.integration.nutrition.DiningProposalStore

@Entity(tableName = "dining_identity_proposals",
    primaryKeys = ["owner_id", "nutrition_food_id", "kind", "remote_scope", "request_version"])
data class DiningProposalRoomEntity(
    @ColumnInfo(name = "owner_id") val ownerId: String,
    @ColumnInfo(name = "nutrition_food_id") val nutritionFoodId: String,
    val kind: String,
    @ColumnInfo(name = "remote_scope") val remoteScope: String,
    @ColumnInfo(name = "idempotency_key") val idempotencyKey: String,
    @ColumnInfo(name = "request_json") val requestJson: String,
    @ColumnInfo(name = "candidate_id") val candidateId: String?,
    val status: String,
    @ColumnInfo(name = "restaurant_id") val restaurantId: String?,
    @ColumnInfo(name = "restaurant_location_id") val restaurantLocationId: String?,
    @ColumnInfo(name = "restaurant_menu_id") val restaurantMenuId: String?,
    @ColumnInfo(name = "catalog_product_id") val catalogProductId: String?,
    @ColumnInfo(name = "review_note") val reviewNote: String?,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "request_version", defaultValue = "1") val requestVersion: Int = 1
) {
    fun model() = DiningProposal(ownerId, nutritionFoodId, kind, remoteScope, idempotencyKey,
        requestJson, candidateId, status, restaurantId, restaurantLocationId, restaurantMenuId,
        catalogProductId, reviewNote, createdAt, updatedAt, requestVersion)
}

@Dao
interface DiningProposalRoomDao {
    @Query("SELECT * FROM dining_identity_proposals WHERE owner_id = :ownerId ORDER BY created_at, request_version")
    fun list(ownerId: String): List<DiningProposalRoomEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun save(proposal: DiningProposalRoomEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun reserve(proposal: DiningProposalRoomEntity)
}

class RoomDiningProposalStore(private val dao: DiningProposalRoomDao) : DiningProposalStore {
    override fun list(ownerId: String) = dao.list(ownerId).map { it.model() }
    override fun save(proposal: DiningProposal) = dao.save(entity(proposal))
    override fun reserve(proposal: DiningProposal) = dao.reserve(entity(proposal))
    private fun entity(proposal: DiningProposal) = with(proposal) {
        DiningProposalRoomEntity(ownerId, nutritionFoodId, kind, remoteScope,
            idempotencyKey, requestJson, candidateId, status, restaurantId, restaurantLocationId,
            restaurantMenuId, catalogProductId, reviewNote, createdAt, updatedAt, requestVersion)
    }
}
