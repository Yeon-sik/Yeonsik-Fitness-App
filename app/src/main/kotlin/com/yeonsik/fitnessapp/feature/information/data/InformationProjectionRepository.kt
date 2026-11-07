package com.yeonsik.fitnessapp.feature.information.data

import android.content.Context
import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitnessapp.core.database.*
import com.yeonsik.fitnessapp.data.*
import com.yeonsik.fitnessapp.feature.body.data.BodyMetricsRepository
import com.yeonsik.fitnessapp.feature.information.model.*
import com.yeonsik.fitnessapp.feature.meal.data.MealRecordRepository
import com.yeonsik.fitnessapp.feature.nutrition.data.NutritionCatalogRepository
import com.yeonsik.fitnessapp.feature.workout.data.WorkoutRoomStorage
import kotlinx.serialization.json.*
import java.time.Instant

class InformationProjectionRepository(
    private val db: FitnessRoomDatabase, private val context: Context, private val profile: InformationProfile
) {
    fun receipt(id: String) = db.informationProjectionDao().applicationForOwner(profile.userId, id)

    fun apply(id: String, preview: InformationProjectionPreview, confirmed: Boolean, checkCurrentProfile: () -> Unit): InformationApplicationEntity {
        require(confirmed) { "실제 기록과 통계에 반영할 내용을 확인하세요." }
        // Do not trust an old preview after device timezone/date or supplied values have changed.
        val checked = InformationProjection.preview(InformationProjectionDraft(preview.kind, preview.values))
        require(checked.mappingJson == preview.mappingJson) { "미리보기 조건이 변경되었습니다. 다시 확인하세요." }
        lateinit var result: InformationApplicationEntity
        db.runInTransaction {
            checkCurrentProfile()
            val row = db.informationInboxDao().find(profile.userId, profile.project, id) ?: error("현재 계정의 원문을 찾지 못했습니다.")
            val record = InformationRecordCodec.decode(row.payloadJson, InformationOwner.FITNESS)
            val existing = receipt(id)
            if (existing != null) {
                require(existing.sourceFingerprint == record.fingerprint && existing.mappingJson == checked.mappingJson) {
                    "이미 다른 내용으로 반영된 자료입니다. 실제 기록 화면에서 정정하세요."
                }
                result = existing
            } else {
                require(row.status != InformationReviewState.ARCHIVED.wire) { "보관한 자료는 검토 대기로 돌린 뒤 반영하세요." }
                var parent = record.payload["supersedes_record_id"]?.jsonPrimitive?.content
                var lineageRoot = record.id
                val ancestors = mutableSetOf(record.id)
                while (parent != null) {
                    require(ancestors.add(parent) && ancestors.size <= 64) { "정정 관계가 순환하거나 너무 깁니다. 실제 기록을 직접 확인하세요." }
                    require(receipt(parent) == null) { "이미 반영한 자료의 정정본입니다. 기존 실제 기록을 직접 정정해야 중복 집계를 막을 수 있습니다." }
                    val ancestor = db.informationInboxDao().find(profile.userId, profile.project, parent)
                        ?: error("정정 대상 원문이 현재 계정에 없습니다. 대상 자료를 먼저 가져오고 실제 반영 여부를 확인하세요.")
                    lineageRoot = parent
                    parent = InformationRecordCodec.decode(ancestor.payloadJson, InformationOwner.FITNESS).payload["supersedes_record_id"]?.jsonPrimitive?.content
                }
                require(db.informationProjectionDao().applicationForLineage(profile.userId, lineageRoot) == null) {
                    "같은 정정 계열의 자료가 이미 반영되었습니다. 기존 실제 기록을 정정하세요."
                }
                val scope = AccountScope(profile.userId)
                val nativeId = when (checked.kind) {
                    InformationProjectionKind.WEIGHT -> {
                        val body = BodyMetricsRepository(db, profile.userId)
                        require(body.bodyMetricForDate(scope, checked.text("date")) == null) {
                            "같은 날짜에 체중 기록이 있습니다. 기존 체중 화면에서 정정하세요."
                        }
                        body.addBodyMetric(scope, checked.text("date"), checked.number("weight_kg"), checked.text("memo"))
                    }
                    InformationProjectionKind.MEAL -> {
                        val nutrition = NutritionProfile.builder()
                            .value(NutritionProfile.CALORIES_KCAL, checked.number("calories_kcal"))
                            .value(NutritionProfile.PROTEIN_GRAMS, checked.number("protein_grams"))
                            .value(NutritionProfile.CARBS_GRAMS, checked.number("carbs_grams"))
                            .value(NutritionProfile.FAT_GRAMS, checked.number("fat_grams"))
                            .value(NutritionProfile.SODIUM_MG, checked.optionalNumber("sodium_mg"))
                            .value(NutritionProfile.SUGARS_GRAMS, checked.optionalNumber("sugars_grams"))
                            .value(NutritionProfile.SATURATED_FAT_GRAMS, checked.optionalNumber("saturated_fat_grams")).build()
                        val food = NutritionFood.builder().id(null).ownerId(profile.userId).name(checked.text("name"))
                            .kind(NutritionFood.KIND_INGREDIENT).category(NutritionFood.CATEGORY_OTHER)
                            .basis(1.0, NutritionUnit.SERVING).profile(nutrition).source("information_review", record.id)
                            .dataVersion(NutritionFood.DATA_VERSION_MACROS_ONLY).build()
                        val meals = MealRecordRepository(db, NutritionCatalogRepository(db, context, profile.userId), profile.userId)
                        meals.saveFoodMealItems(scope, checked.text("date"), checked.text("time"), listOf(MealCompositionItem.from(food, 1.0)))
                    }
                    InformationProjectionKind.WORKOUT -> {
                        val resistance = checked.text("mode") == "resistance"
                        WorkoutRoomStorage(db, context).createReviewedCompletedSession(scope, checked.text("date"), checked.text("activity_name"),
                            checked.text("memo"), checked.text("started_at"), checked.text("ended_at"), checked.text("duration_seconds").toInt(),
                            if (resistance) checked.text("sets").toInt() else 1, if (resistance) checked.text("reps").toInt() else null,
                            if (resistance) checked.number("load_kg") else null)
                    }
                }
                result = InformationApplicationEntity(profile.userId, profile.project, id, record.fingerprint, lineageRoot,
                    checked.kind.name.lowercase(), nativeId, checked.mappingJson, Instant.now().toString())
                db.informationProjectionDao().insertApplication(result)
                check(db.informationInboxDao().updateStatus(profile.userId, profile.project, id, InformationReviewState.REVIEWED.wire) == 1)
            }
            checkCurrentProfile()
        }
        return result
    }
}
