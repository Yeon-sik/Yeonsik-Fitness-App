package com.yeonsik.fitness.shared.feature.dailyworkout

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.yeonsik.fitness.shared.core.ui.fitnessColorScheme
import com.yeonsik.fitness.shared.core.ui.fitnessShapes
import com.yeonsik.fitness.shared.core.ui.fitnessTypography
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSNumber
import platform.Foundation.create
import platform.Foundation.writeToURL
import platform.UIKit.UIViewController
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface IosDailyWorkoutPlatform {
    fun cloudAccount(): WorkoutCloudAccount?
    fun cloudRpc(owner: String, host: String, function: String, body: String, reply: IosDailyWorkoutFileReply)
    fun readHealth(startMillis: Long, endMillis: Long, reply: IosDailyWorkoutHealthReply)
    fun pickFleekCsv(reply: IosDailyWorkoutFileReply)
    fun saveJson(name: String, json: String, reply: IosDailyWorkoutFileReply)
    fun close()
}

interface IosDailyWorkoutHealthReply {
    fun complete(records: DailyWearableRecords?)
    fun fail(message: String)
}

interface IosDailyWorkoutFileReply {
    fun complete(text: String?)
    fun fail(message: String)
}

@OptIn(ExperimentalForeignApi::class)
fun makeDailyWorkoutViewController(
    initialDate: String,
    workouts: List<DailyWorkoutSource>,
    platform: IosDailyWorkoutPlatform
): UIViewController = ComposeUIViewController {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = remember(dark) { fitnessColorScheme(dark) },
        typography = remember { fitnessTypography() },
        shapes = remember { fitnessShapes() }
    ) {
        val repository = remember(workouts) {
            DailyWorkoutRepository(IosDailyWorkoutStorage()) { day ->
                val start = day.start.toEpochMilliseconds()
                val end = day.end.toEpochMilliseconds()
                workouts.filter { it.startMillis?.let { time -> time in start until end } == true }
            }
        }
        val access = remember(platform) {
            object : DailyWorkoutDeviceAccess {
                override suspend fun readWearables(day: WorkoutDay): DailyWearableRecords? = suspendCancellableCoroutine { reply ->
                    platform.readHealth(day.start.toEpochMilliseconds(), day.end.toEpochMilliseconds(), object : IosDailyWorkoutHealthReply {
                        override fun complete(records: DailyWearableRecords?) { if (reply.isActive) reply.resume(records) }
                        override fun fail(message: String) { if (reply.isActive) reply.resumeWithException(IllegalArgumentException(message)) }
                    })
                }

                override suspend fun pickFleekCsv(): String? = awaitFile(platform::pickFleekCsv)
                override suspend fun exportJson(name: String, json: String): Boolean = awaitFile { platform.saveJson(name, json, it) } != null
            }
        }
        val cloud = remember(platform) {
            object : WorkoutCloudAccess {
                override fun account() = platform.cloudAccount()
                override suspend fun rpc(account: WorkoutCloudAccount, function: String, body: String): String =
                    checkNotNull(awaitFile { platform.cloudRpc(account.owner, account.host, function, body, it) })
            }
        }
        DailyWorkoutScreen(initialDate, repository, access, platform::close, cloud)
    }
}

fun makeIosFitnessWorkout(
    id: String, title: String, startMillis: Long, endMillis: Long, exercises: List<DailyWorkoutExercise>, volumeKg: Double
) = DailyWorkoutSource(
    WorkoutOrigin.FITNESS, WorkoutOrigin.FITNESS.label, id, title, DailyWorkoutKind.STRENGTH,
    startMillis, endMillis, ((endMillis - startMillis) / 1000).toInt().takeIf { it > 0 }, exercises = exercises,
    totalVolumeKg = volumeKg
)

fun makeIosWorkoutSet(id: String, weightKg: Double, reps: Int, rir: Int, inputLoad: Double, unit: String) = DailyWorkoutSet(
    weightKg.takeIf { it.isFinite() && it >= 0 }, reps.takeIf { it > 0 }, rir.takeIf { it >= 0 },
    id = id, inputLoad = inputLoad.takeIf { it.isFinite() && it >= 0 },
    inputUnit = unit.takeIf { inputLoad.isFinite() && inputLoad >= 0 }, loadState = "external_load", setType = "normal"
)

fun makeAppleHealthWorkout(
    id: String, writer: String, kindName: String, startMillis: Long, endMillis: Long,
    durationSeconds: Int, distanceMeters: Double, energyKcal: Double
): DailyWorkoutSource {
    val kind = DailyWorkoutKind.valueOf(kindName)
    return DailyWorkoutSource(
        WorkoutOrigin.HEALTH_KIT, writer, id, kind.label, kind, startMillis, endMillis,
        durationSeconds.takeIf { it > 0 }, distanceMeters.takeIf { it.isFinite() && it >= 0 },
        energyKcal.takeIf { it.isFinite() && it >= 0 }
    )
}

private suspend fun awaitFile(request: (IosDailyWorkoutFileReply) -> Unit): String? = suspendCancellableCoroutine { reply ->
    request(object : IosDailyWorkoutFileReply {
        override fun complete(text: String?) { if (reply.isActive) reply.resume(text) }
        override fun fail(message: String) { if (reply.isActive) reply.resumeWithException(IllegalArgumentException(message)) }
    })
}

@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
private class IosDailyWorkoutStorage : DailyWorkoutStorage {
    private val manager = NSFileManager.defaultManager
    private val directory: NSURL
        get() {
            val base = manager.URLsForDirectory(NSApplicationSupportDirectory, NSUserDomainMask).firstOrNull() as? NSURL
            return checkNotNull(base?.URLByAppendingPathComponent("DailyWorkout", isDirectory = true))
        }

    override fun read(name: String): String? {
        val url = checkNotNull(directory.URLByAppendingPathComponent("$name.json"))
        if (!manager.fileExistsAtPath(checkNotNull(url.path))) return null
        return checkNotNull(NSString.create(contentsOfURL = url, encoding = NSUTF8StringEncoding, error = null)).toString()
    }

    override fun write(name: String, text: String) {
        val folder = directory
        check(manager.createDirectoryAtURL(folder, withIntermediateDirectories = true, attributes = null, error = null))
        check(folder.setResourceValue(NSNumber(bool = true), forKey = NSURLIsExcludedFromBackupKey, error = null))
        val url = checkNotNull(folder.URLByAppendingPathComponent("$name.json"))
        check(NSString.create(string = text).writeToURL(url, atomically = true, encoding = NSUTF8StringEncoding, error = null))
    }
}
