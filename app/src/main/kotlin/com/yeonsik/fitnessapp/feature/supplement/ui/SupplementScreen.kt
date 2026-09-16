package com.yeonsik.fitnessapp.feature.supplement.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.core.ui.AppButton
import com.yeonsik.fitnessapp.core.ui.AppCard
import com.yeonsik.fitnessapp.core.ui.AppHeader
import com.yeonsik.fitnessapp.core.ui.AppOutlinedButton
import com.yeonsik.fitnessapp.core.ui.AppSpacing
import com.yeonsik.fitnessapp.core.ui.AppTextField
import com.yeonsik.fitnessapp.feature.supplement.model.SupplementEffectCheckin
import com.yeonsik.fitnessapp.feature.supplement.model.SupplementHistoryEntry
import com.yeonsik.fitnessapp.feature.supplement.model.SupplementPlanDraft
import com.yeonsik.fitnessapp.supplement.SupplementCatalog
import com.yeonsik.fitnessapp.supplement.SupplementEvidence
import com.yeonsik.fitnessapp.supplement.SupplementEvidenceCatalog
import com.yeonsik.fitnessapp.supplement.SupplementPlan
import com.yeonsik.fitnessapp.feature.supplement.data.SupplementRepository
import java.time.LocalDate

interface SupplementScreenActions {
    fun back()
    fun selectDate(date: String)
    fun record(date: String, scheduleId: String, status: String)
    fun undo(date: String, scheduleId: String)
    fun savePlan(existing: SupplementPlan?, draft: SupplementPlanDraft)
    fun archive(itemId: String)
    fun updateHistory(recordId: String, status: String)
    fun deleteHistory(recordId: String)
    fun saveEffect(itemId: String, score: Int, adverseEffects: String, note: String)
}

