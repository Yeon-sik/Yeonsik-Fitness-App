package com.yeonsik.fitness.shared.feature.body.data

import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitness.shared.feature.body.api.BodyMetricsRepositoryApi
import com.yeonsik.fitness.shared.feature.body.model.BodyProfile
import com.yeonsik.fitness.shared.feature.body.model.BodyReadEntry
import com.yeonsik.fitness.shared.feature.body.model.BodyWeightWindow
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSLock
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.Foundation.writeToURL

/** Device-local body records. A mutation publishes its result only after the atomic file write. */
@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
class IosBodyMetricsRepository internal constructor(private val file: NSURL) : BodyMetricsRepositoryApi {
    constructor() : this(bodyStoreUrl())

    override fun addBodyMetric(
        scope: AccountScope, date: String, weightKg: Double, memo: String
    ): String = withStorage {
        val stored = load()
        val owner = stored.owner(scope)
        val recordDate = requireRecordDate(date)
        val weight = requireBodyWeight(weightKg)
        val existing = owner.entries.sortedWith(newestFirst).firstOrNull { it.date == recordDate }
        val id = existing?.id ?: NSUUID().UUIDString
        val nextOrder = stored.writeOrder + 1
        val entry = StoredEntry(id, recordDate, weight, memo.trim(), nextOrder)
        val updated = owner.copy(entries = owner.entries.filterNot { it.id == id } + entry)
        save(stored.withOwner(scope, updated, nextOrder))
        id
    }

    override fun bodyMetricEntryById(scope: AccountScope, id: String): BodyReadEntry? = withStorage {
        load().owner(scope).entries.firstOrNull { it.id == id.trim() }?.toEntry()
    }

    override fun bodyMetricEntriesForDate(scope: AccountScope, date: String?): List<BodyReadEntry> = withStorage {
        val requested = date?.let(::requireRecordDate)
        load().owner(scope).entries
            .filter { requested == null || it.date == requested }
            .sortedWith(newestFirst)
            .take(20)
            .map(StoredEntry::toEntry)
    }

    override fun updateBodyMetric(
        scope: AccountScope, id: String, date: String, weightKg: Double, memo: String
    ) = withStorage {
        val stored = load()
        val owner = stored.owner(scope)
        val current = owner.entries.firstOrNull { it.id == id.trim() } ?: return@withStorage
        val nextOrder = stored.writeOrder + 1
        val entry = current.copy(
            date = requireRecordDate(date), weightKg = requireBodyWeight(weightKg),
            memo = memo.trim(), writeOrder = nextOrder
        )
        val updated = owner.copy(entries = owner.entries.map { if (it.id == current.id) entry else it })
        save(stored.withOwner(scope, updated, nextOrder))
    }

    override fun deleteBodyMetric(scope: AccountScope, id: String) = withStorage {
        val stored = load()
        val owner = stored.owner(scope)
        val entries = owner.entries.filterNot { it.id == id.trim() }
        if (entries.size != owner.entries.size) save(stored.withOwner(scope, owner.copy(entries = entries)))
    }

    override fun bodyProfile(scope: AccountScope): BodyProfile = withStorage {
        val profile = load().owner(scope).profile
        if (profile == null) BodyProfile.empty() else
            BodyProfile(profile.heightCm, profile.createdAt, profile.updatedAt)
    }

    override fun saveBodyProfile(scope: AccountScope, profile: BodyProfile) = withStorage {
        val stored = load()
        val owner = stored.owner(scope)
        val persisted = profile.heightCm?.let { StoredProfile(it, profile.createdAt, profile.updatedAt) }
        save(stored.withOwner(scope, owner.copy(profile = persisted)))
    }

    override fun bodyMetrics(scope: AccountScope, date: String): List<BodyReadEntry> =
        bodyMetricEntriesForDate(scope, date)

    override fun latestBodyMetricOnOrBefore(scope: AccountScope, date: String): BodyReadEntry? = withStorage {
        val requested = requireRecordDate(date)
        load().owner(scope).entries.filter { it.date <= requested }
            .minWithOrNull(newestFirst)?.toEntry()
    }

    override fun recordedDays(scope: AccountScope, startDate: String, endDate: String): Int =
        dates(scope, startDate, endDate).size

    override fun dates(scope: AccountScope, startDate: String, endDate: String): List<String> =
        weightEntries(scope, startDate, endDate).map { it.date }.distinct()

