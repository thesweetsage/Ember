package app.ember.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.ember.ai.Ai
import app.ember.ai.ChatMsg
import app.ember.ai.CreativePrompts
import app.ember.data.Character
import app.ember.data.Store
import app.ember.data.isGroup
import app.ember.data.newId
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Shared full-screen layout for the three AI helpers: a note box, a Generate button,
 * the streamed result, and an action row once it's done.
 */
@Composable
private fun AiHelperDialog(
    title: String,
    explain: String,
    noteLabel: String,
    notePlaceholder: String,
    noteRequired: Boolean,
    prompt: (note: String) -> List<ChatMsg>,
    preferBackup: Boolean,
    showResult: Boolean = true,
    /** When set, a "Suggest ideas" button fills tappable ideas for the note box. */
    suggest: ((avoid: List<String>) -> List<ChatMsg>)? = null,
    initialNote: String = "",
    onDismiss: () -> Unit,
    extra: @Composable () -> Unit = {},
    actions: @Composable (result: String) -> Unit,
) {
    var note by remember { mutableStateOf(initialNote) }
    var result by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    var ideas by remember { mutableStateOf(listOf<String>()) }
    var seen by remember { mutableStateOf(listOf<String>()) }
    var ideasBusy by remember { mutableStateOf(false) }

    fun loadIdeas() {
        val ask = suggest ?: return
        ideasBusy = true; error = null
        scope.launch {
            try {
                val got = CreativePrompts.parseSuggestions(Ai.complete(ask(seen.takeLast(20)), null, 1.0, preferBackup = Ai.creativeOnBackup))
                if (got.isEmpty()) error = "No ideas came back. Try again."
                ideas = got; seen = seen + got
            } catch (e: Exception) {
                error = e.message ?: "Couldn't get ideas."
            } finally {
                ideasBusy = false
            }
        }
    }

    fun generate() {
        job?.cancel()
        result = ""; error = null; busy = true
        job = scope.launch {
            try {
                val out = Ai.stream(prompt(note), null, 0.9, preferBackup = preferBackup) { t -> result = t.trimStart() }
                if (out.isBlank()) error = "The model sent back nothing. Try again."
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong."
            } finally {
                busy = false
            }
        }
    }

    Dialog(onDismissRequest = { job?.cancel(); onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = EmberColors.Bg) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
                    .verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { job?.cancel(); onDismiss() }) { Text("Close") }
                }
                Text(explain, fontSize = 13.sp, color = EmberColors.Muted)
                Text(noteLabel, fontSize = 13.sp, color = EmberColors.TextSoft)
                TextField(
                    value = note, onValueChange = { note = it },
                    trailingIcon = { MicButton({ note = appendDictation(note, it) }) },
                    placeholder = { Text(notePlaceholder, color = EmberColors.Dim) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp), maxLines = 5,
                    colors = fieldColors(), shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                if (suggest != null) {
                    TextButton(onClick = { loadIdeas() }, enabled = !ideasBusy) {
                        Text(
                            if (ideasBusy) "Thinking of ideas…" else if (ideas.isEmpty()) "✦ Suggest ideas" else "✦ More ideas",
                            color = EmberColors.RedSoft,
                        )
                    }
                    ideas.forEach { idea ->
                        Text(
                            idea, fontSize = 14.sp, lineHeight = 19.sp,
                            color = if (idea == note) EmberColors.Bg else EmberColors.Text,
                            modifier = Modifier.fillMaxWidth()
                                .background(if (idea == note) EmberColors.Text else EmberColors.Surface, RoundedCornerShape(12.dp))
                                .clickable { note = idea }.padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }
                extra()
                Button(
                    onClick = { generate() }, enabled = !busy && (!noteRequired || note.isNotBlank()),
                    modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmberColors.Red),
                ) { Text(if (busy) "Writing…" else if (result.isEmpty()) "Generate" else "Try again", fontWeight = FontWeight.SemiBold) }
                error?.let { Text(it, color = EmberColors.RedSoft, fontSize = 13.sp) }
                if (result.isNotEmpty() && showResult) {
                    Column(Modifier.fillMaxWidth().background(EmberColors.Surface, RoundedCornerShape(14.dp)).padding(14.dp)) {
                        Text("${wordCount(result)} words", fontSize = 12.sp, color = EmberColors.Muted)
                        Spacer(Modifier.height(6.dp))
                        Text(formatStory(result), fontSize = 14.5.sp, lineHeight = 20.sp, letterSpacing = 0.sp)
                    }
                } else if (result.isNotEmpty()) {
                    Text("${wordCount(result)} words written", fontSize = 13.sp, color = EmberColors.Muted)
                }
                if (result.isNotBlank() && !busy) actions(result)
            }
        }
    }
}

