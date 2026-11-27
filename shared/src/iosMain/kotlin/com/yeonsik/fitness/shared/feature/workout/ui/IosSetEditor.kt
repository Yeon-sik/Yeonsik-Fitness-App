@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.yeonsik.fitness.shared.feature.workout.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import com.yeonsik.fitness.shared.feature.settings.IosFitnessTheme
import com.yeonsik.fitness.shared.feature.workout.model.*
import platform.UIKit.UIViewController

data class IosSetRow(
    val id: String, val unit: MassUnit, val weight: String, val reps: String, val rir: String,
    val completed: Boolean, val storedLoad: Double?, val storedKg: Double?
)

private data class SetText(val weight: String, val reps: String, val rir: String, val unit: MassUnit)
private fun IosSetRow.text() = SetText(weight, reps, rir, unit)

interface IosSetWriteReply {
    fun finish(error: String?)
}

interface IosSetEditorStorage {
    fun add(reply: IosSetWriteReply)
    fun remove(id: String, reply: IosSetWriteReply)
    fun save(id: String, entry: OrdinarySetEntry, completed: Boolean, reply: IosSetWriteReply)
}

class IosSetEditorHost(private val decimalSeparator: String) {
    private var rows by mutableStateOf(emptyList<IosSetRow>())
    private val drafts = mutableStateMapOf<String, SetText>()
    private var writing by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)

    fun update(rows: List<IosSetRow>) {
        this.rows = rows
        val ids = rows.mapTo(mutableSetOf()) { it.id }
        drafts.keys.toList().filterNot(ids::contains).forEach(drafts::remove)
    }

    private fun write(onSuccess: () -> Unit = {}, action: (IosSetWriteReply) -> Unit) {
        if (writing) return
        writing = true
        error = null
        action(object : IosSetWriteReply {
            override fun finish(error: String?) {
                writing = false
                this@IosSetEditorHost.error = error
                if (error == null) onSuccess()
            }
        })
    }

    fun controller(storage: IosSetEditorStorage): UIViewController = ComposeUIViewController {
        IosFitnessTheme {
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val columns = WorkoutSetInputColumns("중량", "횟수", hasRir = true, hasLoadState = false)
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val width = maxOf(maxWidth, columns.minimumWidth * LocalDensity.current.fontScale)
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            Column(Modifier.width(width), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                WorkoutSetTableHeader(columns)
                                rows.forEachIndexed { index, row ->
                                    key(row.id) {
                                        SetRowEditor(row, drafts[row.id], index + 1, columns, writing, decimalSeparator,
                                            OrdinarySetRules.canRemove(rows.size, index + 1L, index == 0),
                                            onEdit = { drafts[row.id] = it },
                                            onCancel = { drafts.remove(row.id) },
                                            onInvalid = { error = "중량은 0 이상의 숫자, 횟수와 RIR은 0 이상의 정수로 입력하세요." },
                                            onRemove = { write { storage.remove(row.id, it) } },
                                            onSave = { entry, completed ->
                                                write({ drafts.remove(row.id) }) { storage.save(row.id, entry, completed, it) }
                                            })
                                    }
                                }
                            }
                        }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    OutlinedButton(onClick = { write { storage.add(it) } },
                        enabled = !writing, modifier = Modifier.fillMaxWidth().testTag("add-set")) { Text("세트 추가") }
                }
            }
        }
    }
}

@Composable
private fun SetRowEditor(
    row: IosSetRow, draft: SetText?, number: Int, columns: WorkoutSetInputColumns, writing: Boolean, separator: String, canRemove: Boolean,
    onEdit: (SetText) -> Unit, onCancel: () -> Unit, onInvalid: () -> Unit, onRemove: () -> Unit,
    onSave: (OrdinarySetEntry, Boolean) -> Unit
) {
    val text = draft ?: row.text()
    val (weight, reps, rir, unit) = text
    val dirty = draft != null
    var menu by remember { mutableStateOf(false) }
    val label = "중량(${unit.symbol()})"
    val description = "${number}세트"
    val save: (Boolean) -> Unit = { completed ->
        if (!writing) {
            val entry = OrdinarySetRules.read(weight.replace(separator, "."), reps, rir, unit,
                if (unit == row.unit) row.weight.replace(separator, ".") else "", row.storedLoad, row.storedKg)
            if (entry == null) onInvalid() else onSave(entry, completed)
        }
    }
    val finish by rememberUpdatedState { save(row.completed) }
    val keyboard = remember(label) { SetKeyboard(listOf(label, "횟수", "RIR")) { finish() } }
    CompositionLocalProvider(LocalSetKeyboard provides keyboard) {
        WorkoutSetTableRow(columns,
            ordinary = OrdinarySetFields(weight, reps, rir, unit, { onEdit(text.copy(weight = it)) },
                { onEdit(text.copy(reps = it)) }, { onEdit(text.copy(rir = it)) },
                KeyboardActions.Default, KeyboardActions.Default, enabled = !writing),
            setContent = {
                Box {
                    TextButton(onClick = { menu = true }, enabled = !writing, contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.testTag("menu-set-$number").semantics { contentDescription = "$description 설정" }) { Text(number.toString()) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("입력 저장") }, onClick = { menu = false; save(row.completed) })
                        OrdinarySetOptions(unit, dirty, onCancel = { menu = false; onCancel() }) { item ->
                            menu = false
                            if (unit != item) onEdit(text.copy(unit = item))
                        }
                    }
                }
            },
            completedContent = { WorkoutSetCompletionToggle(row.completed && !dirty, save, !writing, false,
                MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary, Modifier.testTag("complete-set-${row.id}").semantics {
                    contentDescription = "$description 완료"
                    stateDescription = if (dirty) "입력 변경됨, 체크하면 저장" else if (row.completed) "저장 및 완료됨" else "체크하면 저장"
                }) },
            loadContent = {},
            removeContent = { WorkoutSetRemoveButton("$description 삭제", canRemove && !writing, onRemove, Modifier.testTag("remove-set-$number")) }
        )
    }
}