@Composable
internal fun SupplementScreen(
    state: SupplementUiState,
    ownerId: String,
    today: String,
    actions: SupplementScreenActions
) {
    val ready = state as? SupplementUiState.Ready
    AppHeader("보충제", "계획·복용·경과", back = actions::back)
    if (ready == null || ready.ownerId != ownerId || ready.today != today) {
        when (state) {
            is SupplementUiState.Error -> Text(state.message)
            else -> Text("보충제 기록을 불러오는 중입니다.")
        }
        return
    }

    var planEditorTarget by remember { mutableStateOf<SupplementPlan?>(null) }
    var showPlanEditor by remember { mutableStateOf(false) }
    var evidenceTarget by remember { mutableStateOf<SupplementPlan?>(null) }
    var effectTarget by remember { mutableStateOf<SupplementPlan?>(null) }
    var historyTarget by remember { mutableStateOf<SupplementHistoryEntry?>(null) }
    val selectedDate = ready.selectedDate
    val selected = runCatching { LocalDate.parse(selectedDate) }.getOrNull()
    val todayDate = runCatching { LocalDate.parse(ready.today) }.getOrNull()

    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.gap)) {
        if (!ready.notice.isNullOrBlank()) {
            AppCard(Modifier.fillMaxWidth()) {
                Text(ready.notice, Modifier.padding(AppSpacing.card), color = MaterialTheme.colorScheme.primary)
            }
        }
        if (selected != null && todayDate != null) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)
            ) {
                AppOutlinedButton(
                    onClick = { actions.selectDate(selected.minusDays(1).toString()) },
                    modifier = Modifier.weight(1f)
                ) { Text("‹ 이전") }
                Text(
                    selectedDate,
                    modifier = Modifier.weight(1f).padding(top = 14.dp),
                    textAlign = TextAlign.Center
                )
                AppOutlinedButton(
                    onClick = { actions.selectDate(selected.plusDays(1).toString()) },
                    modifier = Modifier.weight(1f),
                    enabled = selected.isBefore(todayDate)
                ) { Text("다음 ›") }
            }
            AppOutlinedButton(
                onClick = { actions.selectDate(ready.today) },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedDate != ready.today
            ) { Text("오늘로 이동") }
        }

        AppCard(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(AppSpacing.card),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
            ) {
                Text(
                    "복용 ${ready.progress.taken}/${ready.progress.planned}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "건너뜀 ${ready.progress.skipped} · 미기록 ${ready.progress.unrecorded} · " +
                        "최근 14일 준수율 ${ready.adherence.adherencePercent()}%"
                )
                Text(
                    "예정 ${ready.adherence.planned} · 복용 ${ready.adherence.taken} · " +
                        "건너뜀 ${ready.adherence.skipped} · 미기록 ${ready.adherence.unrecorded}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (selectedDate == ready.today) {
            AppButton(
                onClick = {
                    planEditorTarget = null
                    showPlanEditor = true
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("복용 계획 추가") }
        }

        if (ready.plans.isEmpty()) {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(AppSpacing.card)) {
                    Text("등록된 복용 계획이 없습니다.", fontWeight = FontWeight.Bold)
                    Text("종류·브랜드·제품 형태·용량·복용 시점을 입력해 계획을 추가하세요.")
                }
            }
        } else {
            Text("복용 계획", style = MaterialTheme.typography.titleLarge)
            ready.plans.forEach { plan ->
                SupplementPlanCard(
                    plan = plan,
                    allPlans = ready.plans,
                    effect = ready.effects[plan.itemId],
                    canRecord = selectedDate <= ready.today,
                    onRecord = { status -> actions.record(selectedDate, plan.scheduleId, status) },
                    onUndo = { actions.undo(selectedDate, plan.scheduleId) },
                    onEdit = {
                        planEditorTarget = plan
                        showPlanEditor = true
                    },
                    onArchive = { actions.archive(plan.itemId) },
                    onEvidence = { evidenceTarget = plan },
                    onEffect = { effectTarget = plan }
                )
            }
        }

        Text("최근 7일 기록", style = MaterialTheme.typography.titleLarge)
        if (ready.history.isEmpty()) {
            AppCard(Modifier.fillMaxWidth()) {
                Text("복용 기록이 없습니다.", Modifier.padding(AppSpacing.card))
            }
        } else {
            ready.history.forEach { entry ->
                SupplementHistoryCard(entry) { historyTarget = entry }
            }
        }

        AppCard(Modifier.fillMaxWidth()) {
            Text(
                "표시되는 용량과 복용법은 사용자가 입력한 계획입니다. 근거 카드는 성분 수준의 연구 적용성 안내이며, 의료 조언·권장 용량·안전 승인을 제공하지 않습니다.",
                Modifier.padding(AppSpacing.card),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showPlanEditor) {
        SupplementPlanEditorDialog(
            existing = planEditorTarget,
            onDismiss = { showPlanEditor = false },
            onSave = { draft ->
                showPlanEditor = false
                actions.savePlan(planEditorTarget, draft)
            }
        )
    }
    evidenceTarget?.let { plan ->
        SupplementEvidenceDialog(plan, onDismiss = { evidenceTarget = null })
    }
    effectTarget?.let { plan ->
        SupplementEffectDialog(
            plan = plan,
            existing = ready.effects[plan.itemId],
            date = selectedDate,
            onDismiss = { effectTarget = null },
            onSave = { score, adverse, note ->
                effectTarget = null
                actions.saveEffect(plan.itemId, score, adverse, note)
            }
        )
    }
    historyTarget?.let { entry ->
        SupplementHistoryCorrectionDialog(
            entry = entry,
            onDismiss = { historyTarget = null },
            onUpdate = { status ->
                historyTarget = null
                actions.updateHistory(entry.id, status)
            },
            onDelete = {
                historyTarget = null
                actions.deleteHistory(entry.id)
            }
        )
    }
}

