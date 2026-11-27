package com.yeonsik.fitness.shared.feature.body.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitness.shared.feature.settings.IosDisplaySettings
import com.yeonsik.fitness.shared.feature.settings.IosFitnessTheme
import com.yeonsik.fitness.shared.feature.body.application.BodyMetricsApplicationService
import com.yeonsik.fitness.shared.feature.body.data.IosBodyMetricsRepository
import com.yeonsik.fitness.shared.feature.body.model.BodyReadEntry
import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.NSNumberFormatterRoundHalfUp
import platform.UIKit.UIViewController
import kotlin.time.Clock

@OptIn(ExperimentalForeignApi::class)
fun makeBodyMetricsViewController(openToday: Boolean, onClose: () -> Unit): UIViewController = ComposeUIViewController {
    IosFitnessTheme {
        BodyMetricsHistory(IosDisplaySettings.unit, openToday, onClose)
    }
}

@Composable
private fun BodyMetricsHistory(unit: MassUnit, openToday: Boolean, onClose: () -> Unit) {
    val account = remember { AccountScope("ios-local") }
    val service = remember { BodyMetricsApplicationService(IosBodyMetricsRepository(), account.ownerId) }
    val scope = rememberCoroutineScope()
    val editor = remember { BodyMetricsEditorController(service, scope) }
    val snackbar = remember { SnackbarHostState() }
    var records by remember { mutableStateOf<List<BodyReadEntry>?>(null) }
    var readError by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableStateOf(0) }

    fun openTodayEditor() {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        editor.open(account, today.toString(), null)
    }
    if (openToday) {
        LaunchedEffect(Unit) { openTodayEditor() }
    }
    LaunchedEffect(revision) {
        try {
            records = withContext(Dispatchers.Default) { service.recentEntries(account) }
            readError = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            readError = error.message ?: "체중 기록을 불러오지 못했습니다."
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { insets ->
        LazyColumn(
            Modifier.fillMaxSize().padding(insets),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("체중 기록", style = MaterialTheme.typography.headlineLarge)
            }
            item {
                OutlinedButton(
                    onClick = ::openTodayEditor,
                    modifier = Modifier.fillMaxWidth().testTag("weight-add")
                ) { Text("체중 기록 추가") }
            }
            if (readError != null) {
                item {
                    Text(checkNotNull(readError))
                    OutlinedButton(onClick = { revision++ }) { Text("다시 불러오기") }
                }
            } else if (records.isNullOrEmpty()) {
                item { Text(if (records == null) "체중 기록 불러오는 중" else "체중 기록이 없습니다.") }
            }
            items(records.orEmpty(), key = { it.id }) { entry ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(entry.date, style = MaterialTheme.typography.labelLarge)
                    BodyWeightRecordCard(
                        weightLabel = formatWeight(unit.fromKg(entry.weightKg), 1, true) + unit.symbol(),
                        onEdit = { editor.open(account, entry.date, entry.id) },
                        modifier = Modifier.fillMaxWidth().testTag("weight-record")
                    )
                }
            }
        }
    }

    BodyMetricsEditorDialog(
        controller = editor,
        account = account,
        unit = unit,
        formatInput = { kilograms, unit -> formatWeight(MassUnit.fromKg(kilograms, unit), 2, false) },
        onRecordsChanged = { revision++; if (openToday) onClose() },
        notify = { message -> scope.launch { snackbar.showSnackbar(message) } },
        onCancel = { if (openToday) onClose() }
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun formatWeight(value: Double, decimals: Int, grouping: Boolean): String {
    if (!value.isFinite()) return "—"
    val formatter = NSNumberFormatter().apply {
        locale = NSLocale(localeIdentifier = "en_US_POSIX")
        numberStyle = NSNumberFormatterDecimalStyle
        minimumFractionDigits = 0u
        maximumFractionDigits = decimals.toULong()
        usesGroupingSeparator = grouping
        roundingMode = NSNumberFormatterRoundHalfUp
    }
    return formatter.stringFromNumber(NSNumber(double = value)) ?: "—"
}
