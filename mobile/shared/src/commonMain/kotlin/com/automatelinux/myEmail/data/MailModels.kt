package com.automatelinux.myEmail.data

import kotlinx.serialization.Serializable

@Serializable
data class Unread(val people: Int = 0, val other: Int = 0)

@Serializable
data class StatusResponse(val ok: Boolean = false, val error: String? = null, val email: String = "", val unread: Unread = Unread())

@Serializable
data class ThreadSummary(
    val id: String,
    val from: String,
    val fromEmail: String,
    val subject: String,
    val snippet: String,
    val date: Long,
    val unread: Boolean,
    val count: Int,
)

@Serializable
data class InboxResponse(
    val ok: Boolean = false,
    val error: String? = null,
    val threads: List<ThreadSummary> = emptyList(),
    val nextPageToken: String? = null,
)

@Serializable
data class MessageView(
    val id: String,
    val from: String,
    val fromEmail: String,
    val to: String,
    val date: Long,
    val unread: Boolean,
    val body: String,
    val quoted: String,
    val attachments: List<String> = emptyList(),
)

@Serializable
data class ThreadView(val id: String, val subject: String, val inInbox: Boolean, val messages: List<MessageView>)

@Serializable
data class ThreadResponse(val ok: Boolean = false, val error: String? = null, val thread: ThreadView? = null)

@Serializable
data class OkResponse(val ok: Boolean = false, val error: String? = null, val to: String? = null)

enum class View(val key: String) { People("people"), Other("other") }
