package com.yeonsik.fitnessapp.probe.wearable

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import com.yeonsik.fitnessapp.probe.wearable.analysis.ObservedExerciseSession
import com.yeonsik.fitnessapp.probe.wearable.analysis.ProbeEnvironment
import com.yeonsik.fitnessapp.probe.wearable.analysis.ProbeIdMasker
import com.yeonsik.fitnessapp.probe.wearable.analysis.ProbeLabels
import com.yeonsik.fitnessapp.probe.wearable.analysis.ProbeReportBuilder
import com.yeonsik.fitnessapp.probe.wearable.analysis.ProbeStateStore
import com.yeonsik.fitnessapp.probe.wearable.analysis.ProbeStatistics
import com.yeonsik.fitnessapp.probe.wearable.analysis.ReadOutcome
import com.yeonsik.fitnessapp.probe.wearable.analysis.SessionProbeInput
import com.yeonsik.fitnessapp.probe.wearable.analysis.SessionWindow
import com.yeonsik.fitnessapp.probe.wearable.analysis.SessionWindowParser
import com.yeonsik.fitnessapp.probe.wearable.analysis.TimeInterval
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/**
 * PR0 feasibility probe for Galaxy Fit3 → Samsung Health → Health Connect (debug builds only).
 *
 * The screen reads Health Connect for a session window the tester typed in and shows an
 * anonymized report to paste into `docs/wearable/PR0_PROBE_RESULT.md`. It does not open the
 * Fitness database, does not write anywhere except its own no-backup state file and has no
 * network path. The report is copied to the clipboard only when the tester asks for it.
 */
