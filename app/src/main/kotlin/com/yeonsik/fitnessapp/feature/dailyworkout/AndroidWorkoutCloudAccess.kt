package com.yeonsik.fitnessapp.feature.dailyworkout

import android.content.Context
import com.yeonsik.fitness.shared.feature.dailyworkout.WorkoutCloudAccess
import com.yeonsik.fitness.shared.feature.dailyworkout.WorkoutCloudAccount
import com.yeonsik.fitnessapp.config.NutritionSupabaseConfigStore
import com.yeonsik.fitnessapp.config.SupabaseConfig
import com.yeonsik.fitnessapp.config.SupabaseConfigStore
import com.yeonsik.fitnessapp.sync.SupabaseAuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI

internal class AndroidWorkoutCloudAccess(context: Context) : WorkoutCloudAccess {
    private val local = SupabaseConfigStore(context)
    private val initialLocal = local.load().let { it.supabaseUrl to it.effectiveUserId() }
    private val remote = NutritionSupabaseConfigStore(context)

    override fun account(): WorkoutCloudAccount? {
        val localConfig = local.load()
        if (initialLocal != (localConfig.supabaseUrl to localConfig.effectiveUserId())) return null
        val config = remote.load()
        val endpoint = runCatching { URI(config.supabaseUrl) }.getOrNull() ?: return null
        if (!config.isConfigured || endpoint.scheme != "https" || endpoint.userInfo != null || endpoint.host == null ||
            endpoint.port != -1 || !endpoint.path.isNullOrBlank() && endpoint.path != "/" ||
            endpoint.query != null || endpoint.fragment != null) return null
        return runCatching { WorkoutCloudAccount(endpoint.host.lowercase(), config.userId.lowercase(),
            config.email.ifBlank { config.userId }, localConfig.email.ifBlank { "이 기기의 로컬 기록" }) }.getOrNull()
    }

    override suspend fun rpc(account: WorkoutCloudAccount, function: String, body: String): String = withContext(Dispatchers.IO) {
        check(account() == account) { "동기화 계정이 변경되었습니다." }
        val original = remote.load()
        var response = post(original, function, body)
        if (response.first == 401) {
            check(account() == account) { "동기화 계정이 변경되었습니다." }
            val refreshed = SupabaseAuthManager(remote).refresh(original)
            check(account() == account && refreshed.userId.equals(account.owner, true)) { "동기화 계정이 변경되었습니다." }
            response = post(refreshed, function, body)
        }
        check(account() == account) { "동기화 계정이 변경되었습니다." }
        check(response.first in 200..299) {
            when (response.first) {
                401, 403 -> "Fitness 동기화 계정의 로그인을 확인하세요."
                404 -> "이 Fitness 서버에 완료 운동 동기화가 배포되지 않았습니다."
                409 -> "다른 기기의 변경이 있습니다. 다시 동기화해 확인하세요."
                else -> "서버에서 운동을 확인하지 못했습니다. 다시 동기화해 주세요."
            }
        }
        response.second
    }

    private fun post(config: SupabaseConfig, function: String, body: String): Pair<Int, String> {
        require(function in setOf("fitness_completed_workouts_v1", "fitness_save_completed_workout_v1", "fitness_completed_workout_summary_v1"))
        val conn = URI(config.supabaseUrl.trimEnd('/') + "/rest/v1/rpc/" + function).toURL().openConnection() as HttpURLConnection
        return try {
            conn.instanceFollowRedirects = false
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("apikey", config.supabaseAnonKey)
            conn.setRequestProperty("Authorization", "Bearer ${config.accessToken}")
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = conn.responseCode
            status to if (status in 200..299) conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() } else ""
        } finally { conn.disconnect() }
    }
}
