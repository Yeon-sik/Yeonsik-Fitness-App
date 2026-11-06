package com.yeonsik.fitnessapp.feature.information.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yeonsik.fitnessapp.config.SupabaseConfigStore
import com.yeonsik.fitnessapp.config.SupabaseConfig
import com.yeonsik.fitnessapp.config.NutritionSupabaseConfigStore
import com.yeonsik.fitnessapp.core.database.*
import com.yeonsik.fitnessapp.feature.information.data.*
import com.yeonsik.fitnessapp.feature.information.model.*
import com.yeonsik.fitnessapp.integration.nutrition.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.Instant
import java.util.UUID

data class InformationInboxUiState(
    val records: List<InformationInboxSummary> = emptyList(),
    val draft: String? = null,
    val editor: String? = null,
    val selected: InformationInboxEntity? = null,
    val ownerReviewed: Boolean = false,
    val busy: Boolean = false,
    val message: String = "",
    val error: String = "",
    val remoteMapping: String? = null,
    val hasMoreRemoteMeals: Boolean = false
)

class InformationInboxViewModel(application: Application) : AndroidViewModel(application) {
    private val config = SupabaseConfigStore(application)
    private val profile = currentProfile()
    private val repository = InformationInboxRepository(FitnessRoomDatabaseProvider.get(application), profile)
    private val nutritionConfig = NutritionSupabaseConfigStore(application)
    private val remoteMeals = NutritionMealReviewClient()
    private val remoteImporter = NutritionMealReviewImporter(FitnessRoomDatabaseProvider.get(application), profile)
    private val mutableState = MutableStateFlow(InformationInboxUiState())
    val state = mutableState.asStateFlow()
    private var pendingExport: Pair<InformationProfile, String>? = null
    private var pendingRemote: Pair<SupabaseConfig, String?>? = null
    private var nextRemote: Pair<NutritionMealSource, String>? = null

    init { work { refresh() } }

