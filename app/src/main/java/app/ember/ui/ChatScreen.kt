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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Edit
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
import app.ember.data.Message
import app.ember.data.Store
import coil.compose.AsyncImage
import java.io.File

private val LENGTHS = listOf(150, 200, 300, 500)
private val CharBubble = Color(0xE03A2523)
private val UserBubble = Color(0xD9140807)
private val Chip = Color(0x66000000)
private val Cream = Color(0xFFFFF8F0)

@Composable
fun ChatScreen(
    characterId: String,
    onBack: () -> Unit,
    editCharacter: () -> Unit,
    openRules: () -> Unit,
    openSettings: () -> Unit,
) {
    val vm: ChatViewModel = viewModel()
    LaunchedEffect(characterId) { vm.bind(characterId) }

    val character = Store.character(characterId)
    if (character == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val chat = vm.chat
    val messages = chat?.messages.orEmpty()
    val replyWords = chat?.replyWords ?: character.replyWords

    var input by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var customLength by remember { mutableStateOf(false) }
    var confirmNewChat by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val hasHeader = character.description.isNotBlank()

    // Keep the newest text in view as it streams in.
    val lastLen = messages.lastOrNull()?.text?.length ?: 0
    LaunchedEffect(messages.size, lastLen) {
        val total = listState.layoutInfo.totalItemsCount
        if (total > 0) listState.scrollToItem(total - 1, Int.MAX_VALUE / 4)
    }

    Box(Modifier.fillMaxSize().background(EmberColors.BgDeep)) {
        val bg = character.chatBg ?: character.avatar
        if (bg != null) {
            AsyncImage(
                model = File(bg), contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0x99140807), Color(0x66140807), Color(0xCC140807)))
            )
        )

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            // Top bar
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Cream) }
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(EmberColors.Field)
                        .border(2.dp, Color(0x66FFFFFF), CircleShape)
                ) {
                    if (character.avatar != null) {
                        AsyncImage(
                            model = File(character.avatar), contentDescription = null,
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(character.name, color = Cream, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Store.settings.model, color = Cream.copy(alpha = 0.8f), fontSize = 12.sp)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Chat menu", tint = Cream) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Start new chat") }, onClick = { menu = false; confirmNewChat = true })
                        DropdownMenuItem(text = { Text("Edit character") }, onClick = { menu = false; editCharacter() })
                        DropdownMenuItem(text = { Text("Rules") }, onClick = { menu = false; openRules() })
                        DropdownMenuItem(text = { Text("Settings") }, onClick = { menu = false; openSettings() })
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
                            character.description, color = Cream.copy(alpha = 0.85f), fontSize = 13.sp,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.fillMaxWidth().background(Chip, RoundedCornerShape(12.dp)).padding(12.dp),
                        )
                    }
                }
                itemsIndexed(messages, key = { _, m -> m.id }) { i, m ->
                    MessageItem(
                        m = m,
                        isLast = i == messages.lastIndex,
                        streaming = vm.streamingId == m.id,
                        busy = vm.busy,
                        vm = vm,
                    )
                }
            }

            vm.error?.let { err ->
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth()
                        .background(Color(0xE6401A18), RoundedCornerShape(12.dp)).padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(err, color = Cream, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
                    IconButton(onClick = { vm.error = null }) { Icon(Icons.Filled.Close, "Dismiss", tint = Cream) }
                }
            }

            // Reply length chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item {
                    Text("Reply", color = Cream.copy(alpha = 0.8f), fontSize = 12.sp, modifier = Modifier.padding(end = 2.dp))
                }
                items(LENGTHS) { w ->
                    LengthChip("$w", on = replyWords == w) { vm.setLength(w) }
                }
                item {
                    val custom = replyWords !in LENGTHS
                    LengthChip(if (custom) "$replyWords" else "Custom", on = custom) { customLength = true }
                }
            }

            // Input
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
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
            confirmButton = {
                TextButton(onClick = {
                    value.toIntOrNull()?.let { vm.setLength(it) }
                    customLength = false
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { customLength = false }) { Text("Cancel") } },
            containerColor = EmberColors.Surface,
        )
    }

    if (confirmNewChat) {
        AlertDialog(
            onDismissRequest = { confirmNewChat = false },
            title = { Text("Start a new chat?") },
            text = { Text("This starts again from the opening message. The current chat stays saved on your phone.") },
            confirmButton = { TextButton(onClick = { vm.newChat(); confirmNewChat = false }) { Text("Start new chat") } },
            dismissButton = { TextButton(onClick = { confirmNewChat = false }) { Text("Cancel") } },
            containerColor = EmberColors.Surface,
        )
    }
}

@Composable
private fun LengthChip(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(32.dp).clip(RoundedCornerShape(16.dp))
            .background(if (on) Cream else Chip).clickable(onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (on) Color(0xFF2A1A10) else Cream, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MessageItem(m: Message, isLast: Boolean, streaming: Boolean, busy: Boolean, vm: ChatViewModel) {
    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var confirmRewind by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (m.fromUser) Alignment.End else Alignment.Start,
    ) {
        Box {
            Box(
                Modifier.widthIn(max = if (m.fromUser) 300.dp else 360.dp)
                    .clip(
                        if (m.fromUser) RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
                        else RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
                    )
                    .background(if (m.fromUser) UserBubble else CharBubble)
                    .combinedClickable(onClick = {}, onLongClick = { if (!busy) menu = true })
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (streaming && m.text.isEmpty()) {
                    Text("…", color = Cream, fontSize = 18.sp)
                } else {
                    Text(formatStory(m.text), color = Cream, fontSize = 15.5.sp, lineHeight = 23.sp)
                }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; editing = true })
                DropdownMenuItem(text = { Text("Copy") }, onClick = {
                    menu = false; clipboard.setText(AnnotatedString(m.text))
                })
                if (!isLast) {
                    DropdownMenuItem(text = { Text("Rewind to here") }, onClick = { menu = false; confirmRewind = true })
                }
                DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; vm.deleteMessage(m.id) })
            }
        }

        if (!m.fromUser && isLast && !busy) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RoundAction(Icons.Filled.Refresh, "Regenerate") { vm.regenerate() }
                RoundAction(Icons.Outlined.Edit, "Edit reply") { editing = true }
                RoundAction(Icons.AutoMirrored.Filled.ArrowForward, "Continue reply") { vm.continueLast() }
                if (m.versions.size > 1) {
                    Row(
                        Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(Chip),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { vm.setVersion(m.id, m.sel - 1) }, enabled = m.sel > 0, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.ChevronLeft, "Previous version", tint = Cream)
                        }
                        Text("${m.sel + 1}/${m.versions.size}", color = Cream, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        IconButton(
                            onClick = { vm.setVersion(m.id, m.sel + 1) },
                            enabled = m.sel < m.versions.size - 1, modifier = Modifier.size(36.dp),
                        ) { Icon(Icons.Filled.ChevronRight, "Next version", tint = Cream) }
                    }
                }
            }
        }
    }

    if (editing) {
        var text by remember(m.id, m.sel) { mutableStateOf(m.text) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Edit message") },
            text = {
                TextField(
                    value = text, onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 420.dp),
                    colors = fieldColors(), shape = RoundedCornerShape(12.dp),
                )
            },
            confirmButton = { TextButton(onClick = { vm.editMessage(m.id, text); editing = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } },
            containerColor = EmberColors.Surface,
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
private fun RoundAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(Chip).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = Cream, modifier = Modifier.size(19.dp)) }
}
