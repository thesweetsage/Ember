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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ember.ai.Ai
import app.ember.ai.ChatMsg
import app.ember.ai.CreativePrompts
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.remember
import app.ember.data.BuildMsg
import app.ember.data.BuildSession
import app.ember.data.Character
import app.ember.data.newId
import app.ember.data.Idea
import app.ember.data.Store
import app.ember.data.Tags
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class BuilderViewModel : ViewModel() {
    var messages by mutableStateOf(listOf<BuildMsg>())
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    /** Sections you've accepted, by section name. */
    var draft by mutableStateOf(mapOf<String, String>())
        private set
    var tags by mutableStateOf(listOf<String>())
        private set
    var sessionId: String = newId()
        private set
    private var started = false
    private var job: Job? = null

    /** Saves the conversation so it survives leaving the screen or closing the app. */
    private var discarded = false

    private fun persist() {
        if (discarded || messages.none { it.fromUser }) return
        Store.saveBuild(BuildSession(sessionId, messages.filter { it.text.isNotBlank() }, draft, tags))
    }

    fun updateTags(list: List<String>) { tags = list; persist() }

    fun discard() { discarded = true; job?.cancel(); Store.deleteBuild(sessionId) }

    fun start(buildId: String?, seed: Idea?) {
        if (started) return
        started = true
        Store.build(buildId)?.let { b ->
            sessionId = b.id; messages = b.messages; draft = b.draft; tags = b.tags
            return
        }
        if (seed != null) {
            tags = seed.tags
            send(
                "I want to build a character from this concept: ${seed.text}" +
                    (if (seed.tags.isNotEmpty()) "\nTags: ${Tags.describe(seed.tags)}" else "") +
                    "\nWe can skip the three concepts; ask me anything you need, then let's start with the name."
            )
        }
    }

    fun send(text: String) {
        if (busy || text.isBlank()) return
        messages = messages + BuildMsg(true, text.trim())
        persist()
        val persona = Store.persona(Store.settings.defaultPersonaId)
        val prompt = buildList {
            add(ChatMsg("system", CreativePrompts.builderSystem(persona)))
            if (tags.isNotEmpty()) add(ChatMsg("system", "Tags she has picked so far: ${Tags.describe(tags)}"))
            messages.forEach { add(ChatMsg(if (it.fromUser) "user" else "assistant", it.text)) }
        }
        messages = messages + BuildMsg(false, "")
        val idx = messages.lastIndex
        // The opening message is real story prose, so it goes to your main writer; everything else to the backup.
        val openingPhase = text.contains("opening", ignoreCase = true) ||
            ("Background" in draft && "Opening message" !in draft)
        busy = true; error = null
        job = viewModelScope.launch {
            try {
                Ai.stream(prompt, null, 1.0, preferBackup = Ai.creativeOnBackup && !openingPhase) { t -> messages = messages.toMutableList().also { if (idx < it.size) it[idx] = BuildMsg(false, t) } }
            } catch (e: CancellationException) {
                // stopped
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong."
            } finally {
                if (messages.getOrNull(idx)?.text.isNullOrBlank()) messages = messages.filterIndexed { i, _ -> i != idx }
                busy = false
                persist()
            }
        }
    }

    fun stop() { job?.cancel() }

    fun accept(section: String, text: String) {
        if (section.equals("Tags", ignoreCase = true)) {
            tags = (tags + CreativePrompts.parseTags(text)).distinct()
            draft = draft + ("Tags" to text)
        } else {
            val key = CreativePrompts.SECTIONS.firstOrNull { it.equals(section, ignoreCase = true) } ?: section
            draft = draft + (key to text)
        }
        persist()
    }

    fun toCharacter(): Character = Character(
        name = draft["Name"]?.lines()?.firstOrNull()?.trim()?.trim('*', '"')?.take(60).orEmpty(),
        description = draft["Short description"].orEmpty(),
        personality = draft["Personality"].orEmpty(),
        background = draft["Background"].orEmpty(),
        openings = listOf(draft["Opening message"].orEmpty()),
        example = draft["Example dialogue"].orEmpty(),
        tags = tags,
        replyWords = 0,
    )
}

private val QUICK = listOf("Give me 3 concepts", "Next section", "Make it darker", "More dominant", "Shorter", "Start the opening message")

