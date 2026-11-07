package com.yeonsik.fitnessapp.feature.information.data

import com.yeonsik.fitnessapp.core.database.*
import com.yeonsik.fitnessapp.feature.information.model.*
import java.time.Instant

data class InformationProfile(val userId: String, val project: String) {
    init { require(userId.isNotBlank() && project.isNotBlank()) }
}

enum class InformationReviewState(val wire: String, val label: String) {
    PENDING("pending", "검토 대기"), REVIEWED("reviewed", "검토함"), ARCHIVED("archived", "보관함")
}

/** The profile is captured at screen entry, never accepted from imported JSON. */
class InformationInboxRepository(private val db: FitnessRoomDatabase, val profile: InformationProfile) {
    private val dao get() = db.informationInboxDao()
    fun records() = dao.list(profile.userId, profile.project)
    fun record(id: String) = dao.find(profile.userId, profile.project, id) ?: error("현재 계정의 기록을 찾지 못했습니다.")
    fun draft() = dao.draft(profile.userId, profile.project)?.draftJson
    fun saveDraft(text: String) {
        require(text.toByteArray(Charsets.UTF_8).size <= InformationRecordCodec.MAX_BYTES) { "초안은 1 MiB 이하여야 합니다." }
        dao.saveDraft(InformationDraftEntity(profile.userId, profile.project, text, Instant.now().toString()))
    }

    /** Returns false for an identical replay. A conflicting ID leaves the original untouched. */
    fun importRecord(text: String, ownerReviewed: Boolean): Boolean {
        require(ownerReviewed) { "활동·운동·식사·체중 정보인지 먼저 확인하세요." }
        val record = InformationRecordCodec.decode(text, InformationOwner.FITNESS)
        var inserted = false
        db.runInTransaction {
            val existing = dao.find(profile.userId, profile.project, record.id)
            if (existing != null) {
                require(existing.payloadJson == record.canonical) {
                    "같은 ID의 다른 내용이 있습니다. 기존 기록에서 정정 초안을 만드세요."
                }
            } else {
                dao.insert(InformationInboxEntity(profile.userId, profile.project, record.id, record.title,
                    record.canonical, record.fingerprint, InformationReviewState.PENDING.wire, Instant.now().toString()))
                inserted = true
            }
            dao.clearDraft(profile.userId, profile.project)
        }
        return inserted
    }

    fun changeState(id: String, state: InformationReviewState) {
        check(dao.updateStatus(profile.userId, profile.project, id, state.wire) == 1) { "현재 계정의 기록을 찾지 못했습니다." }
    }

    fun export(id: String): String {
        val row = dao.find(profile.userId, profile.project, id) ?: error("현재 계정의 기록을 찾지 못했습니다.")
        // A restored backup is validated again before it can leave this inbox.
        return InformationRecordCodec.decode(row.payloadJson, InformationOwner.FITNESS).canonical
    }
}
