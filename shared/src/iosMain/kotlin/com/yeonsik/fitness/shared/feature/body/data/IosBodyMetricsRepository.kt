package com.yeonsik.fitness.shared.feature.body.data

import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitness.shared.feature.body.api.BodyMetricsRepositoryApi
import com.yeonsik.fitness.shared.feature.body.model.BodyProfile
import com.yeonsik.fitness.shared.feature.body.model.BodyReadEntry
import com.yeonsik.fitness.shared.feature.body.model.BodyWeightWindow

/**
 * M7 functional-parity adapter for iOS.
 *
 * This is deliberately in-memory and is not production persistence.  The owner-scoped API is
 * the replacement boundary for a durable adapter before a commercial iOS release.
 */
class IosBodyMetricsRepository : BodyMetricsRepositoryApi {
    private val entriesByOwner = mutableMapOf<String, MutableList<BodyReadEntry>>()
    private val profilesByOwner = mutableMapOf<String, BodyProfile>()
    private var nextId: Long = 1

    override fun addBodyMetric(
        scope: AccountScope,
        date: String,
        weightKg: Double,
        memo: String
    ): String {
        val ownerId = owner(scope)
        val recordDate = requireRecordDate(date)
        val validatedWeight = requireBodyWeight(weightKg)
        val entries = entriesByOwner.getOrPut(ownerId) { mutableListOf() }
        val existing = entries.firstOrNull { it.date == recordDate }
        if (existing != null) {
            val index = entries.indexOfFirst { it.id == existing.id }
            entries[index] = existing.copy(weightKg = validatedWeight, memo = memo.trim())
            return existing.id
        }
        val id = "ios-body-${nextId++}"
        entries += BodyReadEntry(id, recordDate, validatedWeight, memo.trim())
        return id
    }

    override fun bodyMetricEntryById(scope: AccountScope, id: String): BodyReadEntry? =
        entriesByOwner[owner(scope)]?.firstOrNull { it.id == id.trim() }

    override fun bodyMetricEntriesForDate(
        scope: AccountScope,
        date: String?
    ): List<BodyReadEntry> {
        val entries = entriesByOwner[owner(scope)].orEmpty()
        return if (date == null) {
            entries.sortedWith(compareByDescending<BodyReadEntry> { it.date }.thenByDescending { it.id })
                .take(20)
        } else {
            val recordDate = requireRecordDate(date)
            entries.filter { it.date == recordDate }
                .sortedByDescending { it.id }
        }
    }

    override fun updateBodyMetric(
        scope: AccountScope,
        id: String,
        date: String,
        weightKg: Double,
        memo: String
    ) {
        val entries = entriesByOwner[owner(scope)] ?: return
        val index = entries.indexOfFirst { it.id == id.trim() }
        if (index < 0) return
        entries[index] = BodyReadEntry(
            id.trim(),
            requireRecordDate(date),
            requireBodyWeight(weightKg),
            memo.trim()
        )
    }

    override fun deleteBodyMetric(scope: AccountScope, id: String) {
        entriesByOwner[owner(scope)]?.removeAll { it.id == id.trim() }
    }

    override fun bodyProfile(scope: AccountScope): BodyProfile =
        profilesByOwner[owner(scope)] ?: BodyProfile.empty()

    override fun saveBodyProfile(scope: AccountScope, profile: BodyProfile) {
        val ownerId = owner(scope)
        if (profile.isConfigured()) {
            profilesByOwner[ownerId] = profile
        } else {
            profilesByOwner.remove(ownerId)
        }
    }

    override fun bodyMetrics(scope: AccountScope, date: String): List<BodyReadEntry> =
        bodyMetricEntriesForDate(scope, date)

    override fun latestBodyMetricOnOrBefore(
        scope: AccountScope,
        date: String
    ): BodyReadEntry? {
        val requested = requireRecordDate(date)
        return entriesByOwner[owner(scope)].orEmpty()
            .filter { it.date <= requested }
            .maxWithOrNull(compareBy<BodyReadEntry> { it.date }.thenBy { it.id })
    }

    override fun recordedDays(scope: AccountScope, startDate: String, endDate: String): Int =
        dates(scope, startDate, endDate).size

    override fun dates(scope: AccountScope, startDate: String, endDate: String): List<String> {
        val start = requireRecordDate(startDate)
        val end = requireRecordDate(endDate)
        require(start <= end) { "날짜 범위가 올바르지 않습니다." }
        return entriesByOwner[owner(scope)].orEmpty()
            .asSequence()
            .map { it.date }
            .filter { it in start..end }
            .toSet()
            .sorted()
    }

    override fun weightWindow(
        scope: AccountScope,
        startDate: String,
        endDate: String
    ): BodyWeightWindow {
        val rows = entriesByOwner[owner(scope)].orEmpty()
            .filter { it.date in dates(scope, startDate, endDate) }
        if (rows.isEmpty()) return BodyWeightWindow(null, 0)
        val dailyAverages = rows.groupBy { it.date }.values.map { day ->
            day.map { it.weightKg }.average()
        }
        return BodyWeightWindow(dailyAverages.average(), dailyAverages.size)
    }

    override fun weightEntries(
        scope: AccountScope,
        startDate: String,
        endDate: String
    ): List<BodyReadEntry> {
        val start = requireRecordDate(startDate)
        val end = requireRecordDate(endDate)
        require(start <= end) { "날짜 범위가 올바르지 않습니다." }
        return entriesByOwner[owner(scope)].orEmpty()
            .filter { it.date in start..end }
            .sortedWith(compareBy<BodyReadEntry> { it.date }.thenByDescending { it.id })
    }

    private fun owner(scope: AccountScope): String = scope.ownerId

    private fun requireRecordDate(value: String): String {
        val normalized = value.trim()
        require(normalized.matches(DATE_PATTERN)) {
            "날짜를 YYYY-MM-DD 형식으로 입력하세요."
        }
        val year = normalized.substring(0, 4).toInt()
        val month = normalized.substring(5, 7).toInt()
        val day = normalized.substring(8, 10).toInt()
        val daysInMonth = when (month) {
            2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
            4, 6, 9, 11 -> 30
            in 1..12 -> 31
            else -> 0
        }
        require(month in 1..12 && day in 1..daysInMonth) {
            "날짜를 YYYY-MM-DD 형식으로 입력하세요."
        }
        return normalized
    }

    private fun requireBodyWeight(value: Double): Double {
        require(value.isFinite() && value > 0) { "체중은 0보다 큰 숫자여야 합니다." }
        return value
    }

    private companion object {
        val DATE_PATTERN = Regex("\\d{4}-\\d{2}-\\d{2}")
    }
}
