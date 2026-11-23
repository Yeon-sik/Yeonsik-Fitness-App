package com.yeonsik.fitnessapp.integration.wearable

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yeonsik.fitnessapp.config.SupabaseConfigStore
import com.yeonsik.fitnessapp.app.AppContainer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.*

data class WearableImportState(
    val date: String = LocalDate.now().toString(), val candidates: List<WearableWorkout> = emptyList(),
    val saved: List<WearableWorkout> = emptyList(), val selected: WearableWorkout? = null,
    val busy: Boolean = false, val message: String = "", val target: String? = null,
    val accountValid: Boolean = true
)

class WearableImportViewModel(application: Application) : AndroidViewModel(application) {
    private val config = SupabaseConfigStore(application)
    private val owner = config.load().effectiveUserId()
    val reader = HealthConnectWorkoutReader(application)
    private val repository = AppContainer.wearableWorkoutRepository(application)
    private val mutable = MutableStateFlow(WearableImportState())
    val state = mutable.asStateFlow()
    private var initialized = false
    private fun ownerCurrent() = config.load().effectiveUserId() == owner
    fun enter(expectedOwner: String?, target: String?, date: String?) {
        if (!ownerCurrent() || expectedOwner != owner) {
            mutable.value = WearableImportState(accountValid = false, message = "계정이 변경됐습니다. 운동 화면에서 다시 열어 주세요.")
            return
        }
        if (initialized) return
        initialized = true
        mutable.value = mutable.value.copy(target = target, date = date ?: LocalDate.now().toString())
        refreshSaved()
    }
    fun resume() {
        if (!ownerCurrent()) mutable.value = WearableImportState(accountValid = false,
            message = "계정이 변경됐습니다. 운동 화면에서 다시 열어 주세요.")
    }
    fun date(value: String) { if (!mutable.value.busy) mutable.value = mutable.value.copy(date = value, candidates = emptyList()) }
    fun select(row: WearableWorkout?) { if (!mutable.value.busy) mutable.value = mutable.value.copy(selected = row) }
    fun unavailable() { mutable.value = mutable.value.copy(message = "이 기기에서 Health Connect를 사용할 수 없습니다. 설치 또는 업데이트를 확인하세요.") }
    fun permissionResult(granted: Set<String>) {
        mutable.value = mutable.value.copy(message = if (granted.containsAll(reader.permissions))
            "읽기 권한이 허용됐습니다. 날짜를 확인한 뒤 조회하세요." else "운동과 심박 읽기 권한이 필요합니다.")
    }
    fun query() {
        val date = runCatching { LocalDate.parse(mutable.value.date) }.getOrNull()
        if (date == null) { mutable.value = mutable.value.copy(message = "날짜를 yyyy-MM-dd 형식으로 입력하세요."); return }
        task("조회하지 못했습니다. Health Connect 설치·읽기 권한·날짜를 확인해 주세요.") {
            val rows = reader.read(date, ZoneId.systemDefault())
            check(ownerCurrent())
            mutable.value = mutable.value.copy(candidates = rows,
                message = if (rows.isEmpty()) "이 날짜의 운동이 없습니다. 밴드와 삼성 헬스 동기화 후 다시 조회하세요."
                else "${rows.size}건 조회 · ${LocalTime.now().withNano(0)} · 저장할 운동을 선택하세요.")
        }
    }
    fun saveSelected() {
        val row = mutable.value.selected ?: return
        val target = mutable.value.target
        task("저장하지 못했습니다. 계정·운동 날짜를 확인하세요. 다른 운동에 연결된 기록은 다시 연결할 수 없습니다.") {
            repository.save(owner, row, target, ZoneId.systemDefault(), ::ownerCurrent)
            val saved = repository.list(owner)
            check(ownerCurrent())
            mutable.value = mutable.value.copy(saved = saved, selected = null,
                message = if (target == null) "외부 운동과 심박을 저장했습니다. 같은 기록을 다시 가져와도 중복되지 않습니다."
                else "현재 Fitness 운동에 측정 기록을 연결했습니다. 세트 입력은 계속할 수 있습니다.")
        }
    }
    private fun refreshSaved() = task("저장된 기록을 읽지 못했습니다.") {
        val saved = repository.list(owner)
        check(ownerCurrent())
        mutable.value = mutable.value.copy(saved = saved, message = "날짜를 선택하고 조회하세요.")
    }
    private fun task(error: String, work: suspend () -> Unit) {
        if (mutable.value.busy || !mutable.value.accountValid) return
        viewModelScope.launch {
            mutable.value = mutable.value.copy(busy = true, message = "처리 중…")
            try { withContext(Dispatchers.IO) { check(ownerCurrent()); work() } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.value = mutable.value.copy(message = error) }
            finally { if (ownerCurrent()) mutable.value = mutable.value.copy(busy = false) else resume() }
        }
    }
}
