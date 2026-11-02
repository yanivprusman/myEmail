package com.automatelinux.myEmail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import com.automatelinux.myEmail.data.MailApi
import com.automatelinux.myEmail.data.ThreadSummary
import com.automatelinux.myEmail.platform.PlatformBackHandler
import com.automatelinux.myEmail.platform.StatusBarIcons
import com.automatelinux.myEmail.ui.InboxScreen
import com.automatelinux.myEmail.ui.ThreadScreen
import com.automatelinux.myEmail.ui.theme.AppTheme
import kotlinx.coroutines.launch

/** The inbox list as the screen sees it. */
data class ListState(
    val threads: List<ThreadSummary> = emptyList(),
    val nextPageToken: String? = null,
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
)

/**
 * The whole app: one inbox and one open thread.
 *
 * Archive and Trash leave the list at once and offer Undo, instead of asking
 * "are you sure?" — clearing mail is the most frequent thing done here, and a
 * confirmation on every one of them is a tax; an undo on the rare mistake isn't.
 */
@Composable
fun App(baseUrl: String, token: String, fonts: FontFamily = FontFamily.Default) {
    val api = remember(baseUrl, token) { MailApi(baseUrl, token) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var list by remember { mutableStateOf(ListState()) }
    var unread by remember { mutableStateOf(0) }
    var account by remember { mutableStateOf("") }
    var statusError by remember { mutableStateOf<String?>(null) }
    var openId by remember { mutableStateOf<String?>(null) }
    // Hoisted so coming back from a thread lands exactly where you left the list.
    val listState = rememberLazyListState()

    fun refreshStatus() = scope.launch {
        val s = api.status()
        statusError = s.error
        if (s.ok) { account = s.email; unread = s.unread }
    }

    fun load(more: Boolean = false) {
        val cur = list
        if (cur.loading || (more && cur.nextPageToken == null)) return
        list = cur.copy(loading = true, error = null)
        scope.launch {
            val r = api.inbox(if (more) cur.nextPageToken else null)
            list = if (r.ok) list.copy(
                threads = if (more) (list.threads + r.threads).distinctBy { it.id } else r.threads,
                nextPageToken = r.nextPageToken, loading = false, loaded = true,
            ) else list.copy(loading = false, loaded = true, error = r.error)
        }
    }

    fun refresh() { load(); refreshStatus() }

    fun patch(id: String, f: (ThreadSummary) -> ThreadSummary) {
        list = list.copy(threads = list.threads.map { if (it.id == id) f(it) else it })
    }

    fun putBack(index: Int, t: ThreadSummary) {
        list = list.copy(threads = list.threads.toMutableList().apply { add(index.coerceAtMost(size), t) })
    }

    /** archive / trash: out of the list now, server second, Undo puts it back. */
    fun remove(id: String, action: String) {
        val t = list.threads.find { it.id == id } ?: return
        val index = list.threads.indexOf(t)
        list = list.copy(threads = list.threads - t)
        if (openId == id) openId = null
        scope.launch {
            val r = api.act(id, action)
            if (!r.ok) {
                putBack(index, t)
                snackbar.showSnackbar(r.error ?: "Couldn't $action")
                return@launch
            }
            refreshStatus()
            val label = if (action == "trash") "Moved to Trash" else "Archived"
            if (snackbar.showSnackbar(label, actionLabel = "Undo", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                val undo = api.act(id, if (action == "trash") "untrash" else "inbox")
                if (undo.ok) { putBack(index, t); refreshStatus() }
                else snackbar.showSnackbar(undo.error ?: "Undo failed")
            }
        }
    }

    fun markUnread(id: String) {
        patch(id) { it.copy(unread = true) }
        openId = null
        scope.launch { val r = api.act(id, "unread"); if (!r.ok) snackbar.showSnackbar(r.error ?: "Failed") else refreshStatus() }
    }

    LaunchedEffect(Unit) { refresh() }
    PlatformBackHandler(enabled = openId != null) { openId = null }
    StatusBarIcons(light = openId == null)

    AppTheme(fonts) {
        AnimatedContent(
            targetState = openId,
            transitionSpec = {
                val opening = targetState != null
                (slideInHorizontally(tween(260)) { if (opening) it / 4 else -it / 4 } + fadeIn(tween(260))) togetherWith
                    (slideOutHorizontally(tween(200)) { if (opening) -it / 8 else it / 8 } + fadeOut(tween(160)))
            },
            label = "screen",
        ) { id ->
            if (id == null) {
                InboxScreen(
                    account = account,
                    statusError = statusError,
                    unread = unread,
                    state = list,
                    listState = listState,
                    snackbar = snackbar,
                    onRefresh = { refresh() },
                    onLoadMore = { load(more = true) },
                    onOpen = { openId = it },
                    onArchive = { remove(it, "archive") },
                )
            } else {
                ThreadScreen(
                    api = api,
                    id = id,
                    snackbar = snackbar,
                    onBack = { openId = null },
                    onOpened = { wasUnread ->
                        if (wasUnread) {
                            patch(id) { it.copy(unread = false) }
                            scope.launch { if (api.act(id, "read").ok) refreshStatus() }
                        }
                    },
                    onArchive = { remove(id, "archive") },
                    onTrash = { remove(id, "trash") },
                    onMarkUnread = { markUnread(id) },
                    onReplied = { load() },
                )
            }
        }
    }
}
