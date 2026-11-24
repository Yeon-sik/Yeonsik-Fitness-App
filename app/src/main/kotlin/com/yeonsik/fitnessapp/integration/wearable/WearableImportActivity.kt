package com.yeonsik.fitnessapp.integration.wearable

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.*
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.health.connect.client.PermissionController
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yeonsik.fitness.shared.feature.dailyworkout.DailyHeartRateSample
import com.yeonsik.fitness.shared.feature.dailyworkout.WorkoutHeartRatePlot
import com.yeonsik.fitnessapp.core.ui.FitnessComposeTheme
import java.time.*
import java.time.format.DateTimeFormatter

class WearableImportActivity : ComponentActivity() {
    private lateinit var model: WearableImportViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        model = ViewModelProvider(this)[WearableImportViewModel::class.java]
        model.enter(intent.getStringExtra("owner"), intent.getStringExtra("record"), intent.getStringExtra("date"))
        setContent {
            FitnessComposeTheme(dark = isSystemInDarkTheme()) {
                val ui by model.state.collectAsStateWithLifecycle()
                val permission = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract(), model::permissionResult)
                WearableImportContent(ui, model::date, { if (model.reader.available()) permission.launch(model.reader.permissions) else model.unavailable() }, model::query,
                    model::select, model::saveSelected, ::finish)
            }
        }
    }
    override fun onResume() { super.onResume(); if (::model.isInitialized) model.resume() }
}

/** Route wrapper owns Android navigation; the entry itself never opens a database. */
@Composable
fun WearableImportEntry(ownerId: String, recordId: String? = null, date: String? = null) {
    WearableImportAction(ownerId, recordId, date) { open ->
        OutlinedButton(onClick = open, modifier = Modifier.fillMaxWidth()) {
            Text(if (recordId == null) "밴드 운동·심박 가져오기" else "이 운동의 밴드 기록 가져오기·확인")
        }
    }
}

@Composable
internal fun WearableImportAction(ownerId: String, recordId: String?, date: String?, content: @Composable (() -> Unit) -> Unit) {
    val context = LocalContext.current
    content {
        context.startActivity(Intent(context, WearableImportActivity::class.java)
            .putExtra("owner", ownerId).putExtra("record", recordId).putExtra("date", date))
    }
}

@Composable
internal fun WearableImportContent(ui: WearableImportState, date: (String) -> Unit, permission: () -> Unit,
    query: () -> Unit, select: (WearableWorkout?) -> Unit, save: () -> Unit, close: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
            item {
                TextButton(onClick = close) { Text("운동으로 돌아가기") }
                Text("밴드 운동·심박", style = MaterialTheme.typography.headlineMedium)
                Text(if (ui.target == null) "Health Connect에 도착한 내 운동을 조회하고 보관합니다."
                    else "선택한 측정 기록을 현재 Fitness 운동에 연결합니다. 중량·횟수·세트는 유지됩니다.")
                Text("실시간 심박이 아닙니다. 밴드 기록이 동기화된 뒤 조회하세요. 기본 읽기 범위는 권한 허용 전 30일부터입니다.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
            if (ui.accountValid) item {
                OutlinedTextField(ui.date, date, label = { Text("운동 날짜 (yyyy-MM-dd)") },
                    enabled = !ui.busy, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(permission, enabled = !ui.busy) { Text("읽기 권한") }
                    Button(query, enabled = !ui.busy) { Text("조회·새로고침") }
                }
            }
            if (ui.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (ui.message.isNotBlank()) item { Text(ui.message) }
            if (ui.accountValid && ui.candidates.isNotEmpty()) item { Text("가져올 운동", style = MaterialTheme.typography.titleLarge) }
            items(if (ui.accountValid) ui.candidates else emptyList(), key = { "candidate:${it.origin}:${it.providerId}" }) { row ->
                WearableWorkoutCard(row) {
                    Button(onClick = { select(row) }, enabled = !ui.busy && ui.accountValid) {
                        Text(if (ui.target == null) "이 기록 가져오기" else "이 운동에 연결")
                    }
                }
            }
            item { Text(if (ui.target == null) "가져온 운동" else "이 운동에 연결된 측정 기록", style = MaterialTheme.typography.titleLarge) }
            val visibleSaved = if (ui.accountValid) ui.saved.filter { ui.target == null || it.linkedRecordId == ui.target } else emptyList()
            if (visibleSaved.isEmpty()) item { Text("아직 저장한 측정 기록이 없습니다.") }
            items(visibleSaved, key = { "saved:${it.origin}:${it.providerId}" }) { row ->
                WearableWorkoutCard(row) {
                    Text("Fitness에 저장됨" + if (row.linkedRecordId != null) " · 운동 연결됨" else "")
                    Text("가져온 시각: ${row.importedAt?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    ui.selected?.let { row ->
        AlertDialog(onDismissRequest = { if (!ui.busy) select(null) }, title = { Text("내 운동 기록 가져오기") },
            text = { Text("${row.title} · 실제 심박 ${row.samples.size}개\n이 기기의 내 기록을 현재 Fitness 계정에 저장합니다." +
                if (ui.target != null) "\n현재 운동에 연결하며 기존 운동 값은 유지합니다." else "") },
            confirmButton = { TextButton(save, enabled = !ui.busy) { Text("확인하고 저장") } },
            dismissButton = { TextButton({ select(null) }, enabled = !ui.busy) { Text("취소") } })
    }
}

@Composable
private fun WearableWorkoutCard(row: WearableWorkout, actions: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(row.title, style = MaterialTheme.typography.titleMedium)
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            Text(row.start.atZone(ZoneId.systemDefault()).format(formatter))
            val seconds = Duration.between(row.start, row.end).seconds
            Text("운동 ${seconds / 60}분 ${seconds % 60}초 · 심박 ${row.samples.size}개")
            if (row.samples.isNotEmpty()) {
                Text("측정 표본 평균 ${"%.1f".format(row.samples.map { it.bpm }.average())} · 최대 ${row.samples.maxOf { it.bpm }} bpm")
                val samples = remember(row) { row.samples.map { DailyHeartRateSample(row.origin, it.time.toEpochMilli(), it.bpm.toDouble()) } }
                WorkoutHeartRatePlot(samples, row.start.toEpochMilli(), row.end.toEpochMilli(), Modifier.fillMaxWidth())
            } else Text("이 출처에서 같은 구간의 심박은 조회되지 않았습니다.")
            Text("출처: ${row.origin}", style = MaterialTheme.typography.bodySmall)
            actions()
        }
    }
}
