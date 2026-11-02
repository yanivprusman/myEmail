package com.automatelinux.myEmail.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One HTTP round trip — Android supplies it (JDK client), iOS will supply NSURLSession. */
expect suspend fun httpRequest(method: String, url: String, token: String, jsonBody: String?): HttpResult

data class HttpResult(val code: Int, val body: String, val transportError: String? = null)

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * The app's whole conversation with its server. Failures are values with an
 * `error` sentence, never exceptions at the UI: "the VPN is down" must render
 * as words, not as a spinner that never ends.
 */
class MailApi(baseUrl: String, private val token: String) {
    private val base = baseUrl.trimEnd('/')
    val configured: Boolean get() = token.isNotEmpty()

    suspend fun status() = call("GET", "/api/status", null, StatusResponse.serializer()) { StatusResponse(error = it) }

    suspend fun inbox(pageToken: String? = null): InboxResponse {
        val page = pageToken?.let { "?pageToken=$it" } ?: ""
        return call("GET", "/api/inbox$page", null, InboxResponse.serializer()) { InboxResponse(error = it) }
    }

    suspend fun thread(id: String) = call("GET", "/api/thread/$id", null, ThreadResponse.serializer()) { ThreadResponse(error = it) }

    suspend fun act(id: String, action: String): OkResponse {
        val body = buildJsonObject { put("action", action) }.toString()
        return call("POST", "/api/thread/$id", body, OkResponse.serializer()) { OkResponse(error = it) }
    }

    suspend fun reply(id: String, text: String): OkResponse {
        val body = buildJsonObject { put("text", text) }.toString()
        return call("POST", "/api/thread/$id/reply", body, OkResponse.serializer()) { OkResponse(error = it) }
    }

    private suspend fun <T> call(method: String, path: String, body: String?, s: KSerializer<T>, fail: (String) -> T): T {
        if (!configured) return fail("This build has no server token — rebuild with mobile/.env")
        val r = httpRequest(method, "$base$path", token, body)
        if (r.code == 0) return fail("Can't reach the mail server — is the VPN on?")
        return runCatching { json.decodeFromString(s, r.body) }.getOrElse {
            fail(if (r.code == 401) "This phone isn't authorized (wrong token)" else "Unexpected reply from the server (${r.code})")
        }
    }
}
