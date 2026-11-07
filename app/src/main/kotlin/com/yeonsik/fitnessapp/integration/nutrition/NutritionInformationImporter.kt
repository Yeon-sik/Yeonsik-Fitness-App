package com.yeonsik.fitnessapp.integration.nutrition

import com.yeonsik.fitnessapp.core.database.*
import com.yeonsik.fitnessapp.feature.information.data.*
import java.time.Instant

/** Same transaction preserves the source and authenticated remote-account provenance. */
class NutritionInformationImporter(private val db: FitnessRoomDatabase, private val profile: InformationProfile) {
    fun importPage(source: NutritionMealSource, page: NutritionInformationPage, checkMapping: () -> Unit): NutritionMealImportResult {
        var inserted = 0; var duplicates = 0
        db.runInTransaction {
            checkMapping()
            page.records.forEach { delivery ->
                val record = delivery.record
                val existing = db.informationInboxDao().find(profile.userId, profile.project, record.id)
                if (existing != null) {
                    require(existing.payloadJson == record.canonical) { "같은 ID의 다른 원문이 있습니다. 원격 자료를 새 정정 ID로 작성하세요." }
                    duplicates++
                } else {
                    db.informationInboxDao().insert(InformationInboxEntity(profile.userId, profile.project, record.id, record.title,
                        record.canonical, record.fingerprint, InformationReviewState.PENDING.wire, Instant.now().toString()))
                    inserted++
                }
                db.informationProjectionDao().insertDelivery(InformationDeliveryEntity(profile.userId, profile.project, record.id,
                    source.project, source.owner, delivery.fingerprint, delivery.receivedAt))
            }
            checkMapping()
        }
        return NutritionMealImportResult(inserted, duplicates)
    }
}