/** "Improve with AI" for one field of the character editor. Nothing changes until you tap Replace. */
@Composable
fun ImproveFieldDialog(field: String, current: String, card: Character, onReplace: (String) -> Unit, onDismiss: () -> Unit) {
    val opening = field.contains("opening", ignoreCase = true)
    AiHelperDialog(
        title = "Improve: $field",
        explain = "Makes this field richer and more consistent with the rest of the character. Your text only changes if you tap Replace.",
        noteLabel = "What to change (optional)",
        notePlaceholder = "e.g. make him more possessive, add how he talks when he's angry",
        noteRequired = false,
        prompt = { note -> CreativePrompts.improve(card, field, current, note) },
        // The opening is story prose, so it goes to your main writer; the rest to the cheaper backup.
        preferBackup = Ai.creativeOnBackup && !opening,
        onDismiss = onDismiss,
    ) { result ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(25.dp)) {
                Text("Keep mine", color = EmberColors.Text)
            }
            Button(
                onClick = { onReplace(result.trim()); onDismiss() }, modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(25.dp), colors = ButtonDefaults.buttonColors(containerColor = EmberColors.Red),
            ) { Text("Replace", fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** "New scenario": the same character in a new setup, starting a fresh chat. */
@Composable
fun NewScenarioDialog(character: Character, onStarted: (chatCharacterId: String) -> Unit, onDismiss: () -> Unit, initialSetup: String = "") {
    var keep by remember { mutableStateOf(false) }
    val persona = Store.persona(character.personaId ?: Store.settings.defaultPersonaId)
    AiHelperDialog(
        title = "New scenario with ${character.name}",
        explain = (if (character.isGroup) "Same people, new story: describe the setup and they get" else "Same man, new story: describe the setup and he gets") +
            " a fresh opening scene in a new chat. Your current chat stays in the archives.",
        noteLabel = "The setup",
        notePlaceholder = "e.g. we're strangers stuck in the same cabin during a snowstorm",
        noteRequired = true,
        prompt = { setup -> CreativePrompts.scenario(character, setup, persona) },
        suggest = { avoid -> CreativePrompts.suggestions(character, forVariant = false, avoid = avoid) },
        initialNote = initialSetup,
        preferBackup = false,
        onDismiss = onDismiss,
        extra = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = keep, onCheckedChange = { keep = it }, colors = CheckboxDefaults.colors(checkedColor = EmberColors.Red))
                Text("Also save it as an alternate opening", fontSize = 13.sp, color = EmberColors.TextSoft)
            }
        },
    ) { result ->
        Button(
            onClick = {
                if (keep) Store.saveCharacter(character.copy(openings = character.openings.filter { it.isNotBlank() } + result.trim()))
                Store.newChatWithOpening(Store.character(character.id) ?: character, result)
                onStarted(character.id)
            },
            modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(25.dp),
            colors = ButtonDefaults.buttonColors(containerColor = EmberColors.Red),
        ) { Text("Start this chat", fontWeight = FontWeight.SemiBold) }
    }
}

/** "Make a variant": a copy of the character reworked with your changes, opened in the editor to review. */
@Composable
fun VariantDialog(character: Character, onReady: () -> Unit, onDismiss: () -> Unit) {
    AiHelperDialog(
        title = "Make a variant of ${character.name}",
        explain = "Describe what's different and the whole character is reworked to match. It opens in the editor so you can check it before creating. " +
            "Leave it empty and tap Copy as is for a plain duplicate.",
        noteLabel = "What's different",
        notePlaceholder = "e.g. he's her rival instead of her bodyguard, set in 1920s Paris",
        noteRequired = true,
        prompt = { changes -> CreativePrompts.variant(character, changes) },
        suggest = { avoid -> CreativePrompts.suggestions(character, forVariant = true, avoid = avoid) },
        preferBackup = Ai.creativeOnBackup,
        showResult = false,
        onDismiss = onDismiss,
        extra = {
            TextButton(onClick = { Store.editorDraft = variantOf(character, emptyMap()); Store.editorDraftBuildId = null; onReady() }) {
                Text("Copy as is (no AI)", color = EmberColors.RedSoft)
            }
        },
    ) { result ->
        val parts = CreativePrompts.parseBuilder(result).filter { it.section != null }.associate { it.section!!.lowercase() to it.text }
        Text("Sections ready: ${parts.size} of 6", fontSize = 13.sp, color = EmberColors.Muted)
        Button(
            onClick = { Store.editorDraft = variantOf(character, parts); Store.editorDraftBuildId = null; onReady() },
            modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(25.dp),
            colors = ButtonDefaults.buttonColors(containerColor = EmberColors.Red),
        ) { Text("Review in the editor", fontWeight = FontWeight.SemiBold) }
    }
}

