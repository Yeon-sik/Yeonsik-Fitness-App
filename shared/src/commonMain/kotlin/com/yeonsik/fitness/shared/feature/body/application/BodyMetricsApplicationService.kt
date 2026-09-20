package com.yeonsik.fitness.shared.feature.body.application

import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitness.shared.feature.body.api.BodyMetricsRepositoryApi
import com.yeonsik.fitness.shared.feature.body.model.BodyProfile

/** Shared editor use cases for Android Room and the iOS adapter. */
class BodyMetricsApplicationService(
    private val repository: BodyMetricsRepositoryApi,
    ownerId: String
) {
    init {
        requireNotNull(repository) { "체중 저장소가 필요합니다." }
    }

    private var ownerId: String = requireOwner(ownerId)

    fun setOwnerId(ownerId: String) {
        this.ownerId = requireOwner(ownerId)
    }

    fun load(scope: AccountScope, date: String, recordId: String?): Editor {
        requireScope(scope)
        val entry = if (recordId == null) {
            repository.bodyMetricForDate(scope, date)
        } else {
            repository.bodyMetricEntryById(scope, recordId)
        }
        return entry?.let { Editor(it.id, it.date, it.weightKg, it.memo) }
            ?: Editor(null, date, 0.0, "")
    }

    fun save(
        scope: AccountScope,
        recordId: String?,
        date: String,
        weightKg: Double,
        memo: String
    ): String {
        requireScope(scope)
        if (recordId == null || recordId.trim().isEmpty()) {
            return repository.addBodyMetric(scope, date, weightKg, memo)
        }
        repository.updateBodyMetric(scope, recordId, date, weightKg, memo)
        return recordId
    }

    fun delete(scope: AccountScope, recordId: String) {
        requireScope(scope)
        repository.deleteBodyMetric(scope, recordId)
    }

    fun loadProfile(scope: AccountScope): BodyProfile {
        requireScope(scope)
        return repository.bodyProfile(scope)
    }

    fun saveProfile(scope: AccountScope, profile: BodyProfile) {
        requireScope(scope)
        repository.saveBodyProfile(scope, profile)
    }

    private fun requireScope(scope: AccountScope) {
        check(ownerId == scope.ownerId) { "계정이 변경된 뒤 체중 작업이 도착했습니다." }
    }

    private companion object {
        fun requireOwner(value: String?): String = value?.trim().orEmpty().also {
            require(it.isNotEmpty()) { "체중 계정 식별자가 필요합니다." }
        }
    }

    class Editor(
        val recordId: String?,
        date: String?,
        val weightKg: Double,
        memo: String?
    ) {
        val date: String = date ?: ""
        val memo: String = memo ?: ""

        fun exists(): Boolean = !recordId.isNullOrBlank()
    }
}
