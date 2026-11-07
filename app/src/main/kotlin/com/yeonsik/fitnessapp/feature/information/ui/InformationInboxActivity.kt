package com.yeonsik.fitnessapp.feature.information.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.core.ui.FitnessComposeTheme
import com.yeonsik.fitnessapp.core.ui.AppButton
import com.yeonsik.fitnessapp.core.ui.AppOutlinedButton
import com.yeonsik.fitnessapp.feature.information.data.InformationReviewState
import com.yeonsik.fitnessapp.feature.information.model.*
import kotlinx.serialization.json.*

class InformationInboxActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val model = ViewModelProvider(this)[InformationInboxViewModel::class.java]
        setContent {
            val mode = intent.getStringExtra("theme")
            FitnessComposeTheme(dark = if (mode == "dark") true else if (mode == "light") false else isSystemInDarkTheme()) {
                InformationInboxScreen(model, ::finish)
            }
        }
    }
}

@Composable
private fun InformationInboxScreen(model: InformationInboxViewModel, close: () -> Unit) {
    val ui by model.state.collectAsStateWithLifecycle()
    var confirmLeave by remember { mutableStateOf(false) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::readFile) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(model::exportFile) }
    val back: () -> Unit = {
        when { ui.editor != null -> confirmLeave = true; ui.projectionDraft != null -> model.closeProjection(); ui.selected != null -> model.select(null); else -> close() }
    }
    BackHandler(onBack = back)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 20.dp),
            contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                TextButton(onClick = back, enabled = !ui.busy, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) { Text("돌아가기") }
                Text("활동 정보 검토함", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                Text("운동·식사·체중과 관련된 자료를 보관합니다. 결제, 상품 정보, 수면·복용 기록은 해당 앱의 참조로 연결하세요.",
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                Text("이곳의 자료는 비공개입니다. 실제 기록과 통계에는 반영 내용을 확인하고 승인한 자료만 추가합니다.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
            if (ui.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("기록을 처리하고 있습니다.") }
            if (ui.error.isNotBlank()) item { Text(ui.error, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            if (ui.message.isNotBlank()) item { Text(ui.message, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            val editor = ui.editor
            val selected = ui.selected
            val projection = ui.projectionDraft
            when {
                editor != null -> {
                    item {
                        Text("기록 초안", style = MaterialTheme.typography.titleLarge)
                        Text("title은 제목, fields는 항목입니다. text·decimal·boolean·date·datetime·json 값을 사용할 수 있습니다. 숫자는 문자열로 입력하면 정밀도를 보존합니다.",
                            style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(value = editor, onValueChange = model::edit, label = { Text("구조화된 기록 JSON") },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp), minLines = 12, maxLines = 22,
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), enabled = !ui.busy)
                    }
                    item {
                        Row(Modifier.fillMaxWidth().clickable(enabled = !ui.busy) { model.ownerReviewed(!ui.ownerReviewed) }) {
                            Checkbox(ui.ownerReviewed, onCheckedChange = model::ownerReviewed, enabled = !ui.busy)
                            Text("이 자료가 나의 활동·운동·식사·체중 정보임을 확인했습니다.", Modifier.weight(1f).padding(top = 12.dp))
                        }
                        AppButton(onClick = model::importEditor, enabled = !ui.busy && ui.ownerReviewed, modifier = Modifier.fillMaxWidth()) { Text("검토 대기에 저장") }
                        AppOutlinedButton(onClick = model::saveDraft, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) { Text("초안 보관") }
                    }
                }
                projection != null -> {
                    item {
                        Text("${projection.kind.label} 반영 내용 확인", style = MaterialTheme.typography.titleLarge)
                        Text("원문: ${selected?.title.orEmpty()}")
                        Text("원문은 그대로 보존합니다. 아래 값은 실제 기록에 넣을 내용입니다. 모르는 값은 0으로 채우지 말고 원문을 확인하세요. 시각은 기기 시간대(${java.time.ZoneId.systemDefault().id}) 기준입니다.")
                        if (projection.kind == InformationProjectionKind.MEAL) Text("한 끼 전체를 한 항목으로 반영합니다. 여러 음식의 세부 구성은 원문에 남습니다. 칼로리와 3대 영양값이 모두 있어야 합니다.")
                        if (projection.kind == InformationProjectionKind.WORKOUT) {
                            Text("한 종목의 완료 기록을 만듭니다. 중량 운동은 모든 세트가 같은 구성일 때 지원합니다.")
                            AppOutlinedButton(onClick = { model.editProjection("mode", "timed") }, enabled = !ui.busy) { Text("시간 기록${if (projection.values["mode"] == "timed") " · 선택됨" else ""}") }
                            AppOutlinedButton(onClick = { model.editProjection("mode", "resistance") }, enabled = !ui.busy) { Text("중량·횟수 기록${if (projection.values["mode"] == "resistance") " · 선택됨" else ""}") }
                        }
                    }
                    items(InformationProjection.fields(projection.kind, projection.values), key = { it.key }) { field ->
                        OutlinedTextField(value = projection.values[field.key].orEmpty(), onValueChange = { model.editProjection(field.key, it) },
                            label = { Text(field.label + if (field.optional) " · 선택" else " · 필수") },
                            modifier = Modifier.fillMaxWidth(), enabled = !ui.busy, singleLine = field.key != "memo")
                    }
                    item {
                        val preview = ui.projectionPreview
                        if (preview == null) AppButton(onClick = model::previewProjection, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) { Text("반영 미리보기") }
                        else {
                            Text("실제 기록에 추가할 내용", style = MaterialTheme.typography.titleMedium)
                            Text(preview.summary, modifier = Modifier.padding(vertical = 12.dp))
                            Text("승인하면 기록과 통계가 바뀝니다. 이후 수정·삭제는 실제 기록 화면에서 하세요. 원문 보관 상태를 바꿔도 실제 기록은 되돌아가지 않습니다.", style = MaterialTheme.typography.bodySmall)
                            AppButton(onClick = model::applyProjection, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) { Text("확인한 내용으로 실제 기록에 반영") }
                        }
                        AppOutlinedButton(onClick = model::closeProjection, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) { Text("원문으로 돌아가기") }
                    }
                }
                selected != null -> {
                    item {
                        Text(selected.title, style = MaterialTheme.typography.titleLarge)
                        Text(InformationReviewState.entries.find { it.wire == selected.status }?.label ?: "상태 확인 필요")
                        Text("출처와 신뢰도는 제출된 주장입니다. 검토 상태는 사실 검증이나 원래 기록 반영을 뜻하지 않습니다.", style = MaterialTheme.typography.bodySmall)
                        ui.application?.let { Text("실제 기록 반영 완료 · ${it.appliedAt.take(10)}\n수정·삭제는 체중·식사·운동의 실제 기록 화면에서 진행하세요.", style = MaterialTheme.typography.titleSmall) }
                    }
                    val record = runCatching { InformationRecordCodec.decode(selected.payloadJson, InformationOwner.FITNESS) }.getOrNull()
                    if (record != null) {
                        items(record.payload.getValue("fields").jsonArray) { field ->
                            val value = field.jsonObject
                            Column {
                                Text(value["label"]?.jsonPrimitive?.content ?: value.getValue("key").jsonPrimitive.content, style = MaterialTheme.typography.titleSmall)
                                val fullValue = value.getValue("value").let { if (it is JsonPrimitive && it.isString) it.content else it.toString() } +
                                    (value["unit"]?.jsonPrimitive?.content?.let { " $it" } ?: "")
                                SelectionContainer { Text(fullValue.take(4000)) }
                                if (fullValue.length > 4000) Text("긴 값은 앞부분만 표시합니다. 전체 내용은 JSON 파일로 내보내세요.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        item {
                            Text("원문·출처·참조", style = MaterialTheme.typography.titleMedium)
                            val fullSource = InformationRecordCodec.pretty(record)
                            SelectionContainer { Text(fullSource.take(16000), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)) }
                            if (fullSource.length > 16000) Text("원문이 길어 앞부분만 표시합니다. 내보낸 JSON에는 전체 원문이 보존됩니다.")
                        }
                    } else item { Text("기록 형식이 올바르지 않습니다. 백업 원본을 확인하세요.", color = MaterialTheme.colorScheme.error) }
                    item {
                        if (ui.application == null && record != null) {
                            Text("실제 기록으로 반영", style = MaterialTheme.typography.titleMedium)
                            Text("자료 종류를 선택하고 빠진 값을 보완한 뒤 승인하세요. 지원하지 않는 자료는 원문으로 계속 보관할 수 있습니다.", style = MaterialTheme.typography.bodySmall)
                            InformationProjectionKind.entries.forEach { kind ->
                                AppButton(onClick = { model.startProjection(kind) }, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) { Text("${kind.label} 반영 검토") }
                            }
                        }
                        InformationReviewState.entries.forEach { state ->
                            AppOutlinedButton(onClick = { model.changeState(state) }, enabled = !ui.busy && selected.status != state.wire,
                                modifier = Modifier.fillMaxWidth()) { Text("${state.label} 상태로 변경") }
                        }
                        AppButton(onClick = { model.prepareExport(); exporter.launch("fitness-information-${selected.recordId}.json") },
                            enabled = !ui.busy && record != null, modifier = Modifier.fillMaxWidth()) { Text("JSON 파일 내보내기") }
                        AppOutlinedButton(onClick = model::correction, enabled = !ui.busy && record != null, modifier = Modifier.fillMaxWidth()) { Text("정정 초안 만들기") }
                    }
                }
                else -> {
                    item {
                        AppButton(onClick = model::create, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) { Text("새 활동 자료 기록") }
                        AppOutlinedButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                            enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) { Text("JSON 파일 가져오기") }
                        AppOutlinedButton(onClick = { model.prepareRemoteInformation() }, enabled = !ui.busy,
                            modifier = Modifier.fillMaxWidth()) { Text("OCR 전송 자료 가져오기") }
                        if (ui.hasMoreRemoteInformation) AppOutlinedButton(onClick = { model.prepareRemoteInformation(true) },
                            enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) { Text("다음 OCR 전송 자료 가져오기") }
                        AppOutlinedButton(onClick = { model.prepareRemoteMeals() }, enabled = !ui.busy,
                            modifier = Modifier.fillMaxWidth()) { Text("Nutrition 식사 가져오기") }
                        if (ui.hasMoreRemoteMeals) AppOutlinedButton(onClick = { model.prepareRemoteMeals(true) },
                            enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) { Text("다음 원격 식사 가져오기") }
                        if (ui.draft != null) AppOutlinedButton(onClick = model::resumeDraft, enabled = !ui.busy) { Text("보관한 초안 이어 쓰기") }
                    }
                    if (ui.records.isEmpty() && !ui.busy) item { Text("아직 보관한 자료가 없습니다. 활동 관찰을 기록하거나 OCR에서 내보낸 Fitness 자료를 가져오세요.") }
                    items(ui.records, key = { it.recordId }) { row ->
                        Column(Modifier.fillMaxWidth().clickable(enabled = !ui.busy) { model.select(row) }.padding(vertical = 12.dp)) {
                            Text(row.title, style = MaterialTheme.typography.titleMedium)
                            Text((InformationReviewState.entries.find { it.wire == row.status }?.label ?: row.status) + " · " + row.receivedAt.take(10), style = MaterialTheme.typography.bodySmall)
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
    ui.remoteMapping?.let { mapping ->
        AlertDialog(onDismissRequest = model::dismissRemoteMeals,
            containerColor = MaterialTheme.colorScheme.background.copy(alpha = 1f),
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            textContentColor = MaterialTheme.colorScheme.onBackground,
            title = { Text("계정 연결 확인") },
            text = { Text(mapping, modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { AppButton(onClick = model::importRemoteMeals) { Text("확인 후 가져오기") } },
            dismissButton = { TextButton(onClick = model::dismissRemoteMeals,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) { Text("취소") } })
    }
    if (confirmLeave) AlertDialog(onDismissRequest = { confirmLeave = false },
        containerColor = MaterialTheme.colorScheme.background.copy(alpha = 1f),
        title = { Text("초안 편집을 닫을까요?") },
        text = { Text("보관하지 않은 수정은 사라집니다. 계속 작성하거나 초안 보관을 누른 뒤 닫으세요.") },
        confirmButton = { TextButton(onClick = { confirmLeave = false; model.closeEditor() }) { Text("편집 닫기") } },
        dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("계속 작성") } })
}
