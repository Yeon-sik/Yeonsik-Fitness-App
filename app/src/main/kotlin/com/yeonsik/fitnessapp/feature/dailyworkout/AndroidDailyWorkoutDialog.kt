package com.yeonsik.fitnessapp.feature.dailyworkout

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.health.connect.client.PermissionController
import com.yeonsik.fitness.shared.feature.dailyworkout.DailyWorkoutDeviceAccess
import com.yeonsik.fitness.shared.feature.dailyworkout.DailyWorkoutRepository
import com.yeonsik.fitness.shared.feature.dailyworkout.DailyWorkoutScreen
import com.yeonsik.fitness.shared.feature.dailyworkout.WorkoutDay
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun AndroidDailyWorkoutDialog(date: String, repository: DailyWorkoutRepository, onClose: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val resolver = context.contentResolver
    val cloud = remember(context, repository) { AndroidWorkoutCloudAccess(context) }
    val reader = remember(context) { HealthConnectDailyWorkoutReader(context) }
    val permissions = remember { ActivityReply<Set<String>>() }
    val inputFile = remember { ActivityReply<android.net.Uri?>() }
    val outputFile = remember { ActivityReply<android.net.Uri?>() }
    val permissionLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract(), permissions::complete)
    val inputLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), inputFile::complete)
    val outputLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json"), outputFile::complete)
    val device = remember(reader) {
        object : DailyWorkoutDeviceAccess {
            override suspend fun readWearables(day: WorkoutDay) = reader.read(day) { permissions.launch(permissionLauncher, it) }

            override suspend fun pickFleekCsv(): String? {
                val uri = inputFile.launch(inputLauncher, arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream"))
                    ?: return null
                return withContext(Dispatchers.IO) {
                    checkNotNull(resolver.openInputStream(uri)).bufferedReader().use { it.readText() }
                }
            }

            override suspend fun exportJson(name: String, json: String): Boolean {
                val uri = outputFile.launch(outputLauncher, name) ?: return false
                withContext(Dispatchers.IO) {
                    checkNotNull(resolver.openOutputStream(uri, "wt")).bufferedWriter().use { it.write(json) }
                }
                return true
            }
        }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DailyWorkoutScreen(date, repository, device, onClose, cloud)
    }
}

private class ActivityReply<O> {
    private var pending: CompletableDeferred<O>? = null

    suspend fun <I> launch(launcher: ActivityResultLauncher<I>, input: I): O {
        val result = CompletableDeferred<O>()
        check(pending == null)
        pending = result
        return try {
            launcher.launch(input)
            result.await()
        } finally {
            pending = null
        }
    }

    fun complete(value: O) {
        pending?.complete(value)
    }
}