/** A new character based on [c], with any sections the AI rewrote; photos are copied, never shared. */
private fun variantOf(c: Character, parts: Map<String, String>): Character {
    fun pick(key: String, old: String) = parts[key]?.takeIf { it.isNotBlank() } ?: old
    val split = if (c.isGroup) parts["personality"]?.let { app.ember.data.splitMembers(it) } else null
    return c.copy(
        members = split?.second ?: c.members.map { it.copy(id = newId()) },
        group = if (split != null) true else c.group,
        id = newId(),
        name = pick("name", c.name).lines().first().trim().trim('*', '"').take(60),
        description = pick("short description", c.description),
        personality = split?.first ?: pick("personality", c.personality),
        background = pick("background", c.background),
        openings = parts["opening message"]?.takeIf { it.isNotBlank() }?.let { listOf(it) } ?: c.openings,
        example = pick("example dialogue", c.example),
        avatar = Store.copyImage(c.avatar),
        chatBg = Store.copyImage(c.chatBg),
        favourite = false, archived = false,
        createdAt = System.currentTimeMillis(),
    )
}

/**
 * "Sort and complete": for characters pasted from another app. Puts personality in Personality and everything else in
 * Background, following the full sheet, keeps every detail, and fills in missing parts. Nothing changes until you tap Replace.
 */
@Composable
fun SortCompleteDialog(card: Character, onReplace: (personality: String, background: String) -> Unit, onDismiss: () -> Unit) {
    AiHelperDialog(
        title = "Sort and complete",
        explain = "Sorts what you pasted into Personality and Background, keeps every detail, removes repeats, and fills in any part of the " +
            "character sheet that's missing (childhood, home, likes, how he is in love, in bed, and so on). You see the result before anything changes.",
        noteLabel = "Anything to keep in mind (optional)",
        notePlaceholder = "e.g. he grew up in Glasgow, keep his kinks exactly as written",
        noteRequired = false,
        prompt = { note -> CreativePrompts.sortAndComplete(card, note) },
        preferBackup = Ai.creativeOnBackup,
        showResult = false,
        onDismiss = onDismiss,
    ) { result ->
        val parts = CreativePrompts.parseBuilder(result).filter { it.section != null }.associate { it.section!!.lowercase() to it.text.trim() }
        val p = parts["personality"].orEmpty()
        val b = parts["background"].orEmpty()
        val added = parts["added"].orEmpty().lines().map { it.trim().trimStart('-', '*', ' ') }.filter { it.isNotEmpty() && !it.equals("nothing", true) }
        if (p.isBlank() || b.isBlank()) {
            Text("The result came back incomplete. Tap Try again.", color = EmberColors.RedSoft, fontSize = 13.sp)
        } else {
            if (added.isNotEmpty()) Text("Filled in because they were missing: ${added.joinToString(", ")}", fontSize = 13.sp, color = EmberColors.Muted)
            listOf("Personality" to p, "Background" to b).forEach { (t, text) ->
                Column(Modifier.fillMaxWidth().background(EmberColors.Surface, RoundedCornerShape(14.dp)).padding(14.dp)) {
                    Text(t.uppercase() + " · ${wordCount(text)} words", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EmberColors.RedSoft, letterSpacing = 1.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(text, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(25.dp)) {
                    Text("Keep mine", color = EmberColors.Text)
                }
                Button(
                    onClick = { onReplace(p, b); onDismiss() }, modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(25.dp), colors = ButtonDefaults.buttonColors(containerColor = EmberColors.Red),
                ) { Text("Replace both", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}
