package app.ember.ui

import androidx.compose.foundation.background
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
    onDismiss: () -> Unit,
    extra: @Composable () -> Unit = {},
    actions: @Composable (result: String) -> Unit,
) {
    var note by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

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
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 200.dp),
                    colors = fieldColors(), shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
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
fun NewScenarioDialog(character: Character, onStarted: (chatCharacterId: String) -> Unit, onDismiss: () -> Unit) {
    var keep by remember { mutableStateOf(false) }
    val persona = Store.persona(character.personaId ?: Store.settings.defaultPersonaId)
    AiHelperDialog(
        title = "New scenario with ${character.name}",
        explain = "Same man, new story: describe the setup and he gets a fresh opening scene in a new chat. Your current chat stays in the archives.",
        noteLabel = "The setup",
        notePlaceholder = "e.g. we're strangers stuck in the same cabin during a snowstorm",
        noteRequired = true,
        prompt = { setup -> CreativePrompts.scenario(character, setup, persona) },
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
    return c.copy(
        id = newId(),
        name = pick("name", c.name).lines().first().trim().trim('*', '"').take(60),
        description = pick("short description", c.description),
        personality = pick("personality", c.personality),
        background = pick("background", c.background),
        openings = parts["opening message"]?.takeIf { it.isNotBlank() }?.let { listOf(it) } ?: c.openings,
        example = pick("example dialogue", c.example),
        avatar = Store.copyImage(c.avatar),
        chatBg = Store.copyImage(c.chatBg),
        favourite = false, archived = false,
        createdAt = System.currentTimeMillis(),
    )
}
