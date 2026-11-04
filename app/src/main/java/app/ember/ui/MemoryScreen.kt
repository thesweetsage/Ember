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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ember.data.Pin
import app.ember.data.Store
import app.ember.data.Summary

@Composable
fun MemoryScreen(characterId: String, onBack: () -> Unit) {
    val vm: ChatViewModel = viewModel()
    LaunchedEffect(characterId) { vm.bind(characterId) }
    val character = Store.character(characterId)
    val chat = Store.activeChat(characterId)
    if (character == null || chat == null) { LaunchedEffect(Unit) { onBack() }; return }

    var editPin by remember { mutableStateOf<Pin?>(null) }
    var editSummary by remember { mutableStateOf<Summary?>(null) }
    var editDirection by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = EmberColors.Bg,
        topBar = {
            EmberTopBar("${character.name} · Memory", onBack) {
                IconButton(onClick = { editPin = Pin(text = "") }) { Icon(Icons.Filled.Add, "Add pinned memory") }
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("Pinned", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("Sent with every reply", fontSize = 13.sp, color = EmberColors.Dim)
                }
            }
            if (chat.pins.isEmpty()) {
                item { Text("Nothing pinned yet. Long-press a message and choose Pin to memory, or tap + above.", color = EmberColors.Muted, fontSize = 14.sp) }
            }
            items(chat.pins, key = { it.id }) { p ->
                Row(
                    Modifier.fillMaxWidth().background(EmberColors.Surface, RoundedCornerShape(14.dp)).clickable { editPin = p }.padding(14.dp),
                ) {
                    Icon(Icons.Filled.PushPin, null, tint = EmberColors.RedSoft, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(p.text, fontSize = 15.sp, lineHeight = 21.sp)
                }
            }

            item {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Story so far", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.summariseNow() }) { Text("Summarise now", color = EmberColors.RedSoft) }
                }
                val s = Store.settings.summariseEvery
                Text(
                    if (s > 0) "A summary is written automatically every $s messages. Older messages are then sent as summaries."
                    else "Automatic summaries are off. Turn them on in Chat settings.",
                    fontSize = 13.sp, color = EmberColors.Muted,
                )
                (vm.error ?: vm.info)?.let { Text(it, fontSize = 13.sp, color = EmberColors.TextSoft, modifier = Modifier.padding(top = 6.dp)) }
            }
            items(chat.summaries.sortedBy { it.from }, key = { it.id }) { s ->
                Row {
                    Box(Modifier.padding(top = 18.dp).size(12.dp).border(2.dp, EmberColors.Red, CircleShape))
                    Spacer(Modifier.width(12.dp))
                    Column(
                        Modifier.fillMaxWidth().background(EmberColors.Surface, RoundedCornerShape(16.dp))
                            .border(1.dp, EmberColors.Line, RoundedCornerShape(16.dp)).clickable { editSummary = s }.padding(14.dp),
                    ) {
                        Row {
                            Text("Messages ${s.from + 1}–${s.to}", fontSize = 12.5.sp, color = EmberColors.Muted, modifier = Modifier.weight(1f))
                            Text("Auto summary · Edit", fontSize = 12.5.sp, color = EmberColors.Muted)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(s.text, fontSize = 15.sp, lineHeight = 22.sp)
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Box(Modifier.size(16.dp).background(EmberColors.Red, CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Text("CURRENT · message ${chat.messages.size}", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
            item {
                Column(
                    Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Color(0xFF3A2220), RoundedCornerShape(18.dp))
                        .border(1.5.dp, EmberColors.Red, RoundedCornerShape(18.dp)).padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Edit, null, tint = EmberColors.RedSoft, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("SCENE DIRECTION", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = EmberColors.RedSoft, letterSpacing = 1.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(chat.direction.ifBlank { "None set. Add one to steer where the story goes." }, fontSize = 16.sp, lineHeight = 23.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { editDirection = true }) { Text(if (chat.direction.isBlank()) "Add" else "Edit", color = EmberColors.Text) }
                        if (chat.direction.isNotBlank()) {
                            OutlinedButton(onClick = { Store.updateChat(chat.id) { it.copy(direction = "") } }) { Text("Clear", color = EmberColors.Text) }
                        }
                    }
                }
            }
        }
    }

    editPin?.let { p ->
        val isNew = chat.pins.none { it.id == p.id }
        TextDialog(
            title = if (isNew) "New pinned memory" else "Pinned memory",
            initial = p.text,
            hint = "A fact that must never be forgotten, e.g. \"Evelyn has a scar on her left wrist.\"",
            onSave = { t ->
                Store.updateChat(chat.id) { c ->
                    when {
                        t.isBlank() -> c.copy(pins = c.pins.filter { it.id != p.id })
                        isNew -> c.copy(pins = c.pins + Pin(text = t.trim()))
                        else -> c.copy(pins = c.pins.map { if (it.id == p.id) it.copy(text = t.trim()) else it })
                    }
                }
                editPin = null
            },
            onDismiss = { editPin = null },
            extra = if (isNew) null else {
                { TextButton(onClick = { Store.updateChat(chat.id) { c -> c.copy(pins = c.pins.filter { it.id != p.id }) }; editPin = null }) { Text("Delete", color = EmberColors.RedSoft) } }
            },
        )
    }

    editSummary?.let { s ->
        TextDialog(
            title = "Summary of messages ${s.from + 1}–${s.to}",
            initial = s.text,
            onSave = { t -> Store.updateChat(chat.id) { c -> c.copy(summaries = c.summaries.map { if (it.id == s.id) it.copy(text = t.trim()) else it }) }; editSummary = null },
            onDismiss = { editSummary = null },
            extra = {
                TextButton(onClick = {
                    // Deleting a summary also drops the ones after it, so those messages are sent in full again.
                    Store.updateChat(chat.id) { c -> c.copy(summaries = c.summaries.filter { it.to <= s.from }) }
                    editSummary = null
                }) { Text("Delete", color = EmberColors.RedSoft) }
            },
        )
    }

    if (editDirection) {
        TextDialog(
            title = "Scene direction", initial = chat.direction,
            hint = "Where the story should go. Stays active until you clear it.",
            onSave = { t -> Store.updateChat(chat.id) { it.copy(direction = t.trim()) }; editDirection = false },
            onDismiss = { editDirection = false },
        )
    }
}
