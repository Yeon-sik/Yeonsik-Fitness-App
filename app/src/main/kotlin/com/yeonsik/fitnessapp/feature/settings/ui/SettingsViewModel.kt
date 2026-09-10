package com.yeonsik.fitnessapp.feature.settings.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.yeonsik.fitnessapp.config.AppSurfacePolicy
import com.yeonsik.fitnessapp.config.MassUnitPreferences
import com.yeonsik.fitnessapp.config.NutritionSupabaseConfigStore
import com.yeonsik.fitnessapp.config.PriceTraceSupabaseConfigStore
import com.yeonsik.fitnessapp.config.SupabaseConfig
import com.yeonsik.fitnessapp.config.SupabaseConfigStore
import com.yeonsik.fitnessapp.config.ThemeModePreferences
import com.yeonsik.fitnessapp.data.MassUnit
import com.yeonsik.fitnessapp.integration.nutrition.NutritionIntegrationService
import com.yeonsik.fitnessapp.integration.sync.SyncApplicationService
import com.yeonsik.fitnessapp.integration.transfer.LocalDataTransferApplicationService
import com.yeonsik.fitnessapp.feature.settings.application.SettingsSessionCoordinator
import com.yeonsik.fitnessapp.sync.SupabaseAuthManager
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

enum class SettingsConnection {
    SHARED,
    NUTRITION,
    PRICE_TRACE
}

data class SettingsUiState(
    val themeMode: String,
    val preferredMassUnit: MassUnit,
    val syncLabel: String,
    val syncDetail: String,
    val isManualSyncing: Boolean,
    val isDataImporting: Boolean,
    val dataImportDetail: String,
    val isDataTransferInProgress: Boolean,
    val dataTransferDetail: String,
    val sharedConfig: SupabaseConfig,
    val sharedConnectionManaged: Boolean,
    val nutritionConfig: SupabaseConfig,
    val nutritionConnectionManaged: Boolean,
    val priceTraceConfig: SupabaseConfig,
    val priceTraceConnectionManaged: Boolean,
    val developerSurfaceAllowed: Boolean
)

sealed class SettingsEvent {
    private val consumed = AtomicBoolean(false)

    fun consume(): Boolean = consumed.compareAndSet(false, true)

    data class ConfigSaved(
        val connection: SettingsConnection,
        val config: SupabaseConfig,
        val message: String
    ) : SettingsEvent()

    data class Authenticated(
        val connection: SettingsConnection,
        val config: SupabaseConfig,
        val confirmationRequired: Boolean,
        val message: String
    ) : SettingsEvent()

    data class SignedOut(
        val connection: SettingsConnection,
        val config: SupabaseConfig,
        val message: String
    ) : SettingsEvent()

    data class SyncCompleted(val result: SyncApplicationService.Result) : SettingsEvent()

    data class BackupPreviewReady(
        val preview: LocalDataTransferApplicationService.BackupPreview
    ) : SettingsEvent()

    data class Notice(val message: String) : SettingsEvent()
    data class Failure(val message: String) : SettingsEvent()
}

