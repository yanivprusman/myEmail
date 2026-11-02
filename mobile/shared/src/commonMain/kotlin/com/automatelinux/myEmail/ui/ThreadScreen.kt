package com.automatelinux.myEmail.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.automatelinux.myEmail.data.MailApi
import com.automatelinux.myEmail.data.MessageView
import com.automatelinux.myEmail.data.ThreadView
import com.automatelinux.myEmail.platform.PlatformBackHandler
import com.automatelinux.myEmail.ui.theme.Ink
import kotlinx.coroutines.launch

/**
 * One conversation, as text on paper cards. No remote HTML is ever rendered:
 * nothing loads a tracking pixel on open, and each paragraph takes its own
 * direction. Every action lives in the bottom dock, under the thumb.
 */
@Composable
fun ThreadScreen(
    api: MailApi,
    id: String,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onOpened: (wasUnread: Boolean) -> Unit,
    onArchive: () -> Unit,
    onTrash: () -> Unit,
    onMarkUnread: () -> Unit,
    onReplied: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var thread by remember(id) { mutableStateOf<ThreadView?>(null) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var composing by remember(id) { mutableStateOf(false) }
    var draft by remember(id) { mutableStateOf("") }
    var sending by remember(id) { mutableStateOf(false) }

    suspend fun fetch() {
        val r = api.thread(id)
        error = r.error
        if (r.thread != null) thread = r.thread
    }

    LaunchedEffect(id) {
        fetch()
        thread?.let { t -> onOpened(t.messages.any { it.unread }) }
    }
    PlatformBackHandler(enabled = composing) { composing = false }

    fun send() {
        sending = true
        scope.launch {
            val r = api.reply(id, draft)
            sending = false
            if (r.ok) {
                draft = ""; composing = false
                fetch(); onReplied()
                snackbar.showSnackbar("Sent to ${r.to}")
            } else snackbar.showSnackbar(r.error ?: "Not sent")
        }
    }

    Scaffold(
        containerColor = Ink.Paper,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIcon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", "back", onBack)
            }
        },
        snackbarHost = { SnackbarHost(snackbar) { InkSnackbar(it) } },
        bottomBar = {
            if (thread != null) Box(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 10.dp)) {
                AnimatedContent(
                    targetState = composing,
                    transitionSpec = { (fadeIn(tween(200)) + slideInVertically { it / 3 }) togetherWith fadeOut(tween(120)) },
                    label = "dock",
                ) { open ->
                    if (!open) Dock(onArchive, onTrash, onMarkUnread) { composing = true }
                    else Composer(
                        to = thread?.messages?.lastOrNull { it.fromEmail.isNotEmpty() }?.from ?: "",
                        draft = draft, sending = sending,
                        onDraft = { draft = it }, onCancel = { composing = false }, onSend = ::send,
                    )
                }
            }
        },
    ) { pad ->
        val t = thread
        Box(Modifier.fillMaxSize().padding(pad)) {
            when {
                t != null -> LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)) {
                    item {
                        Text(t.subject, style = MaterialTheme.typography.headlineSmall, color = Ink.Text,
                            modifier = Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 4.dp, bottom = 14.dp))
                    }
                    items(t.messages, key = { it.id }) { m ->
                        MessageCard(m, expandedByDefault = m == t.messages.last() || m.unread)
                    }
                }
                error != null -> Column(Modifier.fillMaxSize().padding(36.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Can't open this conversation", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(error ?: "", color = Ink.Soft, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(18.dp))
                    Text("Try again", color = Color.White, style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clip(RoundedCornerShape(22.dp)).background(Ink.Navy)
                            .clickable { scope.launch { fetch() } }.testTag("retry-thread").padding(horizontal = 26.dp, vertical = 12.dp))
                }
                else -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Ink.Seal)
            }
        }
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, label: String, tag: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(Ink.Card).clickable(onClick = onClick).testTag(tag),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = label, tint = Ink.Text) }
}

