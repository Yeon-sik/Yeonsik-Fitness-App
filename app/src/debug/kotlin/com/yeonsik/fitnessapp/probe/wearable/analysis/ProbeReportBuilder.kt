package com.yeonsik.fitnessapp.probe.wearable.analysis

import java.time.Duration
import java.time.Instant
import java.util.Locale

/**
 * Builds the anonymized PR0 report text that the person running the probe copies into
 * `docs/wearable/PR0_PROBE_RESULT.md`.
 *
 * Privacy rules enforced here: times are relative to the session window, never absolute; record
 * ids appear only as device-local masks; heart-rate values never reach this layer at all.
 */
object ProbeReportBuilder {
    private const val MAX_LISTED_IDS = 20
    private const val PERMISSION_PREFIX = "android.permission.health."

    fun environment(environment: ProbeEnvironment): String = buildString {
        appendLine("## 환경")
        appendLine("- Android SDK: ${environment.androidSdkInt}")
        appendLine("- 휴대폰: ${environment.phoneManufacturer} ${environment.phoneModel}")
        appendLine("- Health Connect 상태: ${ProbeLabels.outcome(environment.healthConnectStatus)}")
        for ((packageName, version) in environment.packageVersions.toSortedMap()) {
            appendLine("- $packageName: ${version ?: "설치 안 됨 또는 확인 불가"}")
        }
        appendLine("- 요청 권한: ${shortPermissions(environment.requestedPermissions)}")
        appendLine("- 허용된 권한: ${shortPermissions(environment.grantedPermissions).ifEmpty { "없음" }}")
    }

    fun session(input: SessionProbeInput): String = buildString {
        val window = input.window
        appendLine("## 세션 ${input.label}")
        val pauses = window.pauses.sortedBy { it.start }
        val pauseText = if (pauses.isEmpty()) {
            ""
        } else {
            pauses.joinToString(prefix = " (", postfix = ")") { pause ->
                "${offset(window.start, pause.start)}~${offset(window.start, pause.end)}"
            }
        }
        appendLine(
            "- 세션 길이 ${hms(window.duration)}, 활동 ${hms(window.activeDuration())}, " +
                "일시정지 ${pauses.size}회$pauseText"
        )
        appendLine("- 조회 시점: 세션 종료 후 ${signedHms(Duration.between(window.end, input.queriedAt))}")
        appendLine("- 시간대: ${input.zone.id}")
        appendLine()
        appendHeartRate(input)
        appendLine()
        appendExerciseSessions(input)
        appendLine()
        appendSteps(input)
    }

    fun changes(observation: ChangesObservation): String = buildString {
        appendLine("## HeartRate changes 조회")
        appendLine("- 읽기 결과: ${outcomeLine(observation.outcome, observation.pages, null, observation.failureType)}")
        val issuedAt = observation.issuedAt
        appendLine(
            "- token 발급 후 경과: " +
                if (issuedAt == null) "발급 시각 미상" else hms(Duration.between(issuedAt, observation.queriedAt))
        )
        appendLine("- token 만료 응답: ${if (observation.tokenExpired) "예" else "아니오"}")
        if (observation.outcome == ReadOutcome.SUCCESS || observation.outcome == ReadOutcome.EMPTY_SUCCESS) {
            appendLine("- UpsertionChange: ${observation.upsertions}")
            appendLine(
                "- DeletionChange: ${observation.deletionMaskedIds.size} " +
                    "(마지막 완전 조회의 masked id와 일치 ${observation.deletionsMatchingLastQuery})"
            )
            appendLine("- 남은 변경 있음: ${if (observation.hasMoreRemaining) "예" else "아니오"}")
            appendMaskedIds(observation.deletionMaskedIds, label = "삭제된 masked id")
        }
    }

