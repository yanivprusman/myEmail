package com.automatelinux.myEmail.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.automatelinux.myEmail.ListState
import com.automatelinux.myEmail.data.ThreadSummary
import com.automatelinux.myEmail.data.Unread
import com.automatelinux.myEmail.data.View

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    account: String,
    statusError: String?,
    view: View,
    unread: Unread,
    state: ListState,
    snackbar: SnackbarHostState,
    onView: (View) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (String) -> Unit,
    onArchive: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("myEmail", fontWeight = FontWeight.SemiBold)
                            if (account.isNotEmpty())
                                Text(account, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    actions = {
                        IconButton(onClick = onRefresh, modifier = Modifier.testTag("refresh-inbox")) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                        }
                    },
                )
                TabRow(selectedTabIndex = view.ordinal) {
                    ViewTab("People", unread.people, view == View.People, "tab-people") { onView(View.People) }
                    ViewTab("Everything else", unread.other, view == View.Other, "tab-other") { onView(View.Other) }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        PullToRefreshBox(
            isRefreshing = state.loading && state.loaded && state.threads.isNotEmpty(),
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(pad),
        ) {
            val error = state.error ?: statusError
            when {
                !state.loaded && state.loading -> Centered { CircularProgressIndicator() }
                error != null && state.threads.isEmpty() -> Centered {
                    Text(error, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRefresh, modifier = Modifier.testTag("retry")) { Text("Try again") }
                }
                state.threads.isEmpty() -> Centered {
                    Text(
                        if (view == View.People) "Nothing from people is waiting." else "No bulk mail in the inbox.",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text("Pull down to check again.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> ThreadList(state, onLoadMore, onOpen, onArchive)
            }
        }
    }
}

@Composable
private fun ViewTab(label: String, count: Int, selected: Boolean, tag: String, onClick: () -> Unit) {
    Tab(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.testTag(tag),
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                if (count > 0) {
                    Spacer(Modifier.width(6.dp))
                    Badge { Text(if (count >= 100) "99+" else count.toString()) }
                }
            }
        },
    )
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    // A scrollable column so pull-to-refresh still works on an empty list.
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        item { Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) { content() } }
    }
}

@Composable
private fun ThreadList(state: ListState, onLoadMore: () -> Unit, onOpen: (String) -> Unit, onArchive: (String) -> Unit) {
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= listState.layoutInfo.totalItemsCount - 5 }
    }
    LaunchedEffect(nearEnd, state.threads.size) { if (nearEnd && state.nextPageToken != null) onLoadMore() }

    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        items(state.threads, key = { it.id }) { t ->
            SwipeToArchive(t.id, onArchive) { ThreadRow(t) { onOpen(t.id) } }
            HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
        if (state.nextPageToken != null) item {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
        }
    }
}

@Composable
private fun SwipeToArchive(id: String, onArchive: (String) -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(confirmValueChange = {
        if (it != SwipeToDismissBoxValue.Settled) onArchive(id)
        it != SwipeToDismissBoxValue.Settled
    })
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            val start = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Box(
                Modifier.fillMaxSize().background(Color(0xFF2E7D5B)).padding(horizontal = 24.dp),
                contentAlignment = if (start) Alignment.CenterStart else Alignment.CenterEnd,
            ) { Icon(Icons.Outlined.Archive, contentDescription = "Archive", tint = Color.White) }
        },
    ) { content() }
}

@Composable
private fun ThreadRow(t: ThreadSummary, onClick: () -> Unit) {
    val weight = if (t.unread) FontWeight.Bold else FontWeight.Normal
    val strong = MaterialTheme.colorScheme.onSurface
    val soft = MaterialTheme.colorScheme.onSurfaceVariant
    val content = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content)
    Row(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .testTag("thread-${t.id}")
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(40.dp).background(avatarColor(t.fromEmail), CircleShape), contentAlignment = Alignment.Center) {
            Text(initial(t.from), color = Color.White, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    t.from + if (t.count > 1) "  ${t.count}" else "",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
                    fontWeight = weight, maxLines = 1, overflow = TextOverflow.Ellipsis, color = strong,
                )
                Spacer(Modifier.width(8.dp))
                Text(shortTime(t.date), style = MaterialTheme.typography.labelMedium, fontWeight = weight,
                    color = if (t.unread) MaterialTheme.colorScheme.primary else soft)
            }
            Text(t.subject, style = content, fontWeight = weight, maxLines = 1, overflow = TextOverflow.Ellipsis, color = strong)
            Text(t.snippet, style = content, maxLines = 2, overflow = TextOverflow.Ellipsis, color = soft)
        }
    }
}