    override fun earliestRecordedDate(scope: AccountScope): String? = withStorage {
        load().owner(scope).entries.minOfOrNull { it.date }
    }

    override fun weightWindow(scope: AccountScope, startDate: String, endDate: String): BodyWeightWindow {
        val entries = weightEntries(scope, startDate, endDate)
        return BodyWeightWindow(
            entries.takeIf { it.isNotEmpty() }?.map { it.weightKg }?.average(),
            entries.distinctBy { it.date }.size
        )
    }

    override fun weightEntries(scope: AccountScope, startDate: String, endDate: String): List<BodyReadEntry> = withStorage {
        val start = requireRecordDate(startDate)
        val end = requireRecordDate(endDate)
        load().owner(scope).entries.filter { it.date in start..end }
            .sortedWith(compareBy<StoredEntry> { it.date }.thenByDescending { it.writeOrder })
            .map(StoredEntry::toEntry)
    }

    private fun load(): BodyStore {
        val path = checkNotNull(file.path)
        if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return BodyStore(FORMAT_VERSION)
        val contents = NSString.create(contentsOfURL = file, encoding = NSUTF8StringEncoding, error = null)
        checkNotNull(contents) { "체중 기록 파일을 읽지 못했습니다." }
        val stored = try {
            json.decodeFromString<BodyStore>(contents.toString())
        } catch (failure: IllegalArgumentException) {
            throw IllegalStateException("체중 기록 파일 형식을 확인할 수 없습니다.", failure)
        }
        check(stored.schemaVersion == FORMAT_VERSION) { "지원하지 않는 체중 기록 파일 버전입니다." }
        return stored
    }

    private fun save(store: BodyStore) {
        val directory = checkNotNull(file.URLByDeletingLastPathComponent)
        check(NSFileManager.defaultManager.createDirectoryAtURL(
            url = directory, withIntermediateDirectories = true, attributes = null, error = null
        )) { "체중 기록 폴더를 열지 못했습니다." }
        val encoded = NSString.create(string = json.encodeToString(BodyStore.serializer(), store))
        check(encoded.writeToURL(file, atomically = true, encoding = NSUTF8StringEncoding, error = null)) {
            "체중 기록을 저장하지 못했습니다."
        }
    }

    private inline fun <T> withStorage(block: () -> T): T {
        storageLock.lock()
        return try {
            block()
        } finally {
            storageLock.unlock()
        }
    }

    private companion object {
        const val FORMAT_VERSION = 1
        val json = Json { encodeDefaults = true }
        val storageLock = NSLock()
        val newestFirst = compareByDescending<StoredEntry> { it.date }.thenByDescending { it.writeOrder }

        fun bodyStoreUrl(): NSURL {
            val base = NSFileManager.defaultManager
                .URLsForDirectory(NSApplicationSupportDirectory, NSUserDomainMask).firstOrNull() as? NSURL
            return checkNotNull(base?.URLByAppendingPathComponent("FitnessBody", isDirectory = true)
                ?.URLByAppendingPathComponent("body-metrics.json")) { "체중 기록 저장 위치가 없습니다." }
        }

        fun requireRecordDate(value: String): String =
            runCatching { LocalDate.parse(value.trim()).toString() }.getOrElse {
                throw IllegalArgumentException("날짜를 YYYY-MM-DD 형식으로 입력하세요.")
            }

        fun requireBodyWeight(value: Double): Double {
            require(value.isFinite() && value > 0) { "체중은 0보다 큰 숫자여야 합니다." }
            return value
        }
    }
}

@Serializable
private data class BodyStore(
    val schemaVersion: Int,
    val writeOrder: Long = 0,
    val owners: Map<String, StoredOwner> = emptyMap()
) {
    fun owner(scope: AccountScope): StoredOwner = owners[scope.ownerId] ?: StoredOwner()

    fun withOwner(scope: AccountScope, owner: StoredOwner, order: Long = writeOrder): BodyStore =
        copy(writeOrder = order, owners = owners + (scope.ownerId to owner))
}

@Serializable
private data class StoredOwner(
    val entries: List<StoredEntry> = emptyList(),
    val profile: StoredProfile? = null
)

@Serializable
private data class StoredEntry(
    val id: String,
    val date: String,
    val weightKg: Double,
    val memo: String,
    val writeOrder: Long
) {
    fun toEntry() = BodyReadEntry(id, date, weightKg, memo)
}

@Serializable
private data class StoredProfile(val heightCm: Int, val createdAt: String, val updatedAt: String)