@Composable
private fun SupplementPlanCard(
    plan: SupplementPlan,
    allPlans: List<SupplementPlan>,
    effect: SupplementEffectCheckin?,
    canRecord: Boolean,
    onRecord: (String) -> Unit,
    onUndo: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onEvidence: () -> Unit,
    onEffect: () -> Unit
) {
    val duplicateCount = allPlans.count { it.typeCode == plan.typeCode }
    AppCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(AppSpacing.card),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
        ) {
            Text("${plan.typeName} · ${plan.brandName}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "제품 1회 ${formatDose(plan.doseAmount, plan.doseUnit)} · ${plan.productForm} · " +
                    SupplementRepository.purposeLabel(plan.purposeCode)
            )
            Text(
                if (plan.activeIngredientAmount == null) "주요 성분량: 미입력"
                else "주요 성분 ${formatDose(plan.activeIngredientAmount, plan.activeIngredientUnit)}" +
                    if (plan.ingredientDetails.isBlank()) "" else " · ${plan.ingredientDetails}",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("하루 ${plan.timesPerDay}회 · ${plan.timingLabels.joinToString(" / ")}")
            if (duplicateCount > 1) {
                Text(
                    "같은 종류가 ${duplicateCount}개 등록되어 있습니다. 중복 성분과 총 섭취량을 확인하세요.",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                "복용 ${plan.takenCount} / ${plan.timesPerDay} · 건너뜀 ${plan.skippedCount} · 미기록 ${plan.unrecordedCount()}",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val evidence = SupplementEvidenceCatalog.forType(plan.typeCode)
            Text(
                "근거 · ${evidence.statusLabel} · ${evidence.reviewedOn} 검토",
                color = if (evidence.status == SupplementEvidence.Status.VERIFIED_DIRECT) {
                    MaterialTheme.colorScheme.primary
                } else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
            effect?.let {
                Text("최근 경과 ${it.date} · 체감 ${it.effectScore}/5")
                if (it.adverseEffects.isNotBlank()) Text("이상반응: ${it.adverseEffects}")
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)
            ) {
                AppButton(
                    onClick = { onRecord(SupplementRepository.STATUS_TAKEN) },
                    modifier = Modifier.weight(1f),
                    enabled = canRecord && plan.unrecordedCount() > 0
                ) { Text("복용") }
                AppOutlinedButton(
                    onClick = { onRecord(SupplementRepository.STATUS_SKIPPED) },
                    modifier = Modifier.weight(1f),
                    enabled = canRecord && plan.unrecordedCount() > 0
                ) { Text("건너뜀") }
                AppOutlinedButton(
                    onClick = onUndo,
                    modifier = Modifier.weight(1f),
                    enabled = plan.recordedCount() > 0
                ) { Text("되돌리기") }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)
            ) {
                AppOutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                    enabled = plan.currentlyActive
                ) { Text("계획 수정") }
                AppOutlinedButton(onClick = onEvidence, modifier = Modifier.weight(1f)) { Text("근거") }
                AppOutlinedButton(onClick = onEffect, modifier = Modifier.weight(1f)) { Text("경과") }
            }
            if (plan.currentlyActive) {
                TextButton(onClick = onArchive) { Text("복용 계획 종료", color = MaterialTheme.colorScheme.error) }
            } else {
                Text("이 날짜에 적용됐던 종료된 계획", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SupplementHistoryCard(entry: SupplementHistoryEntry, onClick: () -> Unit) {
    val status = if (entry.status == SupplementRepository.STATUS_TAKEN) "복용" else "건너뜀"
    val actualTime = if (entry.takenAt.isNullOrBlank()) "시각 없음" else entry.takenAt!!.take(16).replace('T', ' ')
    AppCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(AppSpacing.card)) {
            Text("${entry.date} · ${entry.typeName} · $status", fontWeight = FontWeight.Bold)
            Text(
                "${entry.brandName} · ${formatDose(entry.doseAmount, entry.doseUnit)} · " +
                    "${entry.timingLabel} · ${entry.doseIndex}회차 · $actualTime" +
                    if (entry.recordSource == "backfill") " · 사후 입력" else ""
            )
            Text("탭하여 기록 수정", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SupplementHistoryCorrectionDialog(
    entry: SupplementHistoryEntry,
    onDismiss: () -> Unit,
    onUpdate: (String) -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${entry.typeName} 기록 수정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                Text("${entry.date} · ${entry.brandName} · ${formatDose(entry.doseAmount, entry.doseUnit)}")
                Text("저장된 intake fact의 상태만 수정합니다. 기록 삭제는 계획 archive와 별개입니다.")
                AppOutlinedButton(onClick = { onUpdate(SupplementRepository.STATUS_TAKEN) }, modifier = Modifier.fillMaxWidth()) {
                    Text("복용으로 변경")
                }
                AppOutlinedButton(onClick = { onUpdate(SupplementRepository.STATUS_SKIPPED) }, modifier = Modifier.fillMaxWidth()) {
                    Text("건너뜀으로 변경")
                }
                AppOutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth(), destructive = true) {
                    Text("기록 삭제")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } }
    )
}

@Composable
private fun SupplementEvidenceDialog(plan: SupplementPlan, onDismiss: () -> Unit) {
    val evidence = SupplementEvidenceCatalog.forType(plan.typeCode)
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${plan.typeName} 논문 근거") },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
            ) {
                EvidenceText("근거 상태", evidence.statusLabel, true)
                EvidenceText("연구 요약", evidence.summaryKo)
                EvidenceText("이 기록에 적용할 때", evidence.applicabilityKo)
                EvidenceText("불확실성", evidence.limitationsKo)
                EvidenceText("안전 경계", evidence.safetyKo)
                EvidenceText(
                    "사용자 입력",
                    "제품 ${formatDose(plan.doseAmount, plan.doseUnit)} · ${plan.productForm} · " +
                        "목적 ${SupplementRepository.purposeLabel(plan.purposeCode)}"
                )
                Text(
                    "논문은 성분 종류 수준의 근거입니다. 제품 형태·성분량·복용 목적이 연구 조건과 일치하거나 개인에게 효과적이고 안전하다는 판정이 아닙니다.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("검증 출처", fontWeight = FontWeight.Bold)
                evidence.sources.forEach { source ->
                    Text("${source.evidenceId} · ${source.citation} · ${source.verificationStatus}")
                    TextButton(onClick = { runCatching { uriHandler.openUri(source.url) } }) {
                        Text("${source.title} 열기")
                    }
                }
                Text(
                    "마지막 검토일 ${evidence.reviewedOn} · Supplement evidence는 Development PaperAdvice와 별도 영역입니다.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } }
    )
}

@Composable
private fun EvidenceText(label: String, value: String, strong: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun SupplementEffectDialog(
    plan: SupplementPlan,
    existing: SupplementEffectCheckin?,
    date: String,
    onDismiss: () -> Unit,
    onSave: (Int, String, String) -> Unit
) {
    var score by rememberSaveable(plan.itemId, date) { mutableStateOf(existing?.effectScore ?: 3) }
    var adverse by rememberSaveable(plan.itemId, date) { mutableStateOf(existing?.adverseEffects.orEmpty()) }
    var note by rememberSaveable(plan.itemId, date) { mutableStateOf(existing?.note.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${plan.typeName} 경과 점검") },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
            ) {
                Text("기록 날짜 $date")
                Text("최근 체감 (1 낮음 · 5 높음)")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..5).forEach { value ->
                        AppOutlinedButton(
                            onClick = { score = value },
                            modifier = Modifier.weight(1f),
                            selected = score == value
                        ) { Text(value.toString()) }
                    }
                }
                AppTextField(
                    value = adverse,
                    onValueChange = { adverse = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("이상반응 (없으면 비움)") }
                )
                AppTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("메모") },
                    singleLine = false
                )
                Text(
                    "체감 기록은 인과관계나 의학적 효능을 증명하지 않습니다. 심한 이상반응은 복용을 중단하고 전문가와 상의하세요.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(score, adverse, note) }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

@Composable
private fun SupplementPlanEditorDialog(
    existing: SupplementPlan?,
    onDismiss: () -> Unit,
    onSave: (SupplementPlanDraft) -> Unit
) {
    val editorKey = existing?.scheduleId ?: "new"
    var typeCode by rememberSaveable(editorKey) { mutableStateOf(existing?.typeCode ?: "multivitamin") }
    var brand by rememberSaveable(editorKey) { mutableStateOf(existing?.brandName.orEmpty()) }
    var productForm by rememberSaveable(editorKey) { mutableStateOf(existing?.productForm ?: "캡슐") }
    var purposeCode by rememberSaveable(editorKey) { mutableStateOf(existing?.purposeCode ?: "general_health") }
    var amount by rememberSaveable(editorKey) { mutableStateOf(existing?.doseAmount?.toString().orEmpty()) }
    var unit by rememberSaveable(editorKey) { mutableStateOf(existing?.doseUnit ?: "캡슐") }
    var activeAmount by rememberSaveable(editorKey) {
        mutableStateOf(existing?.activeIngredientAmount?.toString().orEmpty())
    }
    var activeUnit by rememberSaveable(editorKey) {
        mutableStateOf(existing?.activeIngredientUnit?.ifBlank { "mg" } ?: "mg")
    }
    var ingredientDetails by rememberSaveable(editorKey) { mutableStateOf(existing?.ingredientDetails.orEmpty()) }
    var instructions by rememberSaveable(editorKey) { mutableStateOf("") }
    var timesText by rememberSaveable(editorKey) { mutableStateOf(existing?.timesPerDay?.toString() ?: "1") }
    val timings: SnapshotStateList<String> = remember(editorKey) {
        (existing?.timingLabels?.takeIf { it.isNotEmpty() } ?: listOf("상관없음")).toMutableStateList()
    }
    var validationMessage by rememberSaveable(editorKey) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "복용 계획 추가" else "복용 계획 수정") },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
            ) {
                OptionButton("영양제 종류", typeName(typeCode), SupplementCatalog.KINDS.map { it.name }) { label ->
                    typeCode = SupplementCatalog.KINDS.firstOrNull { it.name == label }?.code ?: typeCode
                }
                AppTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("브랜드") }
                )
                OptionButton("제품 형태", productForm, SupplementRepository.PRODUCT_FORMS) { productForm = it }
                OptionButton(
                    "복용 목적",
                    SupplementRepository.purposeLabel(purposeCode),
                    SupplementRepository.PURPOSE_LABELS
                ) { label ->
                    val index = SupplementRepository.PURPOSE_LABELS.indexOf(label)
                    if (index >= 0) purposeCode = SupplementRepository.PURPOSE_CODES[index]
                }
                AppTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("제품 1회 섭취량") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OptionButton("제품 섭취량 단위", unit, SupplementRepository.DOSE_UNITS) { unit = it }
                AppTextField(
                    value = activeAmount,
                    onValueChange = { activeAmount = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("주요 성분량 (선택)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OptionButton("주요 성분 단위", activeUnit, SupplementRepository.DOSE_UNITS) { activeUnit = it }
                AppTextField(
                    value = ingredientDetails,
                    onValueChange = { ingredientDetails = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("성분 형태·상세 (선택)") }
                )
                AppTextField(
                    value = timesText,
                    onValueChange = { timesText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("하루 횟수 (1~6)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                val count = timesText.toIntOrNull()?.coerceIn(1, 6) ?: 1
                Text("회차별 복용 시점", fontWeight = FontWeight.Bold)
                repeat(count) { index ->
                    val current = timings.getOrNull(index) ?: "상관없음"
                    OptionButton("${index + 1}회차", current, SupplementRepository.TIMING_LABELS) { value ->
                        while (timings.size <= index) timings.add("상관없음")
                        timings[index] = value
                    }
                }
                AppTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("복용 메모 (선택)") },
                    singleLine = false
                )
                validationMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsedAmount = amount.trim().toDoubleOrNull()
                val count = timesText.trim().toIntOrNull()
                val parsedActive = activeAmount.trim().let { if (it.isEmpty()) null else it.toDoubleOrNull() }
                validationMessage = when {
                    brand.trim().isEmpty() -> "브랜드를 입력하세요."
                    parsedAmount == null || parsedAmount <= 0 -> "1회 섭취량을 올바르게 입력하세요."
                    count == null || count !in 1..6 -> "하루 횟수는 1~6회입니다."
                    activeAmount.trim().isNotEmpty() && (parsedActive == null || parsedActive <= 0) ->
                        "주요 성분량을 올바르게 입력하세요."
                    else -> null
                }
                if (validationMessage == null && parsedAmount != null && count != null) {
                    val normalizedTimings = (0 until count).map { timings.getOrNull(it) ?: "상관없음" }
                    onSave(
                        SupplementPlanDraft(
                            typeCode = typeCode,
                            brandName = brand.trim(),
                            productForm = productForm,
                            purposeCode = purposeCode,
                            servingAmount = parsedAmount,
                            servingUnit = unit,
                            activeIngredientAmount = parsedActive,
                            activeIngredientUnit = activeUnit,
                            ingredientDetails = ingredientDetails.trim(),
                            timingLabels = normalizedTimings,
                            instructions = instructions.trim()
                        )
                    )
                }
            }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

@Composable
private fun OptionButton(
    label: String,
    selected: String,
    options: List<String>,
    onSelected: (String) -> Unit
) {
    var expanded by remember(label, selected) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            AppOutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selected)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            expanded = false
                            onSelected(option)
                        }
                    )
                }
            }
        }
    }
}

private fun typeName(code: String): String =
    SupplementCatalog.KINDS.firstOrNull { it.code == code }?.name ?: code

private fun formatDose(amount: Double, unit: String): String =
    amount.toBigDecimal().stripTrailingZeros().toPlainString() + " " + unit