    private fun currentProfile(): InformationProfile = config.load().let {
        InformationProfile(it.effectiveUserId(), if (it.userId.isBlank()) "local" else it.supabaseUrl)
    }
    private fun requireCurrentProfile() {
        check(currentProfile() == profile) { "계정 또는 연결이 변경되었습니다. 검토함을 닫고 다시 열어 주세요." }
    }
    private suspend fun refresh() {
        val rows = repository.records()
        val draft = repository.draft()
        withContext(Dispatchers.Main) { mutableState.value = mutableState.value.copy(records = rows, draft = draft) }
    }
    private fun work(action: suspend () -> Unit) {
        if (mutableState.value.busy) return
        mutableState.value = mutableState.value.copy(busy = true, error = "", message = "")
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { requireCurrentProfile(); action(); requireCurrentProfile() } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutableState.value = mutableState.value.copy(error = e.message ?: "저장하지 못했습니다. 다시 시도하세요.") }
            finally { mutableState.value = mutableState.value.copy(busy = false) }
        }
    }
    private suspend fun message(text: String) = withContext(Dispatchers.Main) {
        mutableState.value = mutableState.value.copy(message = text)
    }
    fun edit(text: String) { mutableState.value = mutableState.value.copy(editor = text, ownerReviewed = false) }
    fun ownerReviewed(value: Boolean) { mutableState.value = mutableState.value.copy(ownerReviewed = value) }
    fun select(row: InformationInboxSummary?) {
        if (row == null) mutableState.value = mutableState.value.copy(selected = null, editor = null, error = "", message = "")
        else work {
            val selected = repository.record(row.recordId)
            withContext(Dispatchers.Main) { mutableState.value = mutableState.value.copy(selected = selected, editor = null) }
        }
    }
    fun resumeDraft() { edit(mutableState.value.draft ?: return) }
    fun closeEditor() { mutableState.value = mutableState.value.copy(editor = null, ownerReviewed = false) }

    fun prepareRemoteMeals(nextPage: Boolean = false) = work {
        val remote = nutritionConfig.load()
        require(remote.isConfigured) { "설정의 Nutrition 계정에 로그인한 뒤 다시 가져오세요." }
        val source = NutritionMealSource(remote.supabaseUrl, remote.userId)
        val cursor = if (nextPage) nextRemote?.also {
            check(it.first == source) { "Nutrition 계정이 변경되었습니다. 처음부터 가져오세요." }
        }?.second ?: error("처음부터 가져오기를 선택하세요.") else null
        pendingRemote = remote to cursor
        withContext(Dispatchers.Main) {
            mutableState.value = mutableState.value.copy(remoteMapping =
                "가져올 Nutrition 계정\n${remote.email.ifBlank { remote.userId }}\n${remote.userId}\n${remote.supabaseUrl}\n\n" +
                "저장할 Fitness 프로필\n${profile.userId}\n${profile.project}\n\n" +
                "두 계정이 본인의 계정인지 확인하세요. ${if (cursor == null) "처음" else "다음"} 최대 ${NutritionMealReviewClient.PAGE_SIZE}건을 검토 대기에 저장합니다. 누락된 영양값과 원문을 보존하며 식사 집계에는 반영하지 않습니다.")
        }
    }
    fun dismissRemoteMeals() {
        pendingRemote = null
        mutableState.value = mutableState.value.copy(remoteMapping = null)
    }
    fun importRemoteMeals() {
        val request = pendingRemote ?: return
        pendingRemote = null
        mutableState.value = mutableState.value.copy(remoteMapping = null)
        work {
            val remote = request.first
            val source = NutritionMealSource(remote.supabaseUrl, remote.userId)
            fun checkMapping() {
                requireCurrentProfile()
                val current = nutritionConfig.load()
                check(current.isConfigured && current.supabaseUrl == remote.supabaseUrl &&
                    current.userId == remote.userId && current.supabaseAnonKey == remote.supabaseAnonKey &&
                    current.accessToken == remote.accessToken) { "Nutrition 계정 또는 연결이 변경되었습니다. 계정을 다시 확인한 뒤 가져오세요." }
            }
            checkMapping()
            val page = remoteMeals.pull(remote, request.second)
            val result = remoteImporter.importPage(source, page, ::checkMapping)
            nextRemote = page.nextCursor?.let { source to it }
            refresh()
            withContext(Dispatchers.Main) { mutableState.value = mutableState.value.copy(hasMoreRemoteMeals = nextRemote != null) }
            message("원격 식사 ${result.inserted}건을 검토 대기에 저장했습니다. 동일 기록 ${result.duplicates}건은 유지했습니다." +
                if (nextRemote != null) " 다음 자료도 가져올 수 있습니다." else " 현재 페이지까지 모두 확인했습니다.")
        }
    }

    fun create() {
        val record = buildJsonObject {
            put("schema_version", "information-record.v1"); put("record_id", UUID.randomUUID().toString())
            put("owner_app", "fitness"); put("record_type", "fitness.activity_observation")
            put("title", "새 활동 기록"); put("captured_at", Instant.now().toString())
            putJsonArray("sources") { add(buildJsonObject { put("source_id", "manual-1"); put("kind", "manual") }) }
            putJsonArray("fields") { add(buildJsonObject {
                put("key", "note"); put("label", "관찰 내용"); put("value_type", "text"); put("value", "")
                putJsonArray("source_ids") { add("manual-1") }
            }) }
            putJsonArray("references") {}
        }
        edit(InformationRecordCodec.pretty(InformationRecordCodec.decode(record.toString(), InformationOwner.FITNESS)))
    }
    fun correction() = work {
        val id = mutableState.value.selected?.recordId ?: return@work
        val old = InformationRecordCodec.decode(repository.export(id), InformationOwner.FITNESS)
        val payload = JsonObject(old.payload + mapOf("record_id" to JsonPrimitive(UUID.randomUUID().toString()),
            "supersedes_record_id" to JsonPrimitive(id), "captured_at" to JsonPrimitive(Instant.now().toString())))
        val text = InformationRecordCodec.pretty(InformationRecordCodec.decode(payload.toString(), InformationOwner.FITNESS))
        withContext(Dispatchers.Main) { edit(text) }
    }
    fun saveDraft() {
        val text = mutableState.value.editor ?: return
        work { repository.saveDraft(text); refresh(); message("초안을 보관했습니다. 아직 기록을 가져오지 않았습니다.") }
    }
    fun importEditor() {
        val text = mutableState.value.editor ?: return
        val reviewed = mutableState.value.ownerReviewed
        work {
            val inserted = repository.importRecord(text, reviewed)
            refresh()
            withContext(Dispatchers.Main) { mutableState.value = mutableState.value.copy(editor = null, selected = null, ownerReviewed = false) }
            message(if (inserted) "검토 대기에 저장했습니다." else "동일한 기록이 이미 있습니다. 중복 저장하지 않았습니다.")
        }
    }
    fun changeState(state: InformationReviewState) {
        val id = mutableState.value.selected?.recordId ?: return
        work {
            repository.changeState(id, state); refresh()
            val selected = repository.record(id)
            withContext(Dispatchers.Main) { mutableState.value = mutableState.value.copy(selected = selected) }
            message("${state.label} 상태로 변경했습니다. 원문은 유지됩니다.")
        }
    }
    fun readFile(uri: Uri) = work {
        val bytes = getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= InformationRecordCodec.MAX_BYTES) { "파일은 UTF-8 1 MiB 이하여야 합니다." }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: error("선택한 파일을 열 수 없습니다.")
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        InformationRecordCodec.decode(text, InformationOwner.FITNESS)
        repository.saveDraft(text)
        refresh()
        withContext(Dispatchers.Main) { edit(text) }
        message("파일을 읽었습니다. 소유 범위를 확인한 뒤 검토 대기에 저장하세요.")
    }
    fun prepareExport() {
        pendingExport = mutableState.value.selected?.let { profile to it.recordId }
    }
    fun exportFile(uri: Uri) = work {
        val request = pendingExport ?: error("내보낼 기록을 다시 선택하세요.")
        check(request.first == profile)
        val text = repository.export(request.second)
        getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: error("선택한 위치에 파일을 저장할 수 없습니다.")
        pendingExport = null
        message("JSON 파일로 내보냈습니다.")
    }
}
