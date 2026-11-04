package app.ember.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Rule
import androidx.compose.material.icons.outlined.ShortText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ember.data.Chat
import app.ember.data.Message
import app.ember.data.Store
import coil.compose.AsyncImage
import java.io.File

private val LENGTHS = listOf(150, 200, 300, 500)
private val Chip = Color(0x66000000)
private val Cream = Color(0xFFFFF8F0)

/** Colours for one chat: tinted from the character's photo, or the default burgundy. */
data class ChatPalette(val bubble: Color, val user: Color, val chip: Color)

fun chatPalette(chat: Chat?, tint: Int?): ChatPalette {
    val opacity = (chat?.bubbleOpacity ?: 0.85f).coerceIn(0.3f, 1f)
    val base = if (chat?.tintFromPhoto != false && tint != null) Color(tint) else Color(0xFF3A2523)
    return ChatPalette(
        bubble = base.copy(alpha = opacity),
        user = Color(0xFF140807).copy(alpha = (opacity + 0.05f).coerceAtMost(1f)),
        chip = Chip,
    )
}

@Composable
fun ChatScreen(
    characterId: String,
    onBack: () -> Unit,
    editCharacter: () -> Unit,
    openRules: () -> Unit,
    openChatSettings: () -> Unit,
    openMemory: () -> Unit,
    openArchives: () -> Unit,
    openSearch: () -> Unit,
    openPersonas: () -> Unit,
) {
    val vm: ChatViewModel = viewModel()
    LaunchedEffect(characterId) { vm.bind(characterId) }

    val character = Store.character(characterId)
    if (character == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val chat = Store.activeChat(characterId)
    val messages = chat?.messages.orEmpty()
    val replyWords = chat?.replyWords ?: character.replyWords
    val persona = Store.personaFor(chat, character)
    val preset = Store.preset(chat?.presetId)
    val pal = chatPalette(chat, character.tint)
    val textSize = (chat?.textSize ?: 16).sp

    var input by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var customLength by remember { mutableStateOf(false) }
    var confirmNewChat by remember { mutableStateOf(false) }
    var pickPersona by remember { mutableStateOf(false) }
    var pickPreset by remember { mutableStateOf(false) }
    var editDirection by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val hasHeader = character.description.isNotBlank()

    // Keep the newest text in view as it streams in, unless search asked for a specific message.
    val lastLen = messages.lastOrNull()?.text?.length ?: 0
    LaunchedEffect(messages.size, lastLen, chat?.id) {
        val focus = Store.focusMessageId
        if (focus != null) {
            val i = messages.indexOfFirst { it.id == focus }
            if (i >= 0) listState.scrollToItem(i + if (hasHeader) 1 else 0)
            Store.focusMessageId = null
        } else {
            val total = listState.layoutInfo.totalItemsCount
            if (total > 0) listState.scrollToItem(total - 1, Int.MAX_VALUE / 4)
        }
    }

    Box(Modifier.fillMaxSize().background(EmberColors.BgDeep)) {
        val bg = character.chatBg ?: character.avatar
        if (bg != null) {
            AsyncImage(model = File(bg), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x99140807), Color(0x55140807), Color(0xCC140807)))))

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            // Top bar
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Cream) }
                Box(Modifier.border(2.dp, Color(0x66FFFFFF), CircleShape)) { Avatar(character.avatar, character.name, 40.dp) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(character.name, color = Cream, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val model = if (chat?.useBackup == true && Store.backupReady) Store.settings.backupModel else Store.settings.model
                    Text(model, color = Cream.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 1)
                }
                RoundIcon(Icons.Outlined.Lightbulb, "Memory", onClick = openMemory)
                Spacer(Modifier.width(6.dp))
                RoundIcon(Icons.Filled.Menu, "Chat settings", onClick = openChatSettings)
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More", tint = Cream) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Start new chat") }, onClick = { menu = false; confirmNewChat = true })
                        DropdownMenuItem(text = { Text("Chat archives") }, onClick = { menu = false; openArchives() })
                        DropdownMenuItem(text = { Text("Search chat history") }, onClick = { menu = false; openSearch() })
                        DropdownMenuItem(text = { Text("Edit character") }, onClick = { menu = false; editCharacter() })
                        DropdownMenuItem(text = { Text("Rules") }, onClick = { menu = false; openRules() })
                    }
                }
            }

            // Scene direction banner
            if (!chat?.direction.isNullOrBlank()) {
                Row(
                    Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(Color(0x80000000)).clickable { editDirection = true }.padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Edit, null, tint = Cream, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        AnnotatedString("Direction: ") + AnnotatedString(chat?.direction.orEmpty()),
                        color = Cream, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                    )
                    IconButton(onClick = { vm.updateChat { it.copy(direction = "") } }) {
                        Icon(Icons.Filled.Close, "Clear direction", tint = Cream, modifier = Modifier.size(18.dp))
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (hasHeader) {
                    item(key = "header") {
                        Text(
                            character.description, color = Cream.copy(alpha = 0.85f), fontSize = 13.sp, fontStyle = FontStyle.Italic,
                            modifier = Modifier.fillMaxWidth().background(Chip, RoundedCornerShape(12.dp)).padding(12.dp),
                        )
                    }
                }
                itemsIndexed(messages, key = { _, m -> m.id }) { i, m ->
                    MessageItem(m, isLast = i == messages.lastIndex, streaming = vm.streamingId == m.id, vm = vm, pal = pal, textSize = textSize.value)
                }
            }

            (vm.error ?: vm.info)?.let { note ->
                val isError = vm.error != null
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth()
                        .background(if (isError) Color(0xE6401A18) else Color(0xCC000000), RoundedCornerShape(12.dp)).padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(note, color = Cream, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
                    IconButton(onClick = { vm.error = null; vm.info = null }) { Icon(Icons.Filled.Close, "Dismiss", tint = Cream) }
                }
            }

            // Persona, preset and length chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item { IconChip(Icons.Outlined.Person, persona?.name ?: "No persona") { pickPersona = true } }
                item { IconChip(Icons.Outlined.Rule, preset?.name ?: "No preset") { pickPreset = true } }
                if (chat?.direction.isNullOrBlank()) {
                    item { IconChip(Icons.Outlined.Edit, "Direction") { editDirection = true } }
                }
                item { Icon(Icons.Outlined.ShortText, "Reply length", tint = Cream.copy(alpha = 0.8f), modifier = Modifier.padding(start = 4.dp).size(18.dp)) }
                items(LENGTHS) { w -> LengthChip("$w", on = replyWords == w) { vm.setLength(w) } }
                item {
                    val custom = replyWords !in LENGTHS
                    LengthChip(if (custom) "$replyWords" else "Custom", on = custom) { customLength = true }
                }
            }

            // Input
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp, max = 200.dp),
                    placeholder = { Text("Write your reply…", color = Cream.copy(alpha = 0.7f)) },
                    shape = RoundedCornerShape(26.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0x8C000000), unfocusedContainerColor = Color(0x8C000000),
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Cream, unfocusedTextColor = Cream, cursorColor = EmberColors.RedSoft,
                    ),
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(Color(0x8C000000))
                        .border(1.5.dp, Cream.copy(alpha = 0.6f), CircleShape)
                        .clickable {
                            if (input.isNotBlank() && vm.wfmNotes.isBlank()) vm.wfmNotes = input
                            vm.openWriteForMe()
                        },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Draw, "Write for me", tint = Cream) }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(EmberColors.Red).clickable {
                        if (vm.busy) vm.stop() else { vm.send(input); input = "" }
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    if (vm.busy) Icon(Icons.Filled.Stop, "Stop", tint = Color.White)
                    else Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = Color.White)
                }
            }
        }
    }

    if (vm.wfmOpen) {
        WriteForMeSheet(vm, personaName = persona?.name ?: "you", onSent = { input = "" })
    }

    if (customLength) {
        var value by remember { mutableStateOf(replyWords.toString()) }
        AlertDialog(
            onDismissRequest = { customLength = false },
            title = { Text("Reply length") },
            text = {
                Column {
                    Text("About how many words should replies be?", color = EmberColors.Muted, fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    TextField(
                        value = value, onValueChange = { value = it.filter(Char::isDigit).take(4) },
                        singleLine = true, colors = fieldColors(), shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { value.toIntOrNull()?.let { vm.setLength(it) }; customLength = false }) { Text("Set") } },
            dismissButton = { TextButton(onClick = { customLength = false }) { Text("Cancel") } },
            containerColor = EmberColors.Surface,
        )
    }

    if (confirmNewChat) {
        AlertDialog(
            onDismissRequest = { confirmNewChat = false },
            title = { Text("Start a new chat?") },
            text = { Text("This starts again from the opening message. The current chat stays in Chat archives.") },
            confirmButton = { TextButton(onClick = { vm.newChat(); confirmNewChat = false }) { Text("Start new chat") } },
            dismissButton = { TextButton(onClick = { confirmNewChat = false }) { Text("Cancel") } },
            containerColor = EmberColors.Surface,
        )
    }

    if (pickPersona) {
        PickerDialog(
            title = "Your persona in this chat",
            items = Store.personas,
            label = { it.name },
            sub = { it.description.take(80) },
            isSelected = { it.id == persona?.id },
            onPick = { p -> if (p != null) vm.updateChat { it.copy(personaId = p.id) }; pickPersona = false },
            onDismiss = { pickPersona = false },
        )
        if (Store.personas.isEmpty()) {
            LaunchedEffect(Unit) { pickPersona = false; openPersonas() }
        }
    }

    if (pickPreset) {
        PickerDialog(
            title = "Rules preset for this chat",
            items = Store.presets,
            label = { it.name },
            sub = { "${it.rules.size} rules" },
            isSelected = { it.id == chat?.presetId },
            noneLabel = "No preset",
            noneSelected = chat?.presetId == null,
            onPick = { p -> vm.updateChat { it.copy(presetId = p?.id) }; pickPreset = false },
            onDismiss = { pickPreset = false },
        )
    }

    if (editDirection) {
        TextDialog(
            title = "Scene direction",
            initial = chat?.direction.orEmpty(),
            hint = "Where the story should go. Stays active until you clear it, e.g. \"slow burn, no kiss yet\".",
            onSave = { t -> vm.updateChat { it.copy(direction = t.trim()) }; editDirection = false },
            onDismiss = { editDirection = false },
        )
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(Chip).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = Cream, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun IconChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(Chip).clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Cream, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Cream, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
fun LengthChip(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(32.dp).clip(RoundedCornerShape(16.dp))
            .background(if (on) Cream else Chip).clickable(onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (on) Color(0xFF2A1A10) else Cream, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MessageItem(m: Message, isLast: Boolean, streaming: Boolean, vm: ChatViewModel, pal: ChatPalette, textSize: Float) {
    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var confirmRewind by remember { mutableStateOf(false) }
    var pinning by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val busy = vm.busy

    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (m.fromUser) Alignment.End else Alignment.Start) {
        Box {
            Box(
                Modifier.widthIn(max = if (m.fromUser) 300.dp else 360.dp)
                    .clip(if (m.fromUser) RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp) else RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
                    .background(if (m.fromUser) pal.user else pal.bubble)
                    .combinedClickable(onClick = {}, onLongClick = { if (!busy) menu = true })
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (streaming && m.text.isEmpty()) Text("…", color = Cream, fontSize = 18.sp)
                else Text(formatStory(m.text), color = Cream, fontSize = textSize.sp, lineHeight = (textSize * 1.48f).sp)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; editing = true })
                DropdownMenuItem(text = { Text("Copy") }, onClick = { menu = false; clipboard.setText(AnnotatedString(m.text)) })
                DropdownMenuItem(text = { Text("Pin to memory") }, onClick = { menu = false; pinning = true })
                if (!isLast) DropdownMenuItem(text = { Text("Rewind to here") }, onClick = { menu = false; confirmRewind = true })
                DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; vm.deleteMessage(m.id) })
            }
        }

        val showActions = !m.fromUser && isLast && !busy
        val showSwipe = !m.fromUser && m.versions.size > 1 && !streaming
        if (showActions || showSwipe) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (showActions) {
                    RoundAction(Icons.Filled.Refresh, "Regenerate") { vm.regenerate() }
                    RoundAction(Icons.Outlined.Edit, "Edit reply") { editing = true }
                    RoundAction(Icons.AutoMirrored.Filled.ArrowForward, "Continue reply") { vm.continueLast() }
                    RoundAction(Icons.Filled.PushPin, "Pin to memory") { pinning = true }
                }
                if (showSwipe) {
                    Row(Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(Chip), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { vm.setVersion(m.id, m.sel - 1) }, enabled = m.sel > 0 && !busy, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.ChevronLeft, "Previous version", tint = Cream)
                        }
                        Text("${m.sel + 1}/${m.versions.size}", color = Cream, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = { vm.setVersion(m.id, m.sel + 1) }, enabled = m.sel < m.versions.size - 1 && !busy, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.ChevronRight, "Next version", tint = Cream)
                        }
                    }
                }
            }
        }
    }

    if (editing) {
        TextDialog(
            title = "Edit message", initial = m.text,
            onSave = { vm.editMessage(m.id, it); editing = false }, onDismiss = { editing = false },
        )
    }
    if (pinning) {
        TextDialog(
            title = "Pin to memory", initial = m.text.take(500),
            hint = "Trim this down to the fact worth remembering. Pinned memories are sent with every reply.",
            saveLabel = "Pin",
            onSave = { vm.pin(it); pinning = false }, onDismiss = { pinning = false },
        )
    }
    if (confirmRewind) {
        AlertDialog(
            onDismissRequest = { confirmRewind = false },
            title = { Text("Rewind to here?") },
            text = { Text("Every message after this one will be deleted.") },
            confirmButton = { TextButton(onClick = { vm.rewindTo(m.id); confirmRewind = false }) { Text("Rewind") } },
            dismissButton = { TextButton(onClick = { confirmRewind = false }) { Text("Cancel") } },
            containerColor = EmberColors.Surface,
        )
    }
}

@Composable
private fun RoundAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(Chip).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = Cream, modifier = Modifier.size(19.dp)) }
}
