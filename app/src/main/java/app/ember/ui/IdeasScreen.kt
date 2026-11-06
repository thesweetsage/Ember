package app.ember.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ember.ai.Ai
import app.ember.ai.CreativePrompts
import app.ember.data.Character
import app.ember.data.Idea
import app.ember.data.Store
import app.ember.data.Tags
import kotlinx.coroutines.launch

enum class IdeaMode(val label: String) { Tags("Tags"), Roulette("Trope roulette"), Favourites("Inspired by favourites"), Vein("In the vein of…") }

/** The roulette's slots, one per tag category. */
private val SLOTS = listOf("Personality", "Setting", "Occupation", "Trope & dynamic", "Kinks")

/** The characters you've written the most with, most first. */
fun favouriteCharacters(limit: Int): List<Character> =
    Store.chats.groupBy { it.characterId }
        .mapValues { (_, cs) -> cs.sumOf { c -> c.messages.sumOf { wordCount(it.text) } } }
        .toList().sortedByDescending { it.second }
        .mapNotNull { Store.character(it.first) }.filter { !it.archived }.take(limit)

class IdeasViewModel : ViewModel() {
    var mode by mutableStateOf(IdeaMode.Tags)
    var tags by mutableStateOf(listOf<String>())
    var notes by mutableStateOf("")
    var vein by mutableStateOf("")
    /** "Man", "Woman" or "Any". */
    var gender by mutableStateOf("Man")
    var results by mutableStateOf(listOf<String>())
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)

    // Trope roulette: one tag per slot, some locked.
    val slots = mutableStateMapOf<String, String>()
    val locked = mutableStateListOf<String>()

    fun spin() {
        SLOTS.forEach { cat ->
            if (cat in locked && slots[cat] != null) return@forEach
            val pool = (Tags.categories.firstOrNull { it.name == cat }?.suggestions.orEmpty() +
                (Store.customTags + Store.characters.flatMap { it.tags }).filter { Tags.category(it) == cat }.map { Tags.name(it) }).distinct()
            pool.filter { it != slots[cat] }.randomOrNull()?.let { slots[cat] = it }
        }
    }

    private fun currentTags(): List<String> = when (mode) {
        IdeaMode.Roulette -> SLOTS.mapNotNull { c -> slots[c]?.let { Tags.make(c, it) } }
        IdeaMode.Tags -> tags
        else -> emptyList()
    }

    fun generate(more: Boolean) {
        if (busy) return
        if (mode == IdeaMode.Vein && vein.isBlank()) { error = "Write a character or trope you love first."; return }
        if (mode == IdeaMode.Roulette && slots.isEmpty()) spin()
        val favs = if (mode == IdeaMode.Favourites) favouriteCharacters(5) else emptyList()
        if (mode == IdeaMode.Favourites && favs.isEmpty()) { error = "Chat with a few characters first, then this has something to learn from."; return }
        busy = true; error = null
        val avoid = if (more) results else emptyList()
        val usedTags = currentTags()
        viewModelScope.launch {
            try {
                val prompt = CreativePrompts.ideas(
                    usedTags, notes, 6, avoid, gender,
                    favourites = CreativePrompts.favouritesBrief(favs),
                    vein = if (mode == IdeaMode.Vein) vein else "",
                    liked = Store.liked, disliked = Store.disliked,
                )
                val text = Ai.complete(prompt, null, 0.9, preferBackup = Ai.creativeOnBackup)
                val parsed = CreativePrompts.parseIdeas(text)
                if (parsed.isEmpty()) error = "No ideas came back. Try again."
                results = if (more) results + parsed else parsed
                lastTags = usedTags
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong."
            } finally {
                busy = false
            }
        }
    }

    /** The tags the current results were made with (saved with an idea). */
    var lastTags = listOf<String>()
        private set

    fun dismiss(text: String) { results = results.filter { it != text } }

    // ----- teasers -----
    val teasers = mutableStateMapOf<String, String>()
    val teaserBusy = mutableStateListOf<String>()

    fun teaser(text: String, savedId: String? = null) {
        if (text in teaserBusy) return
        teaserBusy.add(text)
        viewModelScope.launch {
            try {
                val t = Ai.complete(CreativePrompts.teaser(text), null, 0.95, preferBackup = Ai.creativeOnBackup).trim()
                if (t.isNotBlank()) {
                    teasers[text] = t
                    savedId?.let { id -> Store.updateIdea(id) { it.copy(teaser = t) } }
                }
            } catch (e: Exception) {
                error = e.message ?: "Couldn't write a teaser."
            } finally {
                teaserBusy.remove(text)
            }
        }
    }

    // ----- idea of the day -----
    var dailyBusy by mutableStateOf(false)
        private set

    fun loadDaily() {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        if (Store.dailyIdea?.first == today || dailyBusy || !Store.hasApiKey) return
        dailyBusy = true
        // Seeded by a couple of tags from the characters you love, plus your thumbs up and down.
        val favTags = favouriteCharacters(5).flatMap { it.tags }.shuffled().take(2)
        viewModelScope.launch {
            try {
                val text = Ai.complete(
                    CreativePrompts.ideas(favTags, "", 1, Store.ideas.map { it.text }.take(10), "Man", liked = Store.liked, disliked = Store.disliked),
                    null, 1.0, preferBackup = Ai.creativeOnBackup,
                )
                CreativePrompts.parseIdeas(text).firstOrNull()?.let { Store.setDailyIdea(today, it) }
            } catch (_: Exception) {
                // Quietly try again next time.
            } finally {
                dailyBusy = false
            }
        }
    }

    // ----- scenario ideas for your characters -----
    var scenarioFor by mutableStateOf<String?>(null)
    var scenarioIdeas by mutableStateOf(listOf<String>())
        private set
    var scenarioBusy by mutableStateOf(false)
        private set

    fun suggestScenarios(c: Character, more: Boolean) {
        if (scenarioBusy) return
        if (scenarioFor != c.id) { scenarioIdeas = emptyList() }
        scenarioFor = c.id
        scenarioBusy = true
        viewModelScope.launch {
            try {
                val got = CreativePrompts.parseSuggestions(
                    Ai.complete(CreativePrompts.suggestions(c, false, if (more) scenarioIdeas else emptyList()), null, 1.0, preferBackup = Ai.creativeOnBackup)
                )
                scenarioIdeas = if (more) scenarioIdeas + got else got
                if (got.isEmpty()) error = "No ideas came back. Try again."
            } catch (e: Exception) {
                error = e.message ?: "Couldn't get ideas."
            } finally {
                scenarioBusy = false
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IdeasScreen(
    buildFrom: (Idea) -> Unit,
    bottomBar: @Composable () -> Unit,
    openChat: (String) -> Unit = {},
) {
    val vm: IdeasViewModel = viewModel()
    var pickTags by remember { mutableStateOf(false) }
    var folderFilter by remember { mutableStateOf<String?>(null) }
    var moving by remember { mutableStateOf<Idea?>(null) }
    var noting by remember { mutableStateOf<Idea?>(null) }
    var startScenario by remember { mutableStateOf<Pair<Character, String>?>(null) }

    LaunchedEffect(Unit) { vm.loadDaily() }

    fun save(text: String, tags: List<String>) {
        val existing = Store.ideas.firstOrNull { it.text == text }
        if (existing != null) Store.deleteIdea(existing.id)
        else Store.saveIdea(Idea(text = text, tags = tags, teaser = vm.teasers[text].orEmpty()))
    }

    Scaffold(containerColor = EmberColors.Bg, bottomBar = bottomBar) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Ideas", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.statusBarsPadding().padding(top = 12.dp))
                Text("Short, punchy character concepts. Thumbs up or down teaches it your taste.", fontSize = 13.sp, color = EmberColors.Muted)
            }

            // Idea of the day
            val daily = Store.dailyIdea?.second
            if (daily != null || vm.dailyBusy) item {
                Column(
                    Modifier.fillMaxWidth()
                        .background(Brush.linearGradient(listOf(Color(0xFF7A1E2C), Color(0xFF3A1016))), RoundedCornerShape(18.dp))
                        .padding(16.dp),
                ) {
                    Text("IDEA OF THE DAY", color = Color.White.copy(alpha = 0.7f), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
                    Spacer(Modifier.height(6.dp))
                    if (daily == null) Text("Thinking of today's idea…", color = Color.White.copy(alpha = 0.8f))
                    else {
                        Text(daily, color = Color.White, fontSize = 16.5.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium)
                        vm.teasers[daily]?.let { Spacer(Modifier.height(8.dp)); Text(formatStory(it), color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, lineHeight = 20.sp, fontStyle = FontStyle.Italic) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val saved = Store.ideas.any { it.text == daily }
                            TextButton(onClick = { save(daily, emptyList()) }) { Text(if (saved) "Saved ✓" else "Save", color = Color.White) }
                            if (vm.teasers[daily] == null) TextButton(onClick = { vm.teaser(daily) }, enabled = daily !in vm.teaserBusy) {
                                Text(if (daily in vm.teaserBusy) "Writing…" else "Teaser", color = Color.White)
                            }
                            TextButton(onClick = { buildFrom(Idea(text = daily)) }) { Text("Build ›", color = Color.White) }
                        }
                    }
                }
            }

            // Generator
            item {
                Column(
                    Modifier.fillMaxWidth().background(EmberColors.Surface, RoundedCornerShape(18.dp)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        IdeaMode.entries.forEach { m -> TagChip(m.label, on = vm.mode == m) { vm.mode = m; vm.error = null } }
                    }
                    when (vm.mode) {
                        IdeaMode.Tags -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Tags", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                TextButton(onClick = { pickTags = true }) {
                                    Text(if (vm.tags.isEmpty()) "+ Choose tags" else "Edit (${vm.tags.size})", color = EmberColors.RedSoft)
                                }
                            }
                            if (vm.tags.isNotEmpty()) TagSummary(vm.tags)
                        }
                        IdeaMode.Roulette -> {
                            Text("Spin for a random mix. Tap a tag to lock it, then spin the rest.", fontSize = 13.sp, color = EmberColors.Muted)
                            SLOTS.forEach { cat ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(cat, fontSize = 13.sp, color = EmberColors.Muted, modifier = Modifier.width(120.dp))
                                    val v = vm.slots[cat]
                                    if (v != null) {
                                        val isLocked = cat in vm.locked
                                        TagChip((if (isLocked) "🔒 " else "") + v, on = isLocked) {
                                            if (isLocked) vm.locked.remove(cat) else vm.locked.add(cat)
                                        }
                                    } else Text("…", color = EmberColors.Dim)
                                }
                            }
                            OutlinedButton(onClick = { vm.spin() }, shape = RoundedCornerShape(24.dp)) { Text("🎲  Spin", color = EmberColors.Text) }
                        }
                        IdeaMode.Favourites -> {
                            val favs = favouriteCharacters(5)
                            if (favs.isEmpty()) Text("Chat with a few characters first, then ideas can learn from them.", fontSize = 13.sp, color = EmberColors.Muted)
                            else {
                                Text("New men with the same appeal as the ones you write with most:", fontSize = 13.sp, color = EmberColors.Muted)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    favs.forEach { c ->
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(58.dp)) {
                                            Avatar(c.avatar, c.name, 48.dp)
                                            Text(c.name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = EmberColors.TextSoft)
                                        }
                                    }
                                }
                            }
                        }
                        IdeaMode.Vein -> {
                            EmberField(
                                "A character or trope you love", vm.vein, { vm.vein = it }, minLines = 2, maxLines = 4,
                                placeholder = "e.g. a cold fae prince who'd burn the world for her, or the hockey captain from a sports romance",
                            )
                            Text("You get original men with that energy, never copies.", fontSize = 12.5.sp, color = EmberColors.Dim)
                        }
                    }
                    Text("Character", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Man", "Woman", "Group", "Any").forEach { g -> TagChip(g, on = vm.gender == g) { vm.gender = g } }
                    }
                    EmberField(
                        "Anything else (optional)", vm.notes, { vm.notes = it }, minLines = 2, maxLines = 4,
                        placeholder = "e.g. built around pet play, set in a church, he's a BMX rider",
                    )
                    Button(
                        onClick = { vm.generate(more = false) }, enabled = !vm.busy,
                        modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(25.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmberColors.Red),
                    ) { Text(if (vm.busy) "Thinking…" else "Give me ideas", fontWeight = FontWeight.SemiBold) }
                    vm.error?.let { Text(it, color = EmberColors.RedSoft, fontSize = 13.sp) }
                }
            }

            items(vm.results, key = { "r:$it" }) { text ->
                val rating = when (text) { in Store.liked -> true; in Store.disliked -> false; else -> null }
                IdeaCard(
                    text = text, teaser = vm.teasers[text], teaserBusy = text in vm.teaserBusy, note = "",
                    saved = Store.ideas.any { it.text == text },
                    onSave = { save(text, vm.lastTags) },
                    onBuild = { buildFrom(Idea(text = text, tags = vm.lastTags)) },
                    onTeaser = { vm.teaser(text) },
                    rating = rating,
                    onRate = { up ->
                        Store.rateIdea(text, if (rating == up) null else up)
                        if (up == false && rating != false) vm.dismiss(text)
                    },
                )
            }
            if (vm.results.isNotEmpty()) item {
                OutlinedButton(
                    onClick = { vm.generate(more = true) }, enabled = !vm.busy,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                ) { Text(if (vm.busy) "Thinking…" else "More ideas", color = EmberColors.Text) }
            }

            // Scenario ideas for your characters
            val yours = Store.characters.filter { !it.archived }.sortedByDescending { maxOf(Store.lastActivity(it.id), it.createdAt) }
            if (yours.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("New scenarios for your characters", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Pick someone for fresh setups; start one and it opens as a new chat.", fontSize = 13.sp, color = EmberColors.Muted)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(yours, key = { it.id }) { c ->
                            val on = vm.scenarioFor == c.id
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(64.dp).clickable { vm.suggestScenarios(c, more = false) },
                            ) {
                                Box(
                                    Modifier.border(2.dp, if (on) EmberColors.Red else Color.Transparent, RoundedCornerShape(30.dp)).padding(3.dp),
                                ) { Avatar(c.avatar, c.name, 50.dp) }
                                Text(c.name, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (on) EmberColors.Text else EmberColors.TextSoft)
                            }
                        }
                    }
                }
                val forChar = vm.scenarioFor?.let { Store.character(it) }
                if (forChar != null) {
                    if (vm.scenarioBusy && vm.scenarioIdeas.isEmpty()) item { Text("Thinking of setups for ${forChar.name}…", color = EmberColors.Muted, fontSize = 14.sp) }
                    items(vm.scenarioIdeas, key = { "s:$it" }) { setup ->
                        Row(
                            Modifier.fillMaxWidth().background(EmberColors.Surface, RoundedCornerShape(14.dp))
                                .padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(setup, fontSize = 14.5.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = { startScenario = forChar to setup }) { Text("Start ›", color = EmberColors.RedSoft) }
                        }
                    }
                    if (vm.scenarioIdeas.isNotEmpty()) item {
                        TextButton(onClick = { vm.suggestScenarios(forChar, more = true) }, enabled = !vm.scenarioBusy) {
                            Text(if (vm.scenarioBusy) "Thinking…" else "More setups for ${forChar.name}", color = EmberColors.RedSoft)
                        }
                    }
                }
            }

            // Saved ideas, with folders and notes
            if (Store.ideas.isNotEmpty()) {
                val folders = Store.ideas.map { it.folder }.filter { it.isNotBlank() }.distinct().sorted()
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("Saved ideas", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    if (folders.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            TagChip("All", on = folderFilter == null) { folderFilter = null }
                            folders.forEach { f -> TagChip(f, on = folderFilter == f) { folderFilter = f } }
                            TagChip("No folder", on = folderFilter == "") { folderFilter = "" }
                        }
                    }
                }
                val shown = Store.ideas.filter { folderFilter == null || it.folder == folderFilter }
                items(shown, key = { it.id }) { idea ->
                    IdeaCard(
                        text = idea.text, teaser = idea.teaser.ifBlank { null } ?: vm.teasers[idea.text],
                        teaserBusy = idea.text in vm.teaserBusy, note = idea.note, folder = idea.folder,
                        saved = true,
                        onSave = { Store.deleteIdea(idea.id) },
                        onBuild = { buildFrom(idea) },
                        onTeaser = { vm.teaser(idea.text, idea.id) },
                        menu = listOf(
                            "Move to folder…" to { moving = idea },
                            (if (idea.note.isBlank()) "Add a note" else "Edit note") to { noting = idea },
                            "Delete" to { Store.deleteIdea(idea.id) },
                        ),
                    )
                }
            }
        }
    }

    if (pickTags) {
        TagPickerSheet(
            title = "Tags for new ideas", initial = vm.tags,
            onDone = { vm.tags = it; pickTags = false }, onDismiss = { pickTags = false },
        )
    }
    noting?.let { idea ->
        TextDialog(
            title = "Note", initial = idea.note,
            onSave = { t -> Store.updateIdea(idea.id) { it.copy(note = t.trim()) }; noting = null },
            onDismiss = { noting = null },
        )
    }
    moving?.let { idea -> FolderDialog(idea, onDone = { moving = null }) }
    startScenario?.let { (c, setup) ->
        NewScenarioDialog(c, initialSetup = setup, onStarted = { id -> startScenario = null; openChat(id) }, onDismiss = { startScenario = null })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FolderDialog(idea: Idea, onDone: () -> Unit) {
    var name by remember { mutableStateOf("") }
    val folders = Store.ideas.map { it.folder }.filter { it.isNotBlank() }.distinct().sorted()
    fun move(f: String) { Store.updateIdea(idea.id) { it.copy(folder = f) }; onDone() }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Move to folder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (folders.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    folders.forEach { f -> TagChip(f, on = idea.folder == f) { move(f) } }
                    if (idea.folder.isNotBlank()) TagChip("No folder", on = false) { move("") }
                }
                TextField(
                    value = name, onValueChange = { name = it.take(30) }, singleLine = true,
                    placeholder = { Text("New folder, e.g. Slow burn", color = EmberColors.Dim) },
                    colors = fieldColors(), shape = RoundedCornerShape(12.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) move(name.trim()) }, enabled = name.isNotBlank()) { Text("Create and move") } },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } },
        containerColor = EmberColors.Surface,
    )
}

