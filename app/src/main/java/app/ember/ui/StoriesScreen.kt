package app.ember.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ember.ai.StoryWriter
import app.ember.ai.UsageTag
import app.ember.data.Chat
import app.ember.data.Message
import app.ember.data.Milestone
import app.ember.data.Store
import app.ember.data.Story
import app.ember.work.Generation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val LENGTHS = listOf(1500, 2500, 4000)
private val HOURS = listOf(6, 7, 8, 9, 12, 18, 20, 21, 22, 23)

private fun hourLabel(h: Int) = "%02d:00".format(h)

/** The key the writer runs under, so the screen knows a chapter is being written. */
private fun storyKey(id: String) = "story:$id"

/** Writes the next chapter in the background (keeps going if you leave), and tells you when it's ready. */
fun writeChapterNow(storyId: String) {
    val st = Store.story(storyId) ?: return
    if (Generation.isRunning(storyKey(storyId))) return
    val name = Store.character(st.characterId)?.name ?: "your character"
    Generation.launch(storyKey(storyId), UsageTag(st.characterId), label = "Writing Chapter ${st.chapters.size + 1} with $name…") {
        val problem = runCatching { StoryWriter.writeNext(storyId) }.getOrElse { it.message ?: "Something went wrong." }
        storyErrors[storyId] = problem
        if (problem == null && !Generation.appVisible) Store.story(storyId)?.chapters?.lastOrNull()?.let { ch ->
            Generation.notifyStory(storyId, "New chapter with $name: ${ch.title}", ch.text.take(300))
        }
    }
}

/** The last problem writing each story, shown on its screen. */
private val storyErrors = androidx.compose.runtime.mutableStateMapOf<String, String?>()

/** The "Daily stories" tab: your stories, and a button to start one. */
@Composable
fun StoriesTab(openStory: (String) -> Unit) {
    var creating by remember { mutableStateOf(false) }
    val stories = Store.stories.sortedByDescending { it.chapters.lastOrNull()?.createdAt ?: it.createdAt }
    LazyColumn(
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                "A new chapter every day, written while you're away. It starts from the character's first message, with the persona you choose.",
                fontSize = 13.sp, color = EmberColors.Muted, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            )
            Button(
                onClick = { creating = true }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmberColors.Red),
            ) { Text("+ New daily story", fontWeight = FontWeight.SemiBold) }
        }
        items(stories, key = { it.id }) { st ->
            val c = Store.character(st.characterId)
            val unread = st.chapters.count { !it.read }
            Row(
                Modifier.fillMaxWidth().background(EmberColors.Surface, RoundedCornerShape(16.dp)).clickable { openStory(st.id) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(c?.avatar, c?.name ?: "?", 52.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(c?.name ?: "Deleted character", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    val last = st.chapters.lastOrNull()
                    Text(
                        when {
                            Generation.isRunning(storyKey(st.id)) -> "Writing Chapter ${st.chapters.size + 1}…"
                            last != null -> "Chapter ${st.chapters.size}: ${last.title}"
                            else -> "Chapter 1 coming"
                        },
                        fontSize = 13.5.sp, color = EmberColors.TextSoft, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(if (st.active) "New chapter daily at ${hourLabel(st.hour)}" else "Paused", fontSize = 12.sp, color = EmberColors.Muted)
                }
                if (unread > 0) Box(
                    Modifier.size(24.dp).background(EmberColors.Red, CircleShape), contentAlignment = Alignment.Center,
                ) { Text("$unread", fontSize = 12.sp, color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold) }
            }
        }
    }
    if (creating) NewStoryDialog(onDone = { id -> creating = false; if (id != null) openStory(id) })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewStoryDialog(onDone: (String?) -> Unit) {
    val chars = Store.characters.filter { !it.archived }.sortedByDescending { maxOf(Store.lastActivity(it.id), it.createdAt) }
    var charId by remember { mutableStateOf(chars.firstOrNull()?.id) }
    val c = charId?.let { Store.character(it) }
    var personaId by remember(charId) { mutableStateOf(c?.personaId ?: Store.settings.defaultPersonaId ?: Store.personas.firstOrNull()?.id) }
    var words by remember { mutableStateOf(2500) }
    var hour by remember { mutableStateOf(8) }
    var direction by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text("New daily story") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.imePadding()) {
                Text("Character", fontSize = 13.sp, color = EmberColors.TextSoft)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(chars, key = { it.id }) { ch ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(60.dp).clickable { charId = ch.id }) {
                            Box(Modifier.border(2.dp, if (ch.id == charId) EmberColors.Red else androidx.compose.ui.graphics.Color.Transparent, CircleShape).padding(2.dp)) {
                                Avatar(ch.avatar, ch.name, 46.dp)
                            }
                            Text(ch.name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (Store.personas.isNotEmpty()) {
                    Text("You, as", fontSize = 13.sp, color = EmberColors.TextSoft)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Store.personas.forEach { p -> TagChip(p.name, on = p.id == personaId) { personaId = p.id } }
                    }
                }
                Text("Chapter length", fontSize = 13.sp, color = EmberColors.TextSoft)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { LENGTHS.forEach { w -> TagChip("~$w words", on = words == w) { words = w } } }
                Text("New chapter every day at", fontSize = 13.sp, color = EmberColors.TextSoft)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HOURS.forEach { h -> TagChip(hourLabel(h), on = hour == h) { hour = h } }
                }
                TextField(
                    value = direction, onValueChange = { direction = it }, maxLines = 3,
                    placeholder = { Text("Where Chapter 1 should go (optional)", color = EmberColors.Dim) },
                    colors = fieldColors(), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = charId != null, onClick = {
                val st = Story(characterId = charId!!, personaId = personaId, words = words, hour = hour, nextDirection = direction.trim())
                Store.putStory(st)
                writeChapterNow(st.id)
                onDone(st.id)
            }) { Text("Start and write Chapter 1") }
        },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text("Cancel") } },
        containerColor = EmberColors.Surface,
    )
}

