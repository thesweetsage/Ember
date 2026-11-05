package app.ember.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ember.data.Chat
import app.ember.data.Store
import java.text.DateFormat
import java.util.Date

private fun dateLabel(t: Long): String =
    if (t <= 0) "" else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(t))

/** Every chat with one character; tap one to make it the open chat. */
@Composable
fun ArchivesScreen(characterId: String, onBack: () -> Unit) {
    val character = Store.character(characterId)
    val active = Store.activeChat(characterId)
    var confirmDelete by remember { mutableStateOf<Chat?>(null) }

    Scaffold(containerColor = EmberColors.Bg, topBar = { EmberTopBar("Chat archives", onBack) }) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val list = Store.chatsFor(characterId)
            if (list.isEmpty()) item { Text("No chats yet.", color = EmberColors.Muted) }
            items(list, key = { it.id }) { c ->
                val isActive = c.id == active?.id
                val preview = c.messages.lastOrNull()?.text?.replace("\n", " ")?.take(140).orEmpty()
                Row(
                    Modifier.fillMaxWidth()
                        .background(EmberColors.Surface, RoundedCornerShape(16.dp))
                        .border(1.5.dp, if (isActive) EmberColors.Red else EmberColors.Line, RoundedCornerShape(16.dp))
                        .clickable { Store.setActiveChat(characterId, c.id); onBack() }
                        .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row {
                            Text(
                                "${c.messages.size} messages", fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            if (isActive) Text("Open now", fontSize = 12.sp, color = EmberColors.RedSoft, fontWeight = FontWeight.SemiBold)
                        }
                        Text("Last active ${dateLabel(c.updatedAt)}", fontSize = 12.5.sp, color = EmberColors.Muted)
                        if (preview.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(preview, fontSize = 14.sp, color = EmberColors.TextSoft, maxLines = 2)
                        }
                    }
                    IconButton(onClick = { confirmDelete = c }) { Icon(Icons.Outlined.Delete, "Delete chat", tint = EmberColors.Muted) }
                }
            }
        }
    }

    confirmDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this chat?") },
            text = { Text("It moves to Recently deleted (in Settings) for 30 days, then it's gone for good.") },
            confirmButton = {
                TextButton(onClick = {
                    Store.deleteChat(c.id)
                    if (Store.chatsFor(characterId).isEmpty() && character != null) Store.newChat(character)
                    confirmDelete = null
                }) { Text("Delete", color = EmberColors.RedSoft) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
            containerColor = EmberColors.Surface,
        )
    }
}

/** Search across all chats with one character; tap a result to jump to it. */
@Composable
fun SearchScreen(characterId: String, onBack: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim()
    val results = remember(q, Store.chats) {
        if (q.length < 2) emptyList()
        else Store.chatsFor(characterId).flatMap { c ->
            c.messages.filter { it.text.contains(q, ignoreCase = true) }.map { c to it }
        }.take(200)
    }

    Scaffold(containerColor = EmberColors.Bg, topBar = { EmberTopBar("Search chat history", onBack) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp)) {
            TextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                placeholder = { Text("Search words or names", color = EmberColors.Dim) },
                modifier = Modifier.fillMaxWidth(), colors = fieldColors(), shape = RoundedCornerShape(24.dp),
            )
            Spacer(Modifier.height(10.dp))
            if (q.length >= 2) Text("${results.size} results", fontSize = 13.sp, color = EmberColors.Muted)
            LazyColumn(
                contentPadding = PaddingValues(vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(results, key = { it.first.id + it.second.id }) { (chat, m) ->
                    val text = m.text.replace("\n", " ")
                    val at = text.indexOf(q, ignoreCase = true).coerceAtLeast(0)
                    val start = (at - 60).coerceAtLeast(0)
                    val end = (at + q.length + 100).coerceAtMost(text.length)
                    Column(
                        Modifier.fillMaxWidth().background(EmberColors.Surface, RoundedCornerShape(14.dp))
                            .clickable {
                                Store.setActiveChat(characterId, chat.id)
                                Store.focusMessageId = m.id
                                onBack()
                            }.padding(14.dp),
                    ) {
                        Text(
                            (if (m.fromUser) "You" else Store.character(characterId)?.name ?: "") + " · " + dateLabel(chat.updatedAt),
                            fontSize = 12.sp, color = EmberColors.Muted,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(buildAnnotatedString {
                            if (start > 0) append("…")
                            append(text.substring(start, at))
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = EmberColors.RedSoft)) { append(text.substring(at, (at + q.length).coerceAtMost(text.length))) }
                            append(text.substring((at + q.length).coerceAtMost(text.length), end))
                            if (end < text.length) append("…")
                        }, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                }
            }
        }
    }
}
