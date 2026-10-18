package com.yeonsik.fitnessapp.feature.workout.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.yeonsik.fitness.shared.feature.workout.model.WORKOUT_NOTE_MAX_LENGTH
import com.yeonsik.fitnessapp.core.ui.FitnessSpacing

/** Plain content and callbacks; drafts belong to this account/record/field, not to set editors. */
@Composable
internal fun WorkoutNoteField(
    identity: String,
    title: String,
    value: String,
    hint: String,
    placeholder: String,
    readOnly: Boolean,
    onSave: (String, (Boolean) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    tag: String = "workout-note",
    headerTrailing: (@Composable () -> Unit)? = null
) {
    if (readOnly && value.isBlank()) return
    key(identity) {
        var draft by rememberSaveable(identity) { mutableStateOf(value) }
        var baseline by rememberSaveable(identity) { mutableStateOf(value) }
        var expanded by rememberSaveable(identity) { mutableStateOf(false) }
        var saving by remember(identity) { mutableStateOf(false) }
        var failed by rememberSaveable(identity) { mutableStateOf(false) }
        LaunchedEffect(identity, value) {
            if (draft == baseline) draft = value
            baseline = value
        }
        val dirty = draft.trim() != baseline
        val tooLong = draft.length > WORKOUT_NOTE_MAX_LENGTH
        Column(modifier.testTag(tag), verticalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f, fill = headerTrailing == null),
                        style = MaterialTheme.typography.titleSmall)
                    if (!readOnly) TextButton(onClick = { expanded = !expanded }, enabled = !saving,
                        contentPadding = PaddingValues(horizontal = FitnessSpacing.micro),
                        modifier = Modifier.testTag("$tag-toggle")) {
                        Text(if (expanded) "접기" else if (dirty) "작성 중" else if (value.isBlank()) "추가" else "편집")
                    }
                }
                if (headerTrailing != null) {
                    Spacer(Modifier.width(FitnessSpacing.small))
                    headerTrailing()
                }
            }
            if (readOnly) {
                Text(value, style = MaterialTheme.typography.bodyMedium)
            } else if (expanded) {
                if (hint.isNotBlank()) Text(hint, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it; failed = false },
                    label = { Text(title) },
                    placeholder = { Text(placeholder) },
                    enabled = !saving,
                    isError = tooLong || failed,
                    minLines = 3,
                    maxLines = 6,
                    supportingText = {
                        Text(when {
                            tooLong -> "메모는 ${WORKOUT_NOTE_MAX_LENGTH}자까지 저장할 수 있습니다."
                            failed -> "저장하지 못했습니다. 내용을 유지했으니 다시 저장하세요."
                            else -> "${draft.length}/${WORKOUT_NOTE_MAX_LENGTH} · 저장을 눌러 반영하세요."
                        })
                    },
                    modifier = Modifier.fillMaxWidth().testTag("$tag-input")
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = {
                            saving = true
                            val submitted = draft.trim()
                            onSave(submitted) { ok ->
                                saving = false
                                failed = !ok
                                if (ok) {
                                    baseline = submitted
                                    draft = submitted
                                    expanded = false
                                }
                            }
                        },
                        enabled = dirty && !tooLong && !saving,
                        modifier = Modifier.testTag("$tag-save")
                    ) { Text(if (saving) "저장 중…" else "저장") }
                }
            } else {
                val preview = if (dirty) draft.ifBlank { "메모 삭제 · 저장 전" } else value.ifBlank { hint }
                if (preview.isNotBlank()) Text(preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
