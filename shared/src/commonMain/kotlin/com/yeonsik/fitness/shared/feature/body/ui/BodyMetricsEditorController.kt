package com.yeonsik.fitness.shared.feature.body.ui

import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitness.shared.feature.body.application.BodyMetricsApplicationService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface BodyMetricsEditorState {
    data object Idle : BodyMetricsEditorState
    data object Busy : BodyMetricsEditorState

    data class Ready(
        val requestId: Long,
        val account: AccountScope,
        val editor: BodyMetricsApplicationService.Editor
    ) : BodyMetricsEditorState

    data class Finished(
        val requestId: Long,
        val account: AccountScope,
        val notice: String,
        val recordsChanged: Boolean
    ) : BodyMetricsEditorState
}

/** UI actions run on the host's main scope. Storage operations run in submission order off-main. */
class BodyMetricsEditorController(
    private val service: BodyMetricsApplicationService,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val operations = Mutex()
    private val mutableState = MutableStateFlow<BodyMetricsEditorState>(BodyMetricsEditorState.Idle)
    val state = mutableState.asStateFlow()
    private var requestId = 0L

    fun open(account: AccountScope, date: String, recordId: String?) {
        submit(account, "체중 기록을 불러오지 못했습니다.") { request ->
            BodyMetricsEditorState.Ready(request, account, service.load(account, date, recordId))
        }
    }

    fun save(ready: BodyMetricsEditorState.Ready, date: String, weightKg: Double) {
        if (mutableState.value !== ready) return
        submit(ready.account, "체중 기록을 저장하지 못했습니다.") { request ->
            service.save(ready.account, ready.editor.recordId, date, weightKg, ready.editor.memo)
            BodyMetricsEditorState.Finished(request, ready.account, "체중 기록을 저장했습니다.", true)
        }
    }

    fun delete(ready: BodyMetricsEditorState.Ready) {
        if (mutableState.value !== ready) return
        val id = ready.editor.recordId ?: return
        submit(ready.account, "체중 기록을 삭제하지 못했습니다.") { request ->
            service.delete(ready.account, id)
            BodyMetricsEditorState.Finished(request, ready.account, "체중 기록을 삭제했습니다.", true)
        }
    }

    fun dismiss() {
        requestId++
        mutableState.value = BodyMetricsEditorState.Idle
    }

    internal fun dismiss(ready: BodyMetricsEditorState.Ready) {
        if (mutableState.value === ready) dismiss()
    }

    internal fun acknowledge(result: BodyMetricsEditorState.Finished) {
        if (mutableState.value === result) mutableState.value = BodyMetricsEditorState.Idle
    }

    private fun submit(
        account: AccountScope,
        failureMessage: String,
        run: (Long) -> BodyMetricsEditorState
    ) {
        val request = ++requestId
        mutableState.value = BodyMetricsEditorState.Busy
        scope.launch {
            val next = try {
                operations.withLock { withContext(ioDispatcher) { run(request) } }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                BodyMetricsEditorState.Finished(request, account, error.message ?: failureMessage, false)
            }
            if (request == requestId) mutableState.value = next
        }
    }
}
