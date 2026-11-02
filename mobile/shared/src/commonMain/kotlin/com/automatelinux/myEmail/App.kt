package com.automatelinux.myEmail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import com.automatelinux.myEmail.data.MailApi
import com.automatelinux.myEmail.data.ThreadSummary
import com.automatelinux.myEmail.data.Unread
import com.automatelinux.myEmail.data.View
import com.automatelinux.myEmail.platform.PlatformBackHandler
import com.automatelinux.myEmail.platform.StatusBarIcons
import com.automatelinux.myEmail.ui.InboxScreen
import com.automatelinux.myEmail.ui.ThreadScreen
import com.automatelinux.myEmail.ui.theme.AppTheme
import kotlinx.coroutines.launch

/** One view's list as the screen sees it. */
data class ListState(
    val threads: List<ThreadSummary> = emptyList(),
    val nextPageToken: String? = null,
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
)

/**
 * The whole app: two lists (People / Everything else) and one open thread.
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

    var view by remember { mutableStateOf(View.People) }
    val lists = remember { mutableStateMapOf(View.People to ListState(), View.Other to ListState()) }
    var unread by remember { mutableStateOf(Unread()) }
    var account by remember { mutableStateOf("") }
    var statusError by remember { mutableStateOf<String?>(null) }
    var openId by remember { mutableStateOf<String?>(null) }
    // Hoisted so coming back from a thread lands exactly where you left the list.
    val listStates = remember { View.entries.associateWith { LazyListState() } }

    fun refreshStatus() = scope.launch {
        val s = api.status()
        statusError = s.error
        if (s.ok) { account = s.email; unread = s.unread }
    }

    fun load(v: View, more: Boolean = false) {
        val cur = lists.getValue(v)
        if (cur.loading || (more && cur.nextPageToken == null)) return
        lists[v] = cur.copy(loading = true, error = null)
        scope.launch {
            val r = api.inbox(v, if (more) cur.nextPageToken else null)
            val now = lists.getValue(v)
            lists[v] = if (r.ok) now.copy(
                threads = if (more) (now.threads + r.threads).distinctBy { it.id } else r.threads,
                nextPageToken = r.nextPageToken, loading = false, loaded = true,
            ) else now.copy(loading = false, loaded = true, error = r.error)
        }
    }

    fun refresh(v: View = view) { load(v); refreshStatus() }

    fun locate(id: String): Pair<View, ThreadSummary>? =
        View.entries.firstNotNullOfOrNull { v -> lists.getValue(v).threads.find { it.id == id }?.let { v to it } }

    fun patch(id: String, f: (ThreadSummary) -> ThreadSummary) {
        for (v in View.entries) {
            val l = lists.getValue(v)
            if (l.threads.any { it.id == id }) lists[v] = l.copy(threads = l.threads.map { if (it.id == id) f(it) else it })
        }
    }

    /** archive / trash: out of the list now, server second, Undo puts it back. */
    fun remove(id: String, action: String) {
        val found = locate(id) ?: return
        val (v, t) = found
        val before = lists.getValue(v).threads
        val index = before.indexOf(t)
        lists[v] = lists.getValue(v).copy(threads = before - t)
        if (openId == id) openId = null
        scope.launch {
            val r = api.act(id, action)
            if (!r.ok) {
                lists[v] = lists.getValue(v).copy(threads = lists.getValue(v).threads.toMutableList().apply { add(index.coerceAtMost(size), t) })
                snackbar.showSnackbar(r.error ?: "Couldn't $action")
                return@launch
            }
            refreshStatus()
            val label = if (action == "trash") "Moved to Trash" else "Archived"
            if (snackbar.showSnackbar(label, actionLabel = "Undo", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                val undo = api.act(id, if (action == "trash") "untrash" else "inbox")
                if (undo.ok) {
                    lists[v] = lists.getValue(v).copy(threads = lists.getValue(v).threads.toMutableList().apply { add(index.coerceAtMost(size), t) })
                    refreshStatus()
                } else snackbar.showSnackbar(undo.error ?: "Undo failed")
            }
        }
    }

    fun markUnread(id: String) {
        patch(id) { it.copy(unread = true) }
        openId = null
        scope.launch { val r = api.act(id, "unread"); if (!r.ok) snackbar.showSnackbar(r.error ?: "Failed") else refreshStatus() }
    }

    LaunchedEffect(Unit) { refresh(View.People) }
    LaunchedEffect(view) { if (!lists.getValue(view).loaded) load(view) }
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
                view = view,
                unread = unread,
                state = lists.getValue(view),
                listState = listStates.getValue(view),
                snackbar = snackbar,
                onView = { view = it },
                onRefresh = { refresh() },
                onLoadMore = { load(view, more = true) },
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
                onReplied = { load(locate(id)?.first ?: view) },
            )
        }
      }
    }
}
