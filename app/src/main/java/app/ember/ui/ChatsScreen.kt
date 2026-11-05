package app.ember.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ember.data.Chat
import app.ember.data.Store
import java.text.DateFormat
import java.util.Date

private fun shortDate(t: Long): String {
    if (t <= 0) return ""
    val now = System.currentTimeMillis()
    val day = 24L * 60 * 60 * 1000
    return when {
        now - t < day -> DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(t))
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(t))
    }
}

/** Number of a chat among its character's chats, oldest first (Chat 1, Chat 2…). */
private fun chatNumber(c: Chat): Int =
    Store.chats.filter { it.characterId == c.characterId }.sortedBy { it.createdAt }.indexOfFirst { it.id == c.id } + 1

/** Every chat with every character, newest first. */
@Composable
fun ChatsScreen(
    openChat: (String) -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var confirmFresh by remember { mutableStateOf<Chat?>(null) }
    var confirmDelete by remember { mutableStateOf<Chat?>(null) }

    val q = query.trim().lowercase()
    val chats = Store.chats
        .filter { c -> Store.character(c.characterId) != null }
        .filter { c -> q.isEmpty() || Store.character(c.characterId)?.name?.lowercase()?.contains(q) == true }
        .sortedByDescending { it.updatedAt }

    Scaffold(
        containerColor = EmberColors.Bg,
        bottomBar = bottomBar,
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Chats", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                    Icon(Icons.Outlined.Search, "Search by character", tint = EmberColors.Text)
                }
            }
            if (searching) {
                TextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text("Character name", color = EmberColors.Dim) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = fieldColors(), shape = RoundedCornerShape(24.dp),
                )
            }
            Text(
                "Use ⋮ › Start fresh to begin again from the opening message. The old chat stays here.",
                fontSize = 13.sp, color = EmberColors.Muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            if (chats.isEmpty()) {
                Text(
                    if (q.isNotEmpty()) "No chats with that name." else "No chats yet. Open a character to start one.",
                    color = EmberColors.Muted, modifier = Modifier.padding(16.dp),
                )
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(chats, key = { it.id }) { chat ->
                    ChatRow(
                        chat,
                        onOpen = {
                            Store.setActiveChat(chat.characterId, chat.id)
                            openChat(chat.characterId)
                        },
                        onFresh = { confirmFresh = chat },
                        onDelete = { confirmDelete = chat },
                    )
                }
            }
        }
    }

    confirmFresh?.let { chat ->
        val character = Store.character(chat.characterId)
        AlertDialog(
            onDismissRequest = { confirmFresh = null },
            title = { Text("Start fresh with ${character?.name}?") },
            text = { Text("A new chat begins from the opening message. This chat is kept and stays in your Chats list.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmFresh = null
                    if (character != null) {
                        Store.newChat(character)
                        openChat(character.id)
                    }
                }) { Text("Start fresh") }
            },
            dismissButton = { TextButton(onClick = { confirmFresh = null }) { Text("Cancel") } },
            containerColor = EmberColors.Surface,
        )
    }

    confirmDelete?.let { chat ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this chat?") },
            text = { Text("It moves to Recently deleted (in Settings) for 30 days, then it's gone for good.") },
            confirmButton = {
                TextButton(onClick = { Store.deleteChat(chat.id); confirmDelete = null }) { Text("Delete", color = EmberColors.RedSoft) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
            containerColor = EmberColors.Surface,
        )
    }
}

@Composable
private fun ChatRow(chat: Chat, onOpen: () -> Unit, onFresh: () -> Unit, onDelete: () -> Unit) {
    val character = Store.character(chat.characterId) ?: return
    var menu by remember { mutableStateOf(false) }
    val isCurrent = Store.activeChat(chat.characterId)?.id == chat.id
    val count = Store.chats.count { it.characterId == chat.characterId }
    val last = chat.messages.lastOrNull()
    val preview = last?.text?.replace("\n", " ")?.trim().orEmpty()

    Row(
        Modifier.fillMaxWidth()
            .background(EmberColors.Surface, RoundedCornerShape(16.dp))
            .border(1.dp, if (isCurrent) EmberColors.Outline else EmberColors.Line, RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
            .padding(start = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(character.avatar, character.name, 52.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    character.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                if (count > 1) {
                    Spacer(Modifier.width(6.dp))
                    Text("Chat ${chatNumber(chat)}", fontSize = 12.sp, color = EmberColors.Muted)
                }
                Spacer(Modifier.weight(1f))
                Text(shortDate(chat.updatedAt), fontSize = 12.sp, color = EmberColors.Muted)
            }
            Spacer(Modifier.height(3.dp))
            Text(
                if (preview.isEmpty()) "No messages yet" else (if (last?.fromUser == true) "You: " else "") + preview,
                fontSize = 14.sp, color = EmberColors.TextSoft, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${chat.messages.size} messages" + if (isCurrent && count > 1) " · opens by default" else "",
                fontSize = 12.sp, color = if (isCurrent && count > 1) EmberColors.RedSoft else EmberColors.Dim,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Options for this chat", tint = EmberColors.Muted) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Open") }, onClick = { menu = false; onOpen() })
                DropdownMenuItem(text = { Text("Start fresh (keep this chat)") }, onClick = { menu = false; onFresh() })
                DropdownMenuItem(text = { Text("Delete chat") }, onClick = { menu = false; onDelete() })
            }
        }
    }
}
