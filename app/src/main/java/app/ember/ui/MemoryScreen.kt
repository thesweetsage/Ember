package app.ember.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
    var newFact by remember { mutableStateOf("") }
    var editSummary by remember { mutableStateOf<Summary?>(null) }
    var editDirection by remember { mutableStateOf(false) }
    var editScene by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = EmberColors.Bg,
        topBar = {
            EmberTopBar("${character.name} · Memory", onBack) {
                IconButton(onClick = { editPin = Pin(text = "") }) { Icon(Icons.Filled.Add, "Add a thing to remember") }
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text("Things to remember", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Facts the story must always respect, like how long you've been together. Sent with every reply.", fontSize = 12.5.sp, color = EmberColors.Dim)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.TextField(
                        value = newFact, onValueChange = { newFact = it },
                        placeholder = { Text("e.g. They've been dating for a month and live together", color = EmberColors.Dim) },
                        trailingIcon = { MicButton({ newFact = appendDictation(newFact, it) }) },
                        modifier = Modifier.weight(1f), colors = fieldColors(), shape = RoundedCornerShape(14.dp),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                    )
                    TextButton(
                        onClick = {
                            val t = newFact.trim()
                            if (t.isNotEmpty()) Store.updateChat(chat.id) { c -> c.copy(pins = c.pins + Pin(text = t)) }
                            newFact = ""
                        },
                        enabled = newFact.isNotBlank(),
                    ) { Text("Add", color = EmberColors.RedSoft) }
                }
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
            item { Spacer(Modifier.height(8.dp)) }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Scene tracker", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Updated every 6 messages by your backup model · tap to edit", fontSize = 12.5.sp, color = EmberColors.Dim)
                    }
                    androidx.compose.material3.Switch(
                        checked = chat.sceneOn,
                        onCheckedChange = { on -> Store.updateChat(chat.id) { it.copy(sceneOn = on) } },
                        colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = EmberColors.Red),
                    )
                }
            }
            if (chat.sceneOn) item {
                Column(
                    Modifier.fillMaxWidth().background(EmberColors.Surface, RoundedCornerShape(14.dp)).clickable { editScene = true }.padding(14.dp),
                ) {
                    Text(
                        chat.scene.ifBlank { "Nothing yet. It fills in after the first couple of messages." },
                        fontSize = 14.5.sp, lineHeight = 21.sp, color = if (chat.scene.isBlank()) EmberColors.Muted else EmberColors.Text,
                    )
                }
                TextButton(onClick = { vm.updateSceneNow() }, enabled = !vm.sceneUpdating) {
                    Text(if (vm.sceneUpdating) "Updating…" else "Update now", color = EmberColors.RedSoft)
                }
            }

            if (chat.sceneOn) item {
                Text("How ${character?.name ?: "he"} feels about you", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Moves gradually with the story, and he's told where things stand.", fontSize = 12.5.sp, color = EmberColors.Dim)
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier.fillMaxWidth().background(EmberColors.Surface, RoundedCornerShape(14.dp)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (chat.meters.isEmpty()) Text("Fills in with the scene tracker.", fontSize = 14.sp, color = EmberColors.Muted)
                    else app.ember.data.METERS.forEach { k ->
                        val v = chat.meters[k] ?: 0
                        Column {
                            Row {
                                Text(k, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
                                Text("$v", fontSize = 13.5.sp, color = EmberColors.Muted)
                            }
                            Spacer(Modifier.height(4.dp))
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { v / 100f }, modifier = Modifier.fillMaxWidth().height(6.dp),
                                color = EmberColors.Red, trackColor = EmberColors.Line,
                            )
                        }
                    }
                }
            }

            if (chat.sceneOn) item {
                Spacer(Modifier.height(4.dp))
                Text("Story timeline", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Turning points, added automatically. Tap one to jump to it; long-press to remove it.", fontSize = 12.5.sp, color = EmberColors.Dim)
                Spacer(Modifier.height(8.dp))
                if (chat.timeline.isEmpty()) Text("Nothing yet. First meetings, first kisses and big moments appear here.", fontSize = 14.sp, color = EmberColors.Muted)
                chat.timeline.forEachIndexed { i, m ->
                    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()
                        .combinedClickable(
                            onClick = { Store.focusMessageId = m.messageId; onBack() },
                            onLongClick = { Store.updateChat(chat.id) { c -> c.copy(timeline = c.timeline.filter { it.id != m.id }) } },
                        )) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(end = 12.dp)) {
                            androidx.compose.foundation.layout.Box(Modifier.padding(top = 5.dp).size(10.dp).background(EmberColors.Red, androidx.compose.foundation.shape.CircleShape))
                            if (i < chat.timeline.lastIndex) androidx.compose.foundation.layout.Box(Modifier.width(2.dp).height(30.dp).background(EmberColors.Line))
                        }
                        Column {
                            Text(m.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text("Message ${m.at}", fontSize = 12.sp, color = EmberColors.Muted)
                        }
                    }
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
            title = if (isNew) "New thing to remember" else "Thing to remember",
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

    if (editScene) {
        TextDialog(
            title = "Scene", initial = chat.scene,
            onSave = { t -> Store.updateChat(chat.id) { it.copy(scene = t.trim(), sceneAt = it.messages.size) }; editScene = false },
            onDismiss = { editScene = false },
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