/** Archive · Delete · Unread on the left, Reply as the one amber thing. */
@Composable
private fun Dock(onArchive: () -> Unit, onTrash: () -> Unit, onMarkUnread: () -> Unit, onReply: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().shadow(14.dp, RoundedCornerShape(30.dp)).background(Ink.Night, RoundedCornerShape(30.dp)).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DockButton(Icons.Outlined.Archive, "Archive", "archive-thread", onArchive)
        DockButton(Icons.Outlined.Delete, "Delete", "trash-thread", onTrash)
        DockButton(Icons.Outlined.MarkEmailUnread, "Unread", "mark-unread", onMarkUnread)
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.clip(RoundedCornerShape(24.dp)).background(Ink.SealBrush).clickable(onClick = onReply).testTag("reply")
                .padding(horizontal = 22.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Outlined.Reply, contentDescription = null, tint = Ink.Night, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Reply", color = Ink.Night, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DockButton(icon: ImageVector, label: String, tag: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(22.dp)).clickable(onClick = onClick).testTag(tag).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(21.dp))
        Text(label, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun Composer(to: String, draft: String, sending: Boolean, onDraft: (String) -> Unit, onCancel: () -> Unit, onSend: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().shadow(14.dp, RoundedCornerShape(24.dp)).background(Ink.Card, RoundedCornerShape(24.dp)).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("To ", style = MaterialTheme.typography.labelLarge, color = Ink.Faint)
            Text(to, style = MaterialTheme.typography.labelLarge, color = Ink.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Box(Modifier.size(32.dp).clip(CircleShape).clickable(enabled = !sending, onClick = onCancel).testTag("cancel-reply"), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Close, contentDescription = "Cancel", tint = Ink.Soft, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Box(Modifier.weight(1f).heightIn(min = 72.dp, max = 220.dp).padding(vertical = 6.dp)) {
                if (draft.isEmpty()) Text("Write your reply…", style = MaterialTheme.typography.bodyLarge, color = Ink.Faint)
                BasicTextField(
                    value = draft, onValueChange = onDraft, enabled = !sending,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink.Text),
                    cursorBrush = SolidColor(Ink.SealDeep),
                    modifier = Modifier.fillMaxWidth().testTag("reply-text"),
                )
            }
            Spacer(Modifier.width(10.dp))
            val ready = draft.isNotBlank() && !sending
            Box(
                Modifier.size(50.dp).clip(CircleShape)
                    .background(if (ready || sending) Ink.SealBrush else SolidColor(Ink.Hairline))
                    .clickable(enabled = ready, onClick = onSend).testTag("send-reply"),
                contentAlignment = Alignment.Center,
            ) {
                if (sending) CircularProgressIndicator(Modifier.size(20.dp), color = Ink.Night, strokeWidth = 2.dp)
                else Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Send", tint = if (ready) Ink.Night else Ink.Faint)
            }
        }
    }
}

/** Older messages fold to a one-line card; the latest and unread ones open. */
@Composable
private fun MessageCard(m: MessageView, expandedByDefault: Boolean) {
    var expanded by remember(m.id) { mutableStateOf(expandedByDefault) }
    var showQuoted by remember(m.id) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)
            .shadow(if (expanded) 3.dp else 0.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(if (expanded) Ink.Card else Ink.Card.copy(alpha = 0.55f))
            .clickable { expanded = !expanded }
            .testTag("message-${m.id}")
            .animateContentSize(tween(220))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(m.from, m.fromEmail, size = 38.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(m.from, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Ink.Text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (expanded) m.fromEmail else m.body.replace('\n', ' '), style = MaterialTheme.typography.bodySmall,
                    color = Ink.Soft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Text(if (expanded) longTime(m.date) else shortTime(m.date), style = MaterialTheme.typography.labelMedium, color = Ink.Faint)
        }
        if (expanded) {
            Spacer(Modifier.height(14.dp))
            SelectionContainer {
                Text(m.body.ifBlank { "(empty message)" }, style = MaterialTheme.typography.bodyLarge, color = Ink.Text)
            }
            if (m.attachments.isNotEmpty()) Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                m.attachments.forEach {
                    Row(
                        Modifier.background(Ink.Paper, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.AttachFile, contentDescription = null, tint = Ink.Soft, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = Ink.Soft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (m.quoted.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    if (showQuoted) "Hide earlier text" else "•••  Show earlier text",
                    style = MaterialTheme.typography.labelMedium, color = Ink.SealDeep,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { showQuoted = !showQuoted }
                        .testTag("toggle-quoted-${m.id}").padding(vertical = 6.dp, horizontal = 4.dp),
                )
                if (showQuoted) SelectionContainer {
                    Row(Modifier.padding(top = 6.dp).height(IntrinsicSize.Min)) {
                        Box(Modifier.width(3.dp).fillMaxHeight().background(Ink.Hairline, RoundedCornerShape(2.dp)))
                        Spacer(Modifier.width(10.dp))
                        Text(m.quoted, style = MaterialTheme.typography.bodyMedium, color = Ink.Soft)
                    }
                }
            }
        }
    }
}
