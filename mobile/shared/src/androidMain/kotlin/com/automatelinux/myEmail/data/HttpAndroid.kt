package com.automatelinux.myEmail.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** The Android half of [httpRequest], on the JDK client — one host, a handful
 *  of request shapes; a library would be more to keep in lockstep than it saves. */
actual suspend fun httpRequest(
    method: String,
    url: String,
    token: String,
    jsonBody: String?,
): HttpResult = withContext(Dispatchers.IO) {
    var conn: HttpURLConnection? = null
    try {
        conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 45_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            if (jsonBody != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
            }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        HttpResult(code, stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "")
    } catch (e: IOException) {
        HttpResult(0, "", e.message ?: "no connection")
    } finally {
        conn?.disconnect()
    }
}
