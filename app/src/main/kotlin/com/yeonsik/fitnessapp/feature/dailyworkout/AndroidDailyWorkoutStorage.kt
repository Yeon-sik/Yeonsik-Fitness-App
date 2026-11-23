package com.yeonsik.fitnessapp.feature.dailyworkout

import android.content.Context
import android.util.AtomicFile
import com.yeonsik.fitness.shared.feature.dailyworkout.DailyWorkoutStorage
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest

internal class AndroidDailyWorkoutStorage(context: Context, owner: String, project: String) : DailyWorkoutStorage {
    private val directory by lazy { File(context.noBackupFilesDir, "daily-workout/${profileKey(owner, project)}") }

    override fun read(name: String): String? = try {
        file(name).readFully().toString(Charsets.UTF_8)
    } catch (missing: FileNotFoundException) {
        if (file(name).baseFile.exists()) throw missing
        null
    }

    override fun write(name: String, text: String) {
        check(directory.isDirectory || directory.mkdirs()) { "하루 운동 기록 폴더를 열지 못했습니다." }
        val target = file(name)
        val stream = target.startWrite()
        try {
            stream.write(text.toByteArray(Charsets.UTF_8))
            target.finishWrite(stream)
        } catch (error: Exception) {
            target.failWrite(stream)
            throw error
        }
    }

    private fun file(name: String) = AtomicFile(File(directory, "$name.json"))

    private fun profileKey(owner: String, project: String): String =
        MessageDigest.getInstance("SHA-256").digest("$owner\u0000$project".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
