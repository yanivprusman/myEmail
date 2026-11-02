package com.automatelinux.myEmail.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.automatelinux.myEmail.ui.theme.Ink

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    account: String,
    statusError: String?,
    view: View,
    unread: Unread,
    state: ListState,
    listState: LazyListState,
    snackbar: SnackbarHostState,
    onView: (View) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (String) -> Unit,
    onArchive: (String) -> Unit,
) {
    Scaffold(
        containerColor = Ink.Paper,
        contentWindowInsets = WindowInsets(0),
        topBar = { Header(account, view, unread, onView) },
        snackbarHost = { SnackbarHost(snackbar, Modifier.navigationBarsPadding()) { InkSnackbar(it) } },
    ) { pad ->
        PullToRefreshBox(
            isRefreshing = state.loading && state.loaded,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(pad),
        ) {
            AnimatedContent(
                targetState = view,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                label = "view",
            ) { _ ->
                val error = state.error ?: statusError
                when {
                    !state.loaded -> LazyColumn(Modifier.fillMaxSize()) { item { SkeletonRows() } }
                    error != null && state.threads.isEmpty() -> LazyColumn(Modifier.fillMaxSize()) { item { Problem(error, onRefresh) } }
                    state.threads.isEmpty() -> LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            if (view == View.People) EmptyEnvelope("All caught up", "Nothing from people is waiting.\nPull down to check again.")
                            else EmptyEnvelope("Nothing in bulk", "No newsletters, offers or notifications\nin your inbox.")
                        }
                    }
                    else -> ThreadList(state, listState, onLoadMore, onOpen, onArchive)
                }
            }
        }
    }
}

/** Ink header in the icon's own night: date, the view's name, the switch. */
@Composable
private fun Header(account: String, view: View, unread: Unread, onView: (View) -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(Ink.NightBrush)
            .statusBarsPadding()
            .padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(todayLine().uppercase(), style = MaterialTheme.typography.labelSmall, color = Ink.SealGlow.copy(alpha = 0.85f))
                Spacer(Modifier.height(4.dp))
                val waiting = if (view == View.People) unread.people else unread.other
                Text(
                    if (view == View.People) "People" else "Everything else",
                    style = MaterialTheme.typography.displaySmall, color = Color.White,
                )
                Text(
                    when {
                        account.isEmpty() -> "Connecting…"
                        waiting == 0 -> "Nothing unread"
                        waiting == 1 -> "1 unread"
                        else -> "$waiting unread"
                    },
                    style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.62f),
                )
            }
            if (account.isNotEmpty()) Box(
                Modifier.size(40.dp).background(Ink.SealBrush, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(initial(account), color = Ink.Night, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }
        }
        Spacer(Modifier.height(18.dp))
        SegmentSwitch(
            labels = listOf("People" to unread.people, "Everything else" to unread.other),
            selected = view.ordinal,
            tags = listOf("tab-people", "tab-other"),
            onSelect = { onView(View.entries[it]) },
        )
    }
}

@Composable
private fun Problem(error: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = Ink.Faint, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(16.dp))
        Text("Can't load your mail", style = MaterialTheme.typography.titleLarge, color = Ink.Text)
        Spacer(Modifier.height(6.dp))
        Text(error, style = MaterialTheme.typography.bodyMedium, color = Ink.Soft, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Text(
            "Try again", color = Color.White, style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.clip(RoundedCornerShape(22.dp)).background(Ink.Navy).clickable(onClick = onRetry)
                .testTag("retry").padding(horizontal = 26.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun ThreadList(
    state: ListState,
    listState: LazyListState,
    onLoadMore: () -> Unit,
    onOpen: (String) -> Unit,
    onArchive: (String) -> Unit,
) {
    val nearEnd by remember(listState) {
        derivedStateOf { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= listState.layoutInfo.totalItemsCount - 6 }
    }
    LaunchedEffect(nearEnd, state.threads.size) { if (nearEnd && state.nextPageToken != null) onLoadMore() }

    val grouped = remember(state.threads) { state.threads.groupBy { dayBucket(it.date) }.toList() }
    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        grouped.forEach { (bucket, threads) ->
            item(key = "h-$bucket") {
                Text(
                    bucket.uppercase(), style = MaterialTheme.typography.labelSmall, color = Ink.Faint,
                    modifier = Modifier.animateItem().padding(start = 22.dp, top = 20.dp, bottom = 6.dp),
                )
            }
            items(threads, key = { it.id }) { t ->
                Box(Modifier.animateItem()) { SwipeToArchive(t.id, onArchive) { ThreadRow(t) { onOpen(t.id) } } }
            }
        }
        if (state.nextPageToken != null) item(key = "more") {
            Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), color = Ink.Seal, strokeWidth = 2.dp)
            }
        }
        item(key = "end") { Spacer(Modifier.navigationBarsPadding().height(24.dp)) }
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
            Row(
                Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(18.dp))
                    .background(Ink.Archive).padding(horizontal = 22.dp),
                horizontalArrangement = if (start) Arrangement.Start else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Archive, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(10.dp))
                Text("Archive", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        },
    ) { content() }
}

/**
 * One conversation. Unread rows lift onto a white card with the amber seal;
 * read ones sit flat on the paper — the difference is visible from arm's length.
 */
@Composable
private fun ThreadRow(t: ThreadSummary, onClick: () -> Unit) {
    val unread = t.unread
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (unread) Ink.Card else Ink.Paper)
            .clickable(onClick = onClick)
            .testTag("thread-${t.id}")
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Avatar(t.from, t.fromEmail)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Name (+ count) take the free width; time and seal keep a fixed column on the right.
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        t.from, modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Ltr),
                        fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium,
                        color = if (unread) Ink.Text else Ink.Soft,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (t.count > 1) {
                        Spacer(Modifier.width(6.dp))
                        Text("${t.count}", style = MaterialTheme.typography.labelMedium, color = Ink.Faint,
                            modifier = Modifier.background(Ink.Hairline.copy(alpha = 0.6f), RoundedCornerShape(6.dp)).padding(horizontal = 5.dp))
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(shortTime(t.date), style = MaterialTheme.typography.labelMedium,
                    color = if (unread) Ink.SealDeep else Ink.Faint, fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium)
                if (unread) { Spacer(Modifier.width(6.dp)); SealDot() }
            }
            Spacer(Modifier.height(2.dp))
            Text(t.subject, style = MaterialTheme.typography.bodyMedium, fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
                color = Ink.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
            Text(t.snippet, style = MaterialTheme.typography.bodyMedium, color = Ink.Soft, maxLines = 2,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
        }
    }
}