class WearableProbeActivity : ComponentActivity() {
    private lateinit var reader: HealthConnectProbeReader
    private lateinit var store: ProbeStateStore
    private lateinit var masker: ProbeIdMasker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        reader = HealthConnectProbeReader(applicationContext)
        store = ProbeStateStore(File(noBackupFilesDir, "wearable-probe/state.properties"))
        masker = ProbeIdMasker(store.saltOrCreate())
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ProbeScreen(
                        reader = reader,
                        store = store,
                        masker = masker,
                        environment = ::environment,
                        onCopy = ::copyReport,
                        onOpenHealthConnect = ::openHealthConnect,
                    )
                }
            }
        }
    }

    private fun environment(granted: Set<String>): ProbeEnvironment = ProbeEnvironment(
        androidSdkInt = Build.VERSION.SDK_INT,
        phoneManufacturer = Build.MANUFACTURER,
        phoneModel = Build.MODEL,
        healthConnectStatus = reader.availabilityOutcome(),
        packageVersions = reader.packageVersions(),
        grantedPermissions = granted,
        requestedPermissions = reader.readPermissions,
    )

    private fun copyReport(text: String) {
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        val clip = ClipData.newPlainText("wearable-probe-report", text)
        // Ask the system not to preview the copied report (honored on Android 13 and later).
        clip.description.extras = PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "익명화 보고서를 복사했습니다.", Toast.LENGTH_SHORT).show()
    }

    private fun openHealthConnect() {
        try {
            startActivity(HealthConnectClient.getHealthConnectManageDataIntent(this))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Health Connect 화면을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun ProbeScreen(
    reader: HealthConnectProbeReader,
    store: ProbeStateStore,
    masker: ProbeIdMasker,
    environment: (Set<String>) -> ProbeEnvironment,
    onCopy: (String) -> Unit,
    onOpenHealthConnect: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val zone = remember { ZoneId.systemDefault() }
    var availability by remember { mutableStateOf(reader.availabilityOutcome()) }
    var granted by remember { mutableStateOf<Set<String>>(emptySet()) }
    var label by rememberSaveable { mutableStateOf("S1") }
    var date by rememberSaveable { mutableStateOf(LocalDate.now(zone).toString()) }
    var start by rememberSaveable { mutableStateOf("") }
    var end by rememberSaveable { mutableStateOf("") }
    var pauses by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var recentSessions by remember { mutableStateOf<List<ObservedExerciseSession>>(emptyList()) }
    var sections by remember { mutableStateOf<List<String>>(emptyList()) }

    suspend fun refreshPermissions() {
        availability = reader.availabilityOutcome()
        granted = reader.grantedPermissions()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) {
        scope.launch { refreshPermissions() }
    }

    LaunchedEffect(Unit) { refreshPermissions() }

    fun runBusy(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        message = null
        scope.launch {
            try {
                action()
            } finally {
                busy = false
            }
        }
    }

    fun querySession() {
        val parsed = SessionWindowParser.parse(date, start, end, pauses, zone)
        if (parsed is SessionWindowParser.Result.Invalid) {
            message = parsed.reason
            return
        }
        val window = (parsed as SessionWindowParser.Result.Parsed).window
        runBusy {
            val queriedAt = Instant.now()
            val interval = TimeInterval(window.start, window.end)
            val heartRate = reader.readHeartRate(interval, masker)
            val exercise = reader.readExerciseSessions(interval, masker)
            val steps = reader.readSteps(interval)
            val windowKey = windowKey(masker, window)
            val input = SessionProbeInput(
                label = label.trim().ifEmpty { "세션" },
                window = window,
                zone = zone,
                queriedAt = queriedAt,
                heartRate = heartRate,
                exerciseSessions = exercise,
                steps = steps,
                previous = store.previousQuery(windowKey),
            )
            sections = sections + ProbeReportBuilder.session(input)
            val readable = heartRate.outcome == ReadOutcome.SUCCESS || heartRate.outcome == ReadOutcome.EMPTY_SUCCESS
            if (readable && heartRate.complete) {
                val samples = ProbeStatistics.compute(window, heartRate.items.flatMap { it.sampleTimes }).samplesInWindow
                store.recordQuery(
                    windowKey = windowKey,
                    queriedAt = queriedAt,
                    maskedIds = heartRate.items.mapNotNull { it.metadata.maskedId }.toSet(),
                    sampleCount = samples,
                )
            }
            message = "세션 ${input.label} 조회: HeartRate ${heartRate.outcome}"
        }
    }

    fun loadRecentSessions() = runBusy {
        val now = Instant.now()
        val result = reader.readExerciseSessions(TimeInterval(now.minus(Duration.ofDays(7)), now), masker)
        recentSessions = result.items.sortedByDescending { it.startAt }
        message = "최근 7일 ExerciseSession: ${result.outcome} ${result.items.size}개"
    }

    fun issueChangesToken() = runBusy {
        val result = reader.issueHeartRateChangesToken()
        val token = result.items.firstOrNull()
        if (token != null) {
            store.saveChangesToken(token, Instant.now())
            message = "HeartRate changes token을 발급했습니다."
        } else {
            message = "changes token 발급 실패: ${result.outcome}"
        }
    }

    fun readChanges() = runBusy {
        val stored = store.changesToken()
        if (stored == null) {
            message = "먼저 changes token을 발급하세요."
            return@runBusy
        }
        val changes = reader.readHeartRateChanges(stored.token, stored.issuedAt, masker, store.lastQueryMaskedIds())
        when {
            changes.observation.tokenExpired -> store.clearChangesToken()
            changes.nextToken != null -> store.advanceChangesToken(changes.nextToken)
        }
        sections = sections + ProbeReportBuilder.changes(changes.observation)
        message = "HeartRate changes: ${changes.observation.outcome}"
    }

    fun fullReport(): String = buildString {
        appendLine("# Wearable PR0 probe report (debug, 익명화)")
        appendLine()
        append(ProbeReportBuilder.environment(environment(granted)))
        for (section in sections) {
            appendLine()
            append(section)
        }
    }

    // Built only when its inputs change: it queries PackageManager and Health Connect status.
    val reportText = remember(sections, granted, availability) {
        if (sections.isEmpty()) "" else fullReport()
    }
    val missing = reader.readPermissions - granted
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Wearable PR0 Probe (debug)", style = MaterialTheme.typography.titleLarge)
        Text(
            "Health Connect를 읽기만 합니다. Fitness 기록 DB를 열지 않고, 네트워크로 보내지 않으며, " +
                "심박 값과 원본 record id는 화면·저장소·보고서에 남기지 않습니다.",
            style = MaterialTheme.typography.bodySmall,
        )

        HorizontalDivider()
        Text("Health Connect: ${ProbeLabels.outcome(availability)}")
        Text(
            if (missing.isEmpty()) "권한: 모두 허용" else "권한 미허용: ${missing.joinToString { it.substringAfterLast('.') }}",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = availability == null && !busy, onClick = { permissionLauncher.launch(reader.readPermissions) }) {
                Text("권한 요청")
            }
            OutlinedButton(enabled = !busy, onClick = { runBusy { refreshPermissions() } }) { Text("새로고침") }
            OutlinedButton(onClick = onOpenHealthConnect) { Text("HC 열기") }
        }

        HorizontalDivider()
        Text("세션 구간 (수동 기록한 wall-clock, ${zone.id})", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("라벨 (예: S1)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("날짜 yyyy-MM-dd") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = start, onValueChange = { start = it }, label = { Text("시작 HH:mm[:ss]") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(value = end, onValueChange = { end = it }, label = { Text("종료 HH:mm[:ss]") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        OutlinedTextField(value = pauses, onValueChange = { pauses = it }, label = { Text("일시정지 HH:mm-HH:mm, …") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !busy, onClick = { querySession() }) { Text("세션 조회") }
            OutlinedButton(enabled = !busy, onClick = { loadRecentSessions() }) { Text("최근 7일 운동") }
        }
        if (recentSessions.isNotEmpty()) {
            Text("ExerciseSession을 누르면 날짜·시작·종료를 채웁니다. 일시정지는 직접 입력합니다.", style = MaterialTheme.typography.bodySmall)
            for (session in recentSessions.take(15)) {
                val startLocal = session.startAt.atZone(zone)
                val endLocal = session.endAt.atZone(zone)
                Text(
                    "${startLocal.format(DISPLAY)} ~ ${endLocal.format(TIME)} · type ${session.exerciseType} · " +
                        session.metadata.originPackage,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            date = startLocal.toLocalDate().toString()
                            start = startLocal.format(TIME)
                            end = endLocal.format(TIME)
                        }
                        .padding(vertical = 4.dp),
                )
            }
        }

        HorizontalDivider()
        Text("HeartRate changes feed", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !busy, onClick = { issueChangesToken() }) { Text("token 발급") }
            OutlinedButton(enabled = !busy, onClick = { readChanges() }) { Text("changes 조회") }
        }

        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

        HorizontalDivider()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = sections.isNotEmpty(), onClick = { onCopy(reportText) }) { Text("보고서 복사") }
            OutlinedButton(enabled = sections.isNotEmpty(), onClick = { sections = emptyList() }) { Text("보고서 비우기") }
        }
        if (reportText.isNotEmpty()) {
            SelectionContainer {
                Text(reportText, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun windowKey(masker: ProbeIdMasker, window: SessionWindow): String =
    masker.mask("window|${window.start.toEpochMilli()}|${window.end.toEpochMilli()}")

private val DISPLAY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
