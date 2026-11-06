package com.yeonsik.fitnessapp.integration.healthtrace

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yeonsik.fitnessapp.config.SupabaseConfigStore
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabaseProvider
import com.yeonsik.fitnessapp.core.ui.*
import com.yeonsik.fitnessapp.feature.information.data.InformationProfile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

private const val HEALTH_PACKAGE = "com.yeonsik.health.health_observation_app"

fun openHealthTrace(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(HEALTH_PACKAGE)
    if (launch == null) Toast.makeText(context, "HealthTrace를 설치한 뒤 새 건강 기록을 입력하세요. 이전 자료는 파일로 내보낼 수 있습니다.", Toast.LENGTH_LONG).show()
    else try { context.startActivity(launch) } catch (_: Exception) {
        Toast.makeText(context, "HealthTrace를 열지 못했습니다. 앱에서 직접 열어 주세요.", Toast.LENGTH_LONG).show()
    }
}

@Composable
fun HealthOwnershipActions() {
    val context = LocalContext.current
    val theme = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) "dark" else "light"
    Text("새 수면·복용·건강 정보는 HealthTrace에서 기록합니다. Fitness의 이전 건강 기록은 읽기 전용으로 보존됩니다.", style = MaterialTheme.typography.bodyMedium)
    AppButton(onClick = { openHealthTrace(context) }, modifier = Modifier.fillMaxWidth()) { Text("HealthTrace에서 기록") }
    AppOutlinedButton(onClick = { context.startActivity(Intent(context, HealthHandoffActivity::class.java).putExtra("theme", theme)) },
        modifier = Modifier.fillMaxWidth()) { Text("이전 건강 자료 내보내기") }
}

data class HealthHandoffUiState(
    val rows: List<LegacyHealthRow> = emptyList(),
    val selected: LegacyHealthRow? = null,
    val busy: Boolean = false,
    val error: String = "",
    val message: String = "",
    val exportName: String? = null
)

class HealthHandoffViewModel(application: Application) : AndroidViewModel(application) {
    private val config = SupabaseConfigStore(application)
    private fun profile() = config.load().let { InformationProfile(it.effectiveUserId(), if (it.userId.isBlank()) "local" else it.supabaseUrl) }
    private val owner = profile()
    private val repository = LegacyHealthExportRepository(FitnessRoomDatabaseProvider.get(application), owner)
    private val mutableState = MutableStateFlow(HealthHandoffUiState())
    val state = mutableState.asStateFlow()
    private var pendingPayload: String? = null
    init { work { val rows = repository.rows(); withContext(Dispatchers.Main) { mutableState.value = mutableState.value.copy(rows = rows) } } }
    private fun work(action: suspend () -> Unit) {
        if (mutableState.value.busy) return
        mutableState.value = mutableState.value.copy(busy = true, error = "", message = "")
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) {
                check(owner == profile()) { "계정이 변경되었습니다. 화면을 닫고 다시 여세요." }
                action()
            } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutableState.value = mutableState.value.copy(error = e.message ?: "자료를 처리하지 못했습니다.") }
            finally { mutableState.value = mutableState.value.copy(busy = false) }
        }
    }
    fun select(row: LegacyHealthRow?) { mutableState.value = mutableState.value.copy(selected = row) }
    fun prepareExport() {
        val selection = mutableState.value.selected
        work {
            val record = repository.export(selection)
            // Canonical JSON stays within the wire byte limit even near 1 MiB.
            pendingPayload = record.canonical
            withContext(Dispatchers.Main) { mutableState.value = mutableState.value.copy(exportName = "healthtrace-legacy-${record.id}.json") }
        }
    }
    fun pickerOpened() { mutableState.value = mutableState.value.copy(exportName = null) }
    fun write(uri: Uri?) {
        if (uri == null) { pendingPayload = null; return }
        work {
            val text = pendingPayload ?: error("내보낼 자료를 다시 선택하세요.")
            getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                ?: error("파일을 저장할 수 없습니다.")
            pendingPayload = null
            withContext(Dispatchers.Main) { mutableState.value = mutableState.value.copy(message = "파일을 저장했습니다. HealthTrace의 정보 검토함에서 가져오세요. Fitness 원본은 유지됩니다.") }
        }
    }
}

class HealthHandoffActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val model = ViewModelProvider(this)[HealthHandoffViewModel::class.java]
        setContent {
            val mode = intent.getStringExtra("theme")
            FitnessComposeTheme(if (mode == "dark") true else if (mode == "light") false else isSystemInDarkTheme()) {
                HealthHandoffScreen(model, ::finish)
            }
        }
    }
}

@Composable
private fun HealthHandoffScreen(model: HealthHandoffViewModel, close: () -> Unit) {
    val ui by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json"), model::write)
    LaunchedEffect(ui.exportName) { ui.exportName?.let { exporter.launch(it); model.pickerOpened() } }
    val back: () -> Unit = { if (ui.selected == null) close() else model.select(null) }
    BackHandler(onBack = back)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp), contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                AppOutlinedButton(onClick = back, enabled = !ui.busy) { Text("돌아가기") }
                Text("이전 건강 자료", style = MaterialTheme.typography.headlineMedium)
                Text("보충제·복용·수면 등 Fitness에 남은 건강 자료를 HealthTrace 검토함으로 옮길 파일을 만듭니다. 원본을 삭제하거나 의료·복용 집계로 자동 반영하지 않습니다.")
                Text("수분 섭취와 운동 준비도는 Fitness에 남습니다. 파일에 실제 과거 시각이 없는 경우 관찰 시각을 새로 만들지 않습니다.", style = MaterialTheme.typography.bodySmall)
            }
            if (ui.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("이전 자료를 불러오고 있습니다.") }
            if (ui.error.isNotEmpty()) item { Text(ui.error, color = MaterialTheme.colorScheme.error) }
            if (ui.message.isNotEmpty()) item { Text(ui.message) }
            item {
                AppButton(onClick = model::prepareExport, enabled = !ui.busy && ui.rows.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                    Text(if (ui.selected == null) "전체 ${ui.rows.size}건 파일로 내보내기" else "선택한 기록 내보내기")
                }
                AppOutlinedButton(onClick = { openHealthTrace(context) }, modifier = Modifier.fillMaxWidth()) { Text("HealthTrace 열기") }
                Text("전체 파일은 1 MiB까지 지원합니다. 크기를 넘으면 아래에서 개별 기록을 선택하세요.", style = MaterialTheme.typography.bodySmall)
            }
            val selected = ui.selected
            if (selected != null) item {
                Text(selected.label, style = MaterialTheme.typography.titleMedium)
                SelectionContainer { Text(selected.payload.toString().take(16000), style = MaterialTheme.typography.bodySmall) }
            } else {
                if (ui.rows.isEmpty() && !ui.busy) item { Text("내보낼 이전 건강 기록이 없습니다. 새 기록은 HealthTrace에서 시작하세요.") }
                items(ui.rows, key = { it.table + ":" + it.id }) { row ->
                    Column(Modifier.fillMaxWidth().clickable(enabled = !ui.busy) { model.select(row) }.padding(vertical = 12.dp)) {
                        Text(row.label, style = MaterialTheme.typography.titleMedium)
                        Text(row.payload["date"]?.toString()?.trim('"') ?: row.id, style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
