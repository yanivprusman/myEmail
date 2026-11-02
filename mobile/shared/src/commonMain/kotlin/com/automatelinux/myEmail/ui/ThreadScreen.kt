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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.automatelinux.myEmail.data.MailApi
import com.automatelinux.myEmail.data.MessageView
import com.automatelinux.myEmail.data.ThreadView
import com.automatelinux.myEmail.platform.PlatformBackHandler
import kotlinx.coroutines.launch

/**
 * One conversation, as plain text. No remote HTML is ever rendered: nothing
 * loads a tracking pixel on open, nothing reflows, Hebrew and English
 * paragraphs each take their own direction.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
        thread = r.thread
    }

    LaunchedEffect(id) {
        fetch()
        thread?.let { t -> onOpened(t.messages.any { it.unread }) }
    }
    PlatformBackHandler(enabled = composing) { composing = false }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back")) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onArchive, modifier = Modifier.testTag("archive-thread")) {
                        Icon(Icons.Outlined.Archive, contentDescription = "Archive")
                    }
                    IconButton(onClick = onTrash, modifier = Modifier.testTag("trash-thread")) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete")
                    }
                    IconButton(onClick = onMarkUnread, modifier = Modifier.testTag("mark-unread")) {
                        Icon(Icons.Outlined.MarkEmailUnread, contentDescription = "Mark unread")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (thread != null) Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(12.dp)) {
                    if (!composing) {
                        Button(onClick = { composing = true }, modifier = Modifier.fillMaxWidth().testTag("reply")) {
                            Icon(Icons.AutoMirrored.Outlined.Reply, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Reply")
                        }
                    } else {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp, max = 240.dp).testTag("reply-text"),
                            placeholder = { Text("Write a reply…") },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
                            enabled = !sending,
                        )
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { composing = false }, enabled = !sending, modifier = Modifier.testTag("cancel-reply")) { Text("Cancel") }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
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
                                },
                                enabled = draft.isNotBlank() && !sending,
                                modifier = Modifier.testTag("send-reply"),
                            ) {
                                if (sending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Send")
                            }
                        }
                    }
                }
            }
        },
    ) { pad ->
        val t = thread
        Box(Modifier.fillMaxSize().padding(pad)) {
            when {
                t != null -> LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Text(
                            t.subject,
                            style = MaterialTheme.typography.headlineSmall.copy(textDirection = TextDirection.Content),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                    items(t.messages, key = { it.id }) { m ->
                        Message(m, expandedByDefault = m == t.messages.last() || m.unread)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
                error != null -> Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error ?: "", color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { scope.launch { fetch() } }, modifier = Modifier.testTag("retry-thread")) { Text("Try again") }
                }
                else -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }
}

/** Older messages fold to one line (sender + snippet), like a real conversation. */
@Composable
private fun Message(m: MessageView, expandedByDefault: Boolean) {
    var expanded by remember(m.id) { mutableStateOf(expandedByDefault) }
    var showQuoted by remember(m.id) { mutableStateOf(false) }
    val soft = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }.testTag("message-${m.id}").padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(avatarColor(m.fromEmail), CircleShape), contentAlignment = Alignment.Center) {
                Text(initial(m.from), color = Color.White, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(m.from, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content))
                Text(if (expanded) m.fromEmail else m.body.replace('\n', ' '), color = soft, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(8.dp))
            Text(if (expanded) longTime(m.date) else shortTime(m.date), color = soft, style = MaterialTheme.typography.labelSmall)
        }
        if (expanded) {
            Spacer(Modifier.size(12.dp))
            SelectionContainer {
                Text(m.body.ifBlank { "(empty message)" },
                    style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content))
            }
            if (m.attachments.isNotEmpty()) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                m.attachments.take(3).forEach {
                    AssistChip(onClick = {}, enabled = false, label = { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(Icons.Outlined.AttachFile, contentDescription = null, Modifier.size(16.dp)) })
                }
            }
            if (m.quoted.isNotEmpty()) {
                TextButton(onClick = { showQuoted = !showQuoted }, modifier = Modifier.testTag("toggle-quoted-${m.id}")) {
                    Text(if (showQuoted) "Hide quoted text" else "Show quoted text")
                }
                if (showQuoted) SelectionContainer {
                    Text(m.quoted, color = soft, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content))
                }
            }
        }
    }
}