    private fun StringBuilder.appendHeartRate(input: SessionProbeInput) {
        val result = input.heartRate
        appendLine("### HeartRateRecord (G-HR)")
        appendLine("- 읽기 결과: ${outcomeLine(result.outcome, result.pages, result.complete, result.failureType)}")
        if (!result.outcome.isReadable()) return

        val records = result.items
        val allSampleTimes = records.flatMap { it.sampleTimes }
        val statistics = ProbeStatistics.compute(input.window, allSampleTimes)
        appendLine(
            "- record ${records.size}개, record 전체 sample ${allSampleTimes.size}개, " +
                "세션 구간 sample ${statistics.samplesInWindow}개, 활동 구간 sample ${statistics.samplesInActive}개 " +
                "(중복 시각 ${statistics.duplicateActiveTimestamps}개)"
        )
        val first = statistics.firstSampleOffset
        val last = statistics.lastSampleOffset
        appendLine(
            "- 세션 구간 sample 범위: " +
                if (first == null || last == null) "없음" else "${signedHms(first)} ~ ${signedHms(last)}"
        )
        appendLine(
            "- 활동 구간 sample 간격(nearest-rank): 중앙값 ${seconds(statistics.intervalMedian)}, " +
                "p95 ${seconds(statistics.intervalP95)}, 최대 ${seconds(statistics.intervalMax)}"
        )
        appendLine(
            "- coverage: ${statistics.coveredBinCount}/${statistics.activeBinCount} bin = " +
                "${percent(statistics.coverageRatio)} (60초 bin, 30초 미만 마지막 조각 제외)"
        )
        appendLine("- 활동 구간 최대 공백: ${statistics.maxActiveGap?.let(::hms) ?: "-"}")
        val gate = ProvisionalCoverageGate.evaluate(statistics)
        appendLine(
            "- 잠정 K-12 기준(coverage ≥ 80%, 최대 공백 ≤ 5분): ${gate.status} " +
                "— 판정 보조값이며 PR0 판정이 아님"
        )

        if (records.isNotEmpty()) {
            appendLine()
            appendLine("| origin | records | 활동 구간 samples | coverage | 최대 공백 | 잠정 기준 |")
            appendLine("| --- | --- | --- | --- | --- | --- |")
            for ((origin, originRecords) in records.groupBy { it.metadata.originPackage }.toSortedMap()) {
                val originStatistics = ProbeStatistics.compute(input.window, originRecords.flatMap { it.sampleTimes })
                val originGate = ProvisionalCoverageGate.evaluate(originStatistics)
                appendLine(
                    "| $origin | ${originRecords.size} | ${originStatistics.samplesInActive} | " +
                        "${percent(originStatistics.coverageRatio)} | " +
                        "${originStatistics.maxActiveGap?.let(::hms) ?: "-"} | ${originGate.status} |"
                )
            }
            appendLine()
            appendMetadataSummary(records.map { it.metadata }, input.window.end)
        }
        appendRequeryComparison(input, records, statistics.samplesInWindow)
        appendMaskedIds(records.mapNotNull { it.metadata.maskedId }, label = "masked id")
    }

    private fun StringBuilder.appendMetadataSummary(metadata: List<ObservedMetadata>, sessionEnd: Instant) {
        val methods = metadata.groupingBy { ProbeLabels.recordingMethod(it.recordingMethod) }.eachCount()
        appendLine("- recordingMethod: ${methods.toSortedMap().entries.joinToString { "${it.key} ${it.value}" }}")
        val devices = metadata.groupingBy { item ->
            listOf(
                ProbeLabels.deviceType(item.deviceType),
                item.deviceManufacturer ?: "manufacturer 없음",
                item.deviceModel ?: "model 없음",
            ).joinToString(" / ")
        }.eachCount()
        appendLine("- device: ${devices.toSortedMap().entries.joinToString { "${it.key} ${it.value}" }}")
        val withId = metadata.count { it.maskedId != null }
        val withClientId = metadata.count { it.hasClientRecordId }
        val versions = metadata.map { it.clientRecordVersion }
        appendLine(
            "- identity: metadata.id 있음 $withId/${metadata.size}, clientRecordId 있음 $withClientId/${metadata.size}, " +
                "clientRecordVersion ${versions.minOrNull()}~${versions.maxOrNull()}"
        )
        val delays = metadata.map { Duration.between(sessionEnd, it.lastModifiedAt) }.sorted()
        appendLine(
            "- 동기화 지연(lastModifiedTime − 세션 종료): 최소 ${signedHms(delays.first())}, " +
                "최대 ${signedHms(delays.last())}"
        )
    }

    private fun StringBuilder.appendRequeryComparison(
        input: SessionProbeInput,
        records: List<ObservedHeartRateRecord>,
        samplesInWindow: Int,
    ) {
        val previous = input.previous
        if (previous == null) {
            appendLine("- 재조회 비교: 같은 구간의 이전 완전 조회 없음")
            return
        }
        val current = records.mapNotNull { it.metadata.maskedId }.toSet()
        val kept = current.intersect(previous.maskedIds).size
        val appeared = (current - previous.maskedIds).size
        val disappeared = (previous.maskedIds - current).size
        appendLine(
            "- 재조회 비교: 이전 조회는 세션 종료 후 ${signedHms(Duration.between(input.window.end, previous.queriedAt))}; " +
                "유지 $kept, 새로 보임 $appeared, 사라짐 $disappeared; " +
                "세션 구간 sample 이전 ${previous.sampleCount} → 현재 $samplesInWindow"
        )
    }