@Composable
private fun IdeaCard(
    text: String,
    teaser: String?,
    teaserBusy: Boolean,
    note: String,
    saved: Boolean,
    onSave: () -> Unit,
    onBuild: () -> Unit,
    onTeaser: () -> Unit,
    folder: String = "",
    rating: Boolean? = null,
    onRate: ((Boolean) -> Unit)? = null,
    menu: List<Pair<String, () -> Unit>> = emptyList(),
) {
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().background(EmberColors.Surface, RoundedCornerShape(16.dp))
            .border(1.dp, EmberColors.Line, RoundedCornerShape(16.dp)).padding(start = 16.dp, top = 14.dp, end = 6.dp, bottom = 4.dp),
    ) {
        Row {
            Column(Modifier.weight(1f)) {
                if (folder.isNotBlank()) Text(folder.uppercase(), fontSize = 10.5.sp, color = EmberColors.RedSoft, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                Text(text, fontSize = 15.5.sp, lineHeight = 22.sp)
            }
            if (menu.isEmpty()) {
                IconButton(onClick = onSave) {
                    if (saved) Icon(Icons.Filled.Bookmark, "Saved", tint = EmberColors.RedSoft)
                    else Icon(Icons.Outlined.BookmarkBorder, "Save idea", tint = EmberColors.Muted)
                }
            } else Box {
                IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, "More", tint = EmberColors.Muted) }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    menu.forEach { (label, action) -> DropdownMenuItem(text = { Text(label) }, onClick = { open = false; action() }) }
                }
            }
        }
        if (teaser != null) {
            Spacer(Modifier.height(8.dp))
            Text(formatStory(teaser), fontSize = 14.sp, lineHeight = 20.sp, fontStyle = FontStyle.Italic, color = EmberColors.TextSoft, modifier = Modifier.padding(end = 10.dp))
        }
        if (note.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text("Note: $note", fontSize = 13.sp, color = EmberColors.Muted, modifier = Modifier.padding(end = 10.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBuild) { Text("Build ›", color = EmberColors.RedSoft) }
            if (teaser == null) TextButton(onClick = onTeaser, enabled = !teaserBusy) {
                Text(if (teaserBusy) "Writing…" else "Teaser", color = EmberColors.TextSoft)
            }
            Spacer(Modifier.weight(1f))
            if (onRate != null) {
                IconButton(onClick = { onRate(true) }, modifier = Modifier.size(40.dp)) {
                    Icon(if (rating == true) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, "More like this",
                        tint = if (rating == true) EmberColors.RedSoft else EmberColors.Muted, modifier = Modifier.size(19.dp))
                }
                IconButton(onClick = { onRate(false) }, modifier = Modifier.size(40.dp)) {
                    Icon(if (rating == false) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown, "Less like this",
                        tint = EmberColors.Muted, modifier = Modifier.size(19.dp))
                }
            }
        }
    }
}
