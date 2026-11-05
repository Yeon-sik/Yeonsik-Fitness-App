package com.yeonsik.fitnessapp.probe.wearable.analysis

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProbeReportBuilderTest {
    private val masker = ProbeIdMasker(ByteArray(32) { 7 })
    private val rawId = "f2c1d7aa-raw-hc-record-id"
    private val start = Instant.parse("2026-11-04T22:30:00Z")
    private val window = SessionWindow(
        start = start,
        end = start.plus(Duration.ofMinutes(30)),
        pauses = listOf(TimeInterval(start.plus(Duration.ofMinutes(10)), start.plus(Duration.ofMinutes(12)))),
    )

    private fun metadata(origin: String = "com.sec.android.app.shealth") = ObservedMetadata(
        maskedId = masker.mask(rawId),
        originPackage = origin,
        recordingMethod = 1,
        deviceType = 6,
        deviceManufacturer = "Samsung",
        deviceModel = "SM-R390",
        hasClientRecordId = true,
        clientRecordVersion = 3,
        lastModifiedAt = window.end.plus(Duration.ofMinutes(4)),
    )

    private fun input(
        heartRate: ReadResult<ObservedHeartRateRecord>,
        previous: PreviousQuery? = null,
    ) = SessionProbeInput(
        label = "S1",
        window = window,
        zone = ZoneId.of("Asia/Seoul"),
        queriedAt = window.end.plus(Duration.ofMinutes(20)),
        heartRate = heartRate,
        exerciseSessions = ReadResult.success(
            listOf(ObservedExerciseSession(metadata(), window.start, window.end, exerciseType = 79, segmentCount = 0)),
            pages = 1,
            complete = true,
        ),
        steps = ReadResult.success(
            listOf(StepsObservation(1200, listOf("com.sec.android.app.shealth"), mapOf("com.sec.android.app.shealth" to RawStepsTotal(3, 1300)))),
            pages = 1,
            complete = true,
        ),
        previous = previous,
    )

    private fun heartRateRecords(): ReadResult<ObservedHeartRateRecord> {
        val samples = (0L..1800L step 5).map { window.start.plusSeconds(it) }
        return ReadResult.success(
            listOf(ObservedHeartRateRecord(metadata(), window.start, window.end, samples)),
            pages = 1,
            complete = true,
        )
    }

    @Test
    fun reportUsesRelativeTimesAndMaskedIdsOnly() {
        val report = ProbeReportBuilder.session(input(heartRateRecords()))
        assertFalse(report.contains(rawId))
        assertFalse(report.contains("2026-11-0"))
        assertFalse(report.contains("22:30"))
        assertTrue(report.contains(masker.mask(rawId)))
        assertTrue(report.contains("세션 길이 00:30:00, 활동 00:28:00, 일시정지 1회 (+00:10:00~+00:12:00)"))
        assertTrue(report.contains("조회 시점: 세션 종료 후 +00:20:00"))
        assertTrue(report.contains("동기화 지연(lastModifiedTime − 세션 종료): 최소 +00:04:00, 최대 +00:04:00"))
        assertTrue(report.contains("FITNESS_BAND / Samsung / SM-R390 1"))
        assertTrue(report.contains("ACTIVELY_RECORDED 1"))
        assertTrue(report.contains("판정 보조값이며 PR0 판정이 아님"))
        assertTrue(report.contains("잠정 K-12 기준(coverage ≥ 80%, 최대 공백 ≤ 5분): MET"))
        assertTrue(report.contains("G-HR을 대신하지 않음"))
    }

    @Test
    fun outcomesStayDistinctAndUnreadableResultsShowNoStatistics() {
        val denied = ProbeReportBuilder.session(
            input(ReadResult(ReadOutcome.PERMISSION_DENIED, failureType = "SecurityException"))
        )
        assertTrue(denied.contains("PERMISSION_DENIED (pages=0, complete=false, 예외=SecurityException)"))
        assertFalse(denied.contains("coverage:"))

        val empty = ProbeReportBuilder.session(input(ReadResult.success(emptyList(), pages = 1, complete = true)))
        assertTrue(empty.contains("EMPTY_SUCCESS"))
        assertTrue(empty.contains("잠정 K-12 기준(coverage ≥ 80%, 최대 공백 ≤ 5분): NO_DATA"))
    }

    @Test
    fun requeryComparisonCountsKeptAppearedAndDisappearedMasks() {
        val previous = PreviousQuery(
            queriedAt = window.end.plus(Duration.ofMinutes(5)),
            maskedIds = setOf(masker.mask(rawId), masker.mask("gone")),
            sampleCount = 300,
        )
        val report = ProbeReportBuilder.session(input(heartRateRecords(), previous))
        assertTrue(report.contains("유지 1, 새로 보임 0, 사라짐 1"))
        assertTrue(report.contains("세션 구간 sample 이전 300 → 현재 361"))
        assertFalse(report.contains("gone"))
    }

    @Test
    fun environmentAndChangesSectionsCarryNoRawIdentifiers() {
        val environment = ProbeReportBuilder.environment(
            ProbeEnvironment(
                androidSdkInt = 35,
                phoneManufacturer = "samsung",
                phoneModel = "SM-A256N",
                healthConnectStatus = null,
                packageVersions = mapOf("com.sec.android.app.shealth" to "6.30", "com.google.android.apps.healthdata" to null),
                grantedPermissions = setOf("android.permission.health.READ_HEART_RATE"),
                requestedPermissions = setOf("android.permission.health.READ_HEART_RATE", "android.permission.health.READ_STEPS"),
            )
        )
        assertTrue(environment.contains("Health Connect 상태: AVAILABLE"))
        assertTrue(environment.contains("허용된 권한: READ_HEART_RATE"))
        assertTrue(environment.contains("com.google.android.apps.healthdata: 설치 안 됨 또는 확인 불가"))

        val changes = ProbeReportBuilder.changes(
            ChangesObservation(
                outcome = ReadOutcome.SUCCESS,
                issuedAt = window.end,
                queriedAt = window.end.plus(Duration.ofHours(25)),
                tokenExpired = false,
                upsertions = 4,
                deletionMaskedIds = listOf(masker.mask(rawId)),
                deletionsMatchingLastQuery = 1,
                pages = 1,
                hasMoreRemaining = false,
                failureType = null,
            )
        )
        assertTrue(changes.contains("token 발급 후 경과: 25:00:00"))
        assertTrue(changes.contains("DeletionChange: 1 (마지막 완전 조회의 masked id와 일치 1)"))
        assertFalse(changes.contains(rawId))
    }
}
