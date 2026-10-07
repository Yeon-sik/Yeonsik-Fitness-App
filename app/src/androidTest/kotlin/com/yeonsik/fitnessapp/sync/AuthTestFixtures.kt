package com.yeonsik.fitnessapp.sync

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** Uses independent preference files and never changes the application's real accounts. */
internal class AuthTestContext(context: Context, private val prefix: String = UUID.randomUUID().toString()) : ContextWrapper(context) {
    private val files = mutableSetOf<String>()
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        val isolated = "auth-test-$prefix-$name"
        files.add(isolated)
        return super.getSharedPreferences(isolated, mode)
    }
    fun clean() = files.forEach { super.getSharedPreferences(it, MODE_PRIVATE).edit().clear().commit() }
}

internal class AuthTestConnection(
    url: URL,
    private val status: Int,
    private val response: String,
    private val beforeResponse: () -> Unit = {}
) : HttpURLConnection(url) {
    val sentBody = ByteArrayOutputStream()
    var disconnected = false
    override fun connect() = Unit
    override fun usingProxy() = false
    override fun disconnect() { disconnected = true }
    override fun getOutputStream() = sentBody
    override fun getResponseCode(): Int { beforeResponse(); return status }
    override fun getInputStream() = ByteArrayInputStream(response.toByteArray(Charsets.UTF_8))
    override fun getErrorStream() = ByteArrayInputStream(response.toByteArray(Charsets.UTF_8))
}

internal fun sessionResponse(owner: String = "owner-a") = """
    {"access_token":"test-access","refresh_token":"test-refresh","user":{"id":"$owner","email":"person@example.com"}}
""".trimIndent()