class SettingsViewModel @JvmOverloads constructor(
    private val savedStateHandle: SavedStateHandle,
    private val massUnitPreferences: MassUnitPreferences,
    private val themeModePreferences: ThemeModePreferences,
    private val sharedConfigStore: SupabaseConfigStore,
    private val nutritionConfigStore: NutritionSupabaseConfigStore,
    private val priceTraceConfigStore: PriceTraceSupabaseConfigStore,
    private val sharedAuth: SupabaseAuthManager,
    private val nutritionAuth: SupabaseAuthManager,
    private val priceTraceAuth: SupabaseAuthManager,
    private val syncApplicationService: SyncApplicationService,
    private val localDataTransferApplicationService: LocalDataTransferApplicationService,
    private val sessionCoordinator: SettingsSessionCoordinator,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
) : ViewModel() {
    private val mutableState = MutableLiveData<SettingsUiState>()
    val uiState: LiveData<SettingsUiState> = mutableState
    private val mutableEvents = MutableLiveData<SettingsEvent>()
    val events: LiveData<SettingsEvent> = mutableEvents

    fun enter() {
        mutableState.value = snapshot()
    }

    fun refresh() {
        mutableState.postValue(snapshot())
    }

    fun reconcileSharedWorkoutSummaries(ownerId: String) {
        val normalizedOwnerId = ownerId.trim()
        if (normalizedOwnerId.isEmpty()) return
        executor.execute {
            runCatching {
                localDataTransferApplicationService.reconcileSharedWorkoutSummaries(
                    normalizedOwnerId
                )
            }
        }
    }

    fun setThemeMode(mode: String) {
        themeModePreferences.setThemeMode(mode)
        savedStateHandle[KEY_THEME_MODE] = mode
        mutableState.value = snapshot()
    }

    fun setPreferredMassUnit(unit: MassUnit) {
        massUnitPreferences.setPreferredMassUnit(unit)
        mutableState.value = snapshot()
    }

    fun themeMode(): String = savedStateHandle[KEY_THEME_MODE] ?: themeModePreferences.themeMode()

    fun preferredMassUnit(): MassUnit = massUnitPreferences.preferredMassUnit()

    /** Platform file pickers use this guard without owning transfer progress state. */
    fun canStartDataFileOperation(): Boolean {
        val state = mutableState.value ?: return true
        return !state.isDataImporting && !state.isDataTransferInProgress
    }

    fun saveConnection(connection: SettingsConnection, url: String, anonKey: String) {
        executor.execute {
            try {
                val config = store(connection).saveConnection(url, anonKey)
                sessionCoordinator.apply(connection, config, false)
                mutableEvents.postValue(
                    SettingsEvent.ConfigSaved(connection, config, connectionSavedMessage(connection))
                )
                mutableState.postValue(snapshot())
            } catch (error: Exception) {
                fail(error.message ?: "연결 설정을 저장하지 못했습니다.")
            }
        }
    }

    fun signIn(connection: SettingsConnection, email: String, password: String) {
        val store = store(connection)
        val config = store.load()
        if (!config.isConnectionConfigured) {
            fail(connectionRequiredMessage(connection))
            return
        }
        setAuthenticationState("authenticating", authenticatingMessage(connection))
        executor.execute {
            try {
                val authenticated = auth(connection).signIn(config, email, password)
                applyAuthenticatedConfig(connection, authenticated)
                mutableEvents.postValue(
                    SettingsEvent.Authenticated(
                        connection,
                        authenticated,
                        false,
                        authenticationSuccessMessage(connection)
                    )
                )
                mutableState.postValue(snapshot())
            } catch (error: Exception) {
                failAuthentication(error, connection)
            }
        }
    }

    fun signUp(connection: SettingsConnection, email: String, password: String) {
        val store = store(connection)
        val config = store.load()
        if (!config.isConnectionConfigured) {
            fail(connectionRequiredMessage(connection))
            return
        }
        setAuthenticationState("authenticating", signupMessage(connection))
        executor.execute {
            try {
                val result = auth(connection).signUp(config, email, password)
                if (!result.emailConfirmationRequired) {
                    applyAuthenticatedConfig(connection, result.config)
                }
                mutableEvents.postValue(
                    SettingsEvent.Authenticated(
                        connection,
                        result.config,
                        result.emailConfirmationRequired,
                        if (result.emailConfirmationRequired) {
                            confirmationMessage(connection)
                        } else {
                            authenticationCreatedMessage(connection)
                        }
                    )
                )
                mutableState.postValue(snapshot())
            } catch (error: Exception) {
                failAuthentication(error, connection)
            }
        }
    }

    fun signOut(connection: SettingsConnection) {
        executor.execute {
            try {
                val config = store(connection).clearSession()
                sessionCoordinator.apply(connection, config, false)
                mutableEvents.postValue(
                    SettingsEvent.SignedOut(connection, config, signoutMessage(connection))
                )
                mutableState.postValue(snapshot())
            } catch (error: Exception) {
                fail(error.message ?: "로그아웃하지 못했습니다.")
            }
        }
    }

    fun runManualSync() {
        val sharedConfig = sharedConfigStore.load()
        if (!sharedConfig.isConfigured) {
            fail("Supabase 연결 설정을 저장하고 계정에 로그인하세요.")
            return
        }
        setSyncState(true, "syncing", "공통 DB와 영양 DB를 각각 동기화하는 중입니다.")
        executor.execute {
            try {
                val result = syncApplicationService.run(
                    sharedConfig,
                    nutritionConfigStore.load()
                )
                sessionCoordinator.applySync(result)
                savedStateHandle[KEY_LAST_SYNCED_AT] = result.sharedResult.syncedAt
                val summaryFailed = !result.sharedResult.summaryPublicationSucceeded
                val partial = result.nutritionFailed || summaryFailed
                val legacyPushedRows = result.sharedResult.pushedRows - result.sharedResult.summaryPublishedRows
                val summaryStatus = if (summaryFailed) {
                    "Summary v2 실패: ${result.sharedResult.summaryPublicationError}"
                } else {
                    "Summary v2 ${result.sharedResult.summaryPublishedRows}건"
                }
                setSyncState(
                    false,
                    if (partial) "partial" else "synced",
                    "공통 DB push ${legacyPushedRows}건 · pull ${result.sharedResult.pulledRows}건 · " +
                        "$summaryStatus · ${result.nutritionStatus}"
                )
                mutableEvents.postValue(SettingsEvent.SyncCompleted(result))
            } catch (error: Exception) {
                setSyncState(false, "sync failed", error.message ?: "동기화에 실패했습니다.")
                fail("수동 동기화에 실패했습니다.")
            }
        }
    }

    fun writeBackup(recordOwnerId: String, nutritionOwnerId: String, output: OutputStream) {
        beginTransfer("백업 파일을 만드는 중입니다.")
        executor.execute {
            try {
                output.use {
                    localDataTransferApplicationService.writeBackup(recordOwnerId, nutritionOwnerId, it)
                }
                completeTransfer("전체 백업을 저장했습니다.")
            } catch (error: Exception) {
                failTransfer(error, "백업을 저장하지 못했습니다.")
            }
        }
    }

    fun previewBackup(recordOwnerId: String, nutritionOwnerId: String, input: InputStream) {
        beginTransfer("백업 파일을 확인하는 중입니다.")
        executor.execute {
            try {
                val preview = input.use {
                    localDataTransferApplicationService.previewBackup(recordOwnerId, nutritionOwnerId, it)
                }
                mutableState.postValue(
                    currentState().copy(
                        isDataTransferInProgress = false,
                        dataTransferDetail = "${preview.totalRows}개 항목 확인됨"
                    )
                )
                mutableEvents.postValue(SettingsEvent.BackupPreviewReady(preview))
            } catch (error: Exception) {
                failTransfer(error, "백업 파일을 확인하지 못했습니다.")
            }
        }
    }

    fun restoreBackup(recordOwnerId: String, nutritionOwnerId: String, input: InputStream) {
        beginTransfer("백업을 복원하는 중입니다.")
        executor.execute {
            try {
                val result = input.use {
                    localDataTransferApplicationService.restoreBackup(recordOwnerId, nutritionOwnerId, it)
                }
                completeTransfer("${result.importedRows}개 복원 · ${result.skippedRows}개 중복 건너뜀")
            } catch (error: Exception) {
                failTransfer(error, "백업을 복원하지 못했습니다.")
            }
        }
    }

    fun writeRecordsCsv(recordOwnerId: String, nutritionOwnerId: String, output: OutputStream) {
        beginTransfer("기록 요약 CSV를 만드는 중입니다.")
        executor.execute {
            try {
                output.use {
                    localDataTransferApplicationService.writeRecordsSummaryCsv(recordOwnerId, nutritionOwnerId, it)
                }
                completeTransfer("기록 요약 CSV를 저장했습니다.")
            } catch (error: Exception) {
                failTransfer(error, "CSV를 저장하지 못했습니다.")
            }
        }
    }

    fun writeWorkoutTransfer(ownerId: String, output: OutputStream) {
        beginTransfer("운동 전송 JSON을 만드는 중입니다.")
        executor.execute {
            try {
                output.use { localDataTransferApplicationService.writeWorkoutTransfer(ownerId, it) }
                completeTransfer("Workout Transfer v2 JSON을 저장했습니다.")
            } catch (error: Exception) {
                failTransfer(error, "운동 전송 JSON을 저장하지 못했습니다.")
            }
        }
    }

    fun importWorkoutTransfer(ownerId: String, input: InputStream) {
        beginTransfer("운동 전송 JSON을 읽고 기록을 합치는 중입니다.")
        executor.execute {
            try {
                val result = input.use { localDataTransferApplicationService.importWorkoutTransfer(ownerId, it) }
                completeTransfer(result.summary())
            } catch (error: Exception) {
                failTransfer(error, "운동 전송 JSON을 가져오지 못했습니다.")
            }
        }
    }

    fun importFleek(ownerId: String, input: InputStream) {
        beginImport("CSV를 읽고 운동 기록을 변환하는 중입니다.")
        executor.execute {
            try {
                val result = input.use { localDataTransferApplicationService.importFleek(ownerId, it) }
                mutableState.postValue(
                    currentState().copy(
                        isDataImporting = false,
                        dataImportDetail = result.summary()
                    )
                )
                mutableEvents.postValue(
                    SettingsEvent.Notice(
                        if (result.importedSessions > 0) {
                            "FLEEK 운동 기록을 가져왔습니다."
                        } else {
                            "이미 가져온 기록이라 새로 저장된 세션이 없습니다."
                        }
                    )
                )
            } catch (error: Exception) {
                val message = error.message ?: "FLEEK CSV 가져오기에 실패했습니다."
                mutableState.postValue(
                    currentState().copy(isDataImporting = false, dataImportDetail = message)
                )
                mutableEvents.postValue(SettingsEvent.Failure("FLEEK CSV 가져오기에 실패했습니다."))
            }
        }
    }

    private fun snapshot(): SettingsUiState {
        val shared = sharedConfigStore.load()
        val nutrition = nutritionConfigStore.load()
        val priceTrace = priceTraceConfigStore.load()
        val lastSyncedAt = savedStateHandle[KEY_LAST_SYNCED_AT] ?: ""
        val syncLabel: String
        val syncDetail: String
        if (shared.isConfigured) {
            syncLabel = if (lastSyncedAt.isEmpty()) "configured" else "synced"
            syncDetail = if (lastSyncedAt.isEmpty()) {
                "공통 DB 계정 연결됨 · ${nutritionConnectionSummary(nutrition)}"
            } else {
                "마지막 동기화 $lastSyncedAt"
            }
        } else {
            syncLabel = if (shared.isConnectionConfigured) "login required" else "local-only"
            syncDetail = if (shared.isConnectionConfigured) {
                "공통 DB는 연결되었습니다. 공통 계정 로그인이 필요합니다. · " +
                    nutritionConnectionSummary(nutrition)
            } else {
                "Personal OS 공통 DB 설정이 없습니다. · ${nutritionConnectionSummary(nutrition)}"
            }
        }
        val current = mutableState.value
        return SettingsUiState(
            themeMode = themeMode(),
            preferredMassUnit = massUnitPreferences.preferredMassUnit(),
            syncLabel = current?.syncLabel?.takeIf { current.isManualSyncing || it == "authenticating" || it == "authentication failed" || it == "sync failed" }
                ?: syncLabel,
            syncDetail = current?.syncDetail?.takeIf { current.isManualSyncing || it == "authenticating" || it == "authentication failed" || it == "sync failed" }
                ?: syncDetail,
            isManualSyncing = current?.isManualSyncing ?: false,
            isDataImporting = current?.isDataImporting ?: false,
            dataImportDetail = current?.dataImportDetail ?: "",
            isDataTransferInProgress = current?.isDataTransferInProgress ?: false,
            dataTransferDetail = current?.dataTransferDetail ?: "",
            sharedConfig = shared,
            sharedConnectionManaged = sharedConfigStore.isConnectionManaged(),
            nutritionConfig = nutrition,
            nutritionConnectionManaged = nutritionConfigStore.isConnectionManaged(),
            priceTraceConfig = priceTrace,
            priceTraceConnectionManaged = priceTraceConfigStore.isConnectionManaged(),
            developerSurfaceAllowed = AppSurfacePolicy.allowsDeveloperSurface()
        )
    }

    private fun currentState(): SettingsUiState = mutableState.value ?: snapshot()

    private fun store(connection: SettingsConnection): SupabaseConfigStore = when (connection) {
        SettingsConnection.SHARED -> sharedConfigStore
        SettingsConnection.NUTRITION -> nutritionConfigStore
        SettingsConnection.PRICE_TRACE -> priceTraceConfigStore
    }

    private fun auth(connection: SettingsConnection): SupabaseAuthManager = when (connection) {
        SettingsConnection.SHARED -> sharedAuth
        SettingsConnection.NUTRITION -> nutritionAuth
        SettingsConnection.PRICE_TRACE -> priceTraceAuth
    }

    private fun setAuthenticationState(label: String, detail: String) {
        mutableState.value = currentState().copy(syncLabel = label, syncDetail = detail)
    }

    private fun failAuthentication(error: Exception, connection: SettingsConnection) {
        setAuthenticationState(
            "authentication failed",
            error.message ?: authenticationFailureMessage(connection)
        )
        mutableEvents.postValue(SettingsEvent.Failure(authenticationFailureMessage(connection)))
    }

    private fun applyAuthenticatedConfig(connection: SettingsConnection, config: SupabaseConfig) {
        try {
            sessionCoordinator.apply(connection, config, true)
        } catch (error: RuntimeException) {
            runCatching {
                sessionCoordinator.apply(connection, store(connection).clearSession(), false)
            }
            throw error
        }
    }

    private fun fail(message: String) {
        mutableEvents.postValue(SettingsEvent.Failure(message))
    }

    private fun setSyncState(syncing: Boolean, label: String, detail: String) {
        mutableState.postValue(
            currentState().copy(
                isManualSyncing = syncing,
                syncLabel = label,
                syncDetail = detail
            )
        )
    }

    private fun beginTransfer(detail: String) {
        mutableState.postValue(
            currentState().copy(isDataTransferInProgress = true, dataTransferDetail = detail)
        )
    }

    private fun beginImport(detail: String) {
        mutableState.postValue(
            currentState().copy(isDataImporting = true, dataImportDetail = detail)
        )
    }

    private fun completeTransfer(message: String) {
        mutableState.postValue(
            currentState().copy(isDataTransferInProgress = false, dataTransferDetail = message)
        )
        mutableEvents.postValue(SettingsEvent.Notice(message))
    }

    private fun failTransfer(error: Exception, fallback: String) {
        val message = error.message?.takeIf { it.isNotBlank() } ?: fallback
        mutableState.postValue(
            currentState().copy(isDataTransferInProgress = false, dataTransferDetail = message)
        )
        mutableEvents.postValue(SettingsEvent.Failure(message))
    }

    private fun nutritionConnectionSummary(config: SupabaseConfig): String = when {
        config.isConfigured -> "영양 DB 계정 연결됨"
        config.isConnectionConfigured -> "영양 DB 공개 카탈로그 연결됨"
        else -> "영양 DB 연결 없음"
    }

    private fun connectionSavedMessage(connection: SettingsConnection) = when (connection) {
        SettingsConnection.SHARED -> "Personal OS 공통 DB 설정을 저장했습니다."
        SettingsConnection.NUTRITION -> "영양 전용 DB 설정을 저장했습니다."
        SettingsConnection.PRICE_TRACE -> "PriceTrace DB 설정을 저장했습니다."
    }

    private fun connectionRequiredMessage(connection: SettingsConnection) = when (connection) {
        SettingsConnection.SHARED -> "Personal OS 공통 DB 설정이 없습니다. 연결 설정을 먼저 확인하세요."
        SettingsConnection.NUTRITION -> "영양 전용 DB 설정이 없습니다. 연결 설정을 먼저 확인하세요."
        SettingsConnection.PRICE_TRACE -> "PriceTrace DB 설정이 없습니다. 연결 설정을 먼저 확인하세요."
    }

    private fun authenticatingMessage(connection: SettingsConnection) = when (connection) {
        SettingsConnection.SHARED -> "Supabase 계정에 로그인하는 중입니다."
        SettingsConnection.NUTRITION -> "영양 DB 계정에 로그인하는 중입니다."
        SettingsConnection.PRICE_TRACE -> "PriceTrace 계정에 로그인하는 중입니다."
    }

    private fun signupMessage(connection: SettingsConnection) = when (connection) {
        SettingsConnection.SHARED -> "Supabase 계정을 만드는 중입니다."
        SettingsConnection.NUTRITION -> "영양 DB 계정을 만드는 중입니다."
        SettingsConnection.PRICE_TRACE -> "PriceTrace 계정을 만드는 중입니다."
    }

    private fun authenticationSuccessMessage(connection: SettingsConnection) = when (connection) {
        SettingsConnection.SHARED -> "Personal OS 공통 계정으로 로그인했습니다."
        SettingsConnection.NUTRITION -> "영양 DB 계정으로 로그인했습니다."
        SettingsConnection.PRICE_TRACE -> "PriceTrace 계정으로 로그인했습니다."
    }

    private fun authenticationCreatedMessage(connection: SettingsConnection) = when (connection) {
        SettingsConnection.SHARED -> "Personal OS 공통 계정이 생성되고 로그인되었습니다."
        SettingsConnection.NUTRITION -> "영양 DB 계정이 생성되고 로그인되었습니다."
        SettingsConnection.PRICE_TRACE -> "PriceTrace 계정이 생성되고 로그인되었습니다."
    }

    private fun confirmationMessage(connection: SettingsConnection) = when (connection) {
        SettingsConnection.SHARED -> "가입 확인 메일을 확인한 뒤 로그인하세요."
        SettingsConnection.NUTRITION -> "영양 DB 가입 확인 메일을 확인한 뒤 로그인하세요."
        SettingsConnection.PRICE_TRACE -> "PriceTrace 가입 확인 메일을 확인한 뒤 로그인하세요."
    }

    private fun authenticationFailureMessage(connection: SettingsConnection) = when (connection) {
        SettingsConnection.SHARED -> "로그인 또는 계정 생성에 실패했습니다."
        SettingsConnection.NUTRITION -> "영양 DB 인증에 실패했습니다."
        SettingsConnection.PRICE_TRACE -> "PriceTrace 인증에 실패했습니다."
    }

    private fun signoutMessage(connection: SettingsConnection) = when (connection) {
        SettingsConnection.SHARED -> "공통 계정에서 로그아웃했습니다. 영양 DB 세션은 유지됩니다."
        SettingsConnection.NUTRITION -> "영양 DB 계정에서 로그아웃했습니다. 공통 계정 세션은 유지됩니다."
        SettingsConnection.PRICE_TRACE -> "PriceTrace 계정에서 로그아웃했습니다."
    }

    override fun onCleared() {
        executor.shutdownNow()
    }

    private companion object {
        const val KEY_THEME_MODE = "settings.theme_mode"
        const val KEY_LAST_SYNCED_AT = "settings.last_synced_at"
    }
}
