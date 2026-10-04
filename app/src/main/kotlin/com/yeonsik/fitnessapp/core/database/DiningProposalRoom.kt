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
    primaryKeys = ["owner_id", "nutrition_food_id", "kind", "remote_scope"])
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
    @ColumnInfo(name = "updated_at") val updatedAt: String
) {
    fun model() = DiningProposal(ownerId, nutritionFoodId, kind, remoteScope, idempotencyKey,
        requestJson, candidateId, status, restaurantId, restaurantLocationId, restaurantMenuId,
        catalogProductId, reviewNote, createdAt, updatedAt)
}

@Dao
interface DiningProposalRoomDao {
    @Query("SELECT * FROM dining_identity_proposals WHERE owner_id = :ownerId ORDER BY created_at")
    fun list(ownerId: String): List<DiningProposalRoomEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun save(proposal: DiningProposalRoomEntity)
}

class RoomDiningProposalStore(private val dao: DiningProposalRoomDao) : DiningProposalStore {
    override fun list(ownerId: String) = dao.list(ownerId).map { it.model() }
    override fun save(proposal: DiningProposal) = with(proposal) {
        dao.save(DiningProposalRoomEntity(ownerId, nutritionFoodId, kind, remoteScope,
            idempotencyKey, requestJson, candidateId, status, restaurantId, restaurantLocationId,
            restaurantMenuId, catalogProductId, reviewNote, createdAt, updatedAt))
    }
}