/** Reading a daily story, steering the next chapter, and its settings. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StoryScreen(id: String, onBack: () -> Unit) {
    val st = Store.story(id)
    val c = st?.let { Store.character(it.characterId) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val writing = Generation.isRunning(storyKey(id))
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/epub+zip")) { uri ->
        val s = Store.story(id); val ch = s?.let { Store.character(it.characterId) }
        if (uri != null && s != null && ch != null) scope.launch {
            // A story exports like a chat: one chapter per message, each named by its title.
            val msgs = s.chapters.map { Message(fromUser = false, versions = listOf(it.text), chapter = true) }
            val chat = Chat(characterId = ch.id, personaId = s.personaId, messages = msgs,
                timeline = s.chapters.zip(msgs).mapIndexed { i, (chp, m) -> Milestone(text = chp.title, messageId = m.id, at = i + 1) })
            note = try {
                withContext(Dispatchers.IO) { app.ember.data.BookExport.export(context, uri, ch, listOf(chat)) }
                "Book saved."
            } catch (e: Exception) { e.message ?: "Couldn't export the book." }
        }
    }
    LaunchedEffect(st?.chapters?.size) { if (st != null && st.chapters.any { !it.read }) Store.updateStory(id) { s -> s.copy(chapters = s.chapters.map { it.copy(read = true) }) } }

    Scaffold(containerColor = EmberColors.Bg, topBar = { EmberTopBar(c?.name ?: "Story", onBack) }) { pad ->
        if (st == null) { Text("This story is gone.", modifier = Modifier.padding(pad).padding(16.dp)); return@Scaffold }
        LazyColumn(
            Modifier.fillMaxSize().padding(pad).imePadding(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (st.chapters.isEmpty()) item { Text(if (writing) "Writing Chapter 1…" else "No chapters yet.", color = EmberColors.Muted, modifier = Modifier.padding(top = 16.dp)) }
            itemsIndexed(st.chapters, key = { _, ch -> ch.id }) { i, ch ->
                Column(Modifier.padding(top = 16.dp)) {
                    Text("CHAPTER ${i + 1}", fontSize = 12.sp, letterSpacing = 1.5.sp, color = EmberColors.RedSoft, fontWeight = FontWeight.SemiBold)
                    Text(ch.title, fontSize = 22.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(formatStory(ch.text), fontSize = 16.sp, lineHeight = 25.sp, fontFamily = FontFamily.Serif, letterSpacing = 0.sp)
                }
            }
            item {
                Column(
                    Modifier.fillMaxWidth().padding(top = 16.dp).background(EmberColors.Surface, RoundedCornerShape(16.dp)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Where next", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Pick an idea or write your own for Chapter ${st.chapters.size + 1}. Leave it and the story decides.",
                        fontSize = 12.5.sp, color = EmberColors.Muted,
                    )
                    st.suggestions.forEach { idea ->
                        val on = st.nextDirection == idea
                        Text(
                            idea, fontSize = 14.sp, lineHeight = 19.sp, color = if (on) EmberColors.Bg else EmberColors.Text,
                            modifier = Modifier.fillMaxWidth().background(if (on) EmberColors.Text else EmberColors.Field, RoundedCornerShape(12.dp))
                                .clickable { Store.updateStory(id) { it.copy(nextDirection = if (on) "" else idea) } }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                    TextField(
                        value = if (st.nextDirection in st.suggestions) "" else st.nextDirection,
                        onValueChange = { v -> Store.updateStory(id) { it.copy(nextDirection = v) } },
                        maxLines = 4, placeholder = { Text("Or your own idea for the next chapter", color = EmberColors.Dim) },
                        trailingIcon = { MicButton({ t -> Store.updateStory(id) { it.copy(nextDirection = appendDictation(it.nextDirection, t)) } }) },
                        colors = fieldColors(), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                    Button(
                        onClick = { storyErrors[id] = null; writeChapterNow(id) }, enabled = !writing,
                        modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmberColors.Red),
                    ) { Text(if (writing) "Writing Chapter ${st.chapters.size + 1}…" else "Write the next chapter now", fontWeight = FontWeight.SemiBold) }
                    storyErrors[id]?.let { Text(it, color = EmberColors.RedSoft, fontSize = 13.sp) }
                    note?.let { Text(it, color = EmberColors.Muted, fontSize = 13.sp) }
                }
            }
            item {
                SettingsGroup("Story settings") {
                    ToggleRow(
                        "New chapter every day", st.active,
                        sub = if (st.active) "At ${hourLabel(st.hour)}, written while you're away" else "Paused; write chapters yourself with the button above",
                    ) { on -> Store.updateStory(id) { it.copy(active = on) } }
                    Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Time", fontSize = 13.sp, color = EmberColors.TextSoft)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            HOURS.forEach { h -> TagChip(hourLabel(h), on = st.hour == h) { Store.updateStory(id) { it.copy(hour = h) } } }
                        }
                        Text("Chapter length", fontSize = 13.sp, color = EmberColors.TextSoft)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            LENGTHS.forEach { w -> TagChip("~$w words", on = st.words == w) { Store.updateStory(id) { it.copy(words = w) } } }
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { exportLauncher.launch((c?.name ?: "Story") + " story.epub") }, enabled = st.chapters.isNotEmpty(),
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(22.dp),
                    ) { Text("Export as a book", color = EmberColors.Text) }
                    OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(22.dp)) {
                        Text("Delete story", color = EmberColors.RedSoft)
                    }
                }
            }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this story?") },
        text = { Text("All its chapters are deleted. This can't be undone.") },
        confirmButton = { TextButton(onClick = { Store.deleteStory(id); confirmDelete = false; onBack() }) { Text("Delete", color = EmberColors.RedSoft) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        containerColor = EmberColors.Surface,
    )
}