@Composable
fun BuilderScreen(buildId: String?, onBack: () -> Unit, openEditor: () -> Unit) {
    val vm: BuilderViewModel = viewModel()
    LaunchedEffect(Unit) {
        vm.start(buildId, Store.builderSeed)
        Store.builderSeed = null
    }
    var confirmLeave by remember { mutableStateOf(false) }
    val tryLeave: () -> Unit = { if (vm.messages.any { it.fromUser }) { confirmLeave = true } else { onBack() } }
    BackHandler { tryLeave() }
    var input by rememberSaveable { mutableStateOf("") }
    var pickTags by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val lastLen = vm.messages.lastOrNull()?.text?.length ?: 0
    LaunchedEffect(vm.messages.size, lastLen) {
        val total = listState.layoutInfo.totalItemsCount
        if (total > 0) listState.scrollToItem(total - 1, Int.MAX_VALUE / 4)
    }

    Scaffold(
        containerColor = EmberColors.Bg,
        topBar = {
            EmberTopBar("Build with AI", tryLeave) {
                TextButton(onClick = { pickTags = true }) { Text("Tags (${vm.tags.size})", color = EmberColors.RedSoft) }
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).navigationBarsPadding().imePadding()) {
            // Progress through the sections
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(CreativePrompts.SECTIONS) { s ->
                    val done = vm.draft.containsKey(s) || (s == "Tags" && vm.tags.isNotEmpty())
                    Row(
                        Modifier.clip(RoundedCornerShape(14.dp)).background(if (done) EmberColors.Red else EmberColors.Field)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (done) { Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(13.dp)); Spacer(Modifier.width(4.dp)) }
                        Text(s, fontSize = 12.sp, color = if (done) Color.White else EmberColors.Muted)
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (vm.messages.isEmpty()) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().background(EmberColors.Surface, RoundedCornerShape(16.dp)).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("Tell me what you'd like", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Describe the kind of character, a setting, a job, a kink, or just pick tags. I'll ask a few questions, " +
                                    "offer three concepts, then we'll write him section by section. Tap \"Use\" on each section you like.",
                                fontSize = 14.sp, color = EmberColors.Muted, lineHeight = 20.sp,
                            )
                        }
                    }
                }
                itemsIndexed(vm.messages) { i, m ->
                    if (m.fromUser) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                            Text(
                                m.text, fontSize = 15.sp, lineHeight = 21.sp,
                                modifier = Modifier.widthIn(max = 300.dp).background(EmberColors.Field, RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)).padding(12.dp),
                            )
                        }
                    } else {
                        val streaming = vm.busy && i == vm.messages.lastIndex
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (m.text.isEmpty()) Text("…", color = EmberColors.Muted)
                            CreativePrompts.parseBuilder(m.text).forEach { part ->
                                if (part.section == null) {
                                    Text(
                                        formatStory(part.text), fontSize = 15.sp, lineHeight = 21.sp,
                                        modifier = Modifier.widthIn(max = 340.dp).background(EmberColors.Surface, RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)).padding(12.dp),
                                    )
                                } else {
                                    SectionCard(part.section, part.text, streaming, accepted = vm.draft[sectionKey(part.section)] == part.text) {
                                        vm.accept(part.section, part.text)
                                    }
                                }
                            }
                        }
                    }
                }
                vm.error?.let { item { Text(it, color = EmberColors.RedSoft, fontSize = 13.sp) } }
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(QUICK) { q -> TagChip(q, on = false) { if (!vm.busy) vm.send(q) } }
            }

            if (vm.draft.containsKey("Name")) {
                Button(
                    onClick = {
                        Store.editorDraft = vm.toCharacter()
                        Store.editorDraftBuildId = vm.sessionId
                        openEditor()
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).height(46.dp),
                    shape = RoundedCornerShape(23.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmberColors.Red),
                ) { Text("Open in editor (${vm.draft.size} sections ready)", fontWeight = FontWeight.SemiBold) }
            }

            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Bottom) {
                TextField(
                    value = input, onValueChange = { input = it },
                    trailingIcon = { MicButton({ input = appendDictation(input, it) }) },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp, max = 180.dp),
                    placeholder = { Text("e.g. a priest and an atheist, dominant, pet play", color = EmberColors.Dim) },
                    colors = fieldColors(), shape = RoundedCornerShape(26.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
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

    if (pickTags) {
        TagPickerSheet(
            title = "Tags for this character", initial = vm.tags,
            onDone = { vm.updateTags(it); pickTags = false }, onDismiss = { pickTags = false },
        )
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave the builder?") },
            text = {
                Text("Your progress is saved. Pick it up any time from + → Continue building, or discard it now.")
            },
            confirmButton = { TextButton(onClick = { confirmLeave = false; onBack() }) { Text("Leave, keep it saved") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmLeave = false; vm.discard(); onBack() }) { Text("Discard", color = EmberColors.RedSoft) }
                    TextButton(onClick = { confirmLeave = false }) { Text("Stay") }
                }
            },
            containerColor = EmberColors.Surface,
        )
    }
}

private fun sectionKey(s: String) = CreativePrompts.SECTIONS.firstOrNull { it.equals(s, ignoreCase = true) } ?: s

@Composable
private fun SectionCard(title: String, text: String, streaming: Boolean, accepted: Boolean, onUse: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(EmberColors.Surface, RoundedCornerShape(16.dp))
            .border(1.5.dp, if (accepted) EmberColors.Red else EmberColors.Outline, RoundedCornerShape(16.dp)).padding(14.dp),
    ) {
        Text(title.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EmberColors.RedSoft, letterSpacing = 1.sp)
        Spacer(Modifier.height(6.dp))
        Text(formatStory(text), fontSize = 15.sp, lineHeight = 22.sp)
        if (!streaming) {
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onUse, enabled = !accepted) {
                Text(if (accepted) "✓ Using this" else "Use as $title", color = if (accepted) EmberColors.Muted else EmberColors.RedSoft)
            }
        }
    }
}