    private fun StringBuilder.appendExerciseSessions(input: SessionProbeInput) {
        val result = input.exerciseSessions
        appendLine("### ExerciseSessionRecord (보조)")
        appendLine("- 읽기 결과: ${outcomeLine(result.outcome, result.pages, result.complete, result.failureType)}")
        if (!result.outcome.isReadable()) return
        appendLine("- 세션 구간과 겹치는 기록 ${result.items.size}개")
        if (result.items.isEmpty()) return
        appendLine()
        appendLine("| masked id | origin | exerciseType | 시작 | 종료 | segments | recordingMethod | device |")
        appendLine("| --- | --- | --- | --- | --- | --- | --- | --- |")
        for (session in result.items.sortedBy { it.startAt }) {
            val metadata = session.metadata
            appendLine(
                "| ${metadata.maskedId ?: "id 없음"} | ${metadata.originPackage} | ${session.exerciseType} | " +
                    "${offset(input.window.start, session.startAt)} | ${offset(input.window.start, session.endAt)} | " +
                    "${session.segmentCount} | ${ProbeLabels.recordingMethod(metadata.recordingMethod)} | " +
                    "${ProbeLabels.deviceType(metadata.deviceType)} |"
            )
        }
    }

    private fun StringBuilder.appendSteps(input: SessionProbeInput) {
        val result = input.steps
        appendLine("### Steps (보조, G-HR을 대신하지 않음)")
        appendLine("- 읽기 결과: ${outcomeLine(result.outcome, result.pages, result.complete, result.failureType)}")
        val observation = result.items.firstOrNull() ?: return
        val origins = observation.aggregateOrigins.joinToString().ifEmpty { "없음" }
        appendLine("- aggregate COUNT_TOTAL: ${observation.aggregateTotal ?: "없음"} (기여 origin: $origins)")
        val rawTotal = observation.rawByOrigin.values.sumOf { it.steps }
        for ((origin, total) in observation.rawByOrigin.toSortedMap()) {
            appendLine("- raw record(구간과 겹치는 record 전체) $origin: record ${total.records}개, 걸음 ${total.steps}")
        }
        val aggregate = observation.aggregateTotal
        if (aggregate != null && aggregate > 0) {
            appendLine("- raw 합계 / aggregate: ${String.format(Locale.ROOT, "%.2f", rawTotal.toDouble() / aggregate)}")
        }
    }

    private fun StringBuilder.appendMaskedIds(ids: List<String>, label: String) {
        if (ids.isEmpty()) return
        val listed = ids.distinct().sorted()
        val shown = listed.take(MAX_LISTED_IDS).joinToString { "`$it`" }
        val more = if (listed.size > MAX_LISTED_IDS) " 외 ${listed.size - MAX_LISTED_IDS}개" else ""
        appendLine("- $label(최대 ${MAX_LISTED_IDS}개): $shown$more")
    }

    private fun ReadOutcome.isReadable(): Boolean =
        this == ReadOutcome.SUCCESS || this == ReadOutcome.EMPTY_SUCCESS

    private fun outcomeLine(outcome: ReadOutcome, pages: Int, complete: Boolean?, failureType: String?): String {
        val details = mutableListOf("pages=$pages")
        if (complete != null) details += "complete=$complete"
        if (failureType != null) details += "예외=$failureType"
        return "${outcome.name} (${details.joinToString()})"
    }

    private fun shortPermissions(permissions: Set<String>): String =
        permissions.map { it.removePrefix(PERMISSION_PREFIX) }.sorted().joinToString()

    private fun offset(origin: Instant, instant: Instant): String = signedHms(Duration.between(origin, instant))

    internal fun hms(duration: Duration): String {
        val total = duration.abs().seconds
        val sign = if (duration.isNegative) "-" else ""
        return String.format(Locale.ROOT, "%s%02d:%02d:%02d", sign, total / 3600, (total % 3600) / 60, total % 60)
    }

    internal fun signedHms(duration: Duration): String =
        if (duration.isNegative) hms(duration) else "+${hms(duration)}"

    private fun seconds(duration: Duration?): String =
        duration?.let { String.format(Locale.ROOT, "%.1f초", it.toMillis() / 1000.0) } ?: "-"

    private fun percent(ratio: Double?): String =
        ratio?.let { String.format(Locale.ROOT, "%.1f%%", it * 100) } ?: "-"
}
