package app.ember.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Two versions of the last reply, side by side: the current one and the one from another model. */
@Composable
fun CompareScreen(vm: ChatViewModel, current: String, textSize: Float) {
    val model = vm.compareModel ?: return
    Dialog(onDismissRequest = { vm.closeCompare(false) }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = EmberColors.Bg) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(12.dp)) {
                Text("Side by side", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Same moment, two models. Keep the new one and both stay as swipes on the message.", fontSize = 12.5.sp, color = EmberColors.Muted)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompareColumn("Current reply", current, textSize, Modifier.weight(1f))
                    val body = when {
                        vm.compareText.isNotEmpty() -> vm.compareText
                        vm.compareError != null -> vm.compareError!!
                        vm.thinking -> "Thinking…"
                        else -> "Writing…"
                    }
                    CompareColumn(model.substringAfter('/'), body, textSize, Modifier.weight(1f), highlight = true)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { vm.closeCompare(false) }, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(25.dp),
                    ) { Text("Keep current", color = EmberColors.Text) }
                    Button(
                        onClick = { vm.closeCompare(true) }, enabled = !vm.compareBusy && vm.compareText.isNotBlank(),
                        modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(25.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmberColors.Red),
                    ) { Text("Use new one", fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

@Composable
private fun CompareColumn(title: String, text: String, textSize: Float, modifier: Modifier, highlight: Boolean = false) {
    Column(
        modifier.fillMaxSize().background(EmberColors.Surface, RoundedCornerShape(14.dp))
            .border(1.5.dp, if (highlight) EmberColors.Red else EmberColors.Line, RoundedCornerShape(14.dp)).padding(10.dp),
    ) {
        Text(title, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = EmberColors.RedSoft, maxLines = 1)
        Text("${wordCount(text)} words", fontSize = 11.5.sp, color = EmberColors.Muted)
        Spacer(Modifier.height(6.dp))
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(formatStory(text), fontSize = (textSize - 2f).sp, lineHeight = ((textSize - 2f) * 1.38f).sp, letterSpacing = 0.sp, color = EmberColors.Text)
        }
    }
}

/** Asks whether to export just this chat or every chat with the character. */
@Composable
fun ExportBookDialog(characterName: String, chatCount: Int, onPick: (all: Boolean) -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export as a book") },
        text = {
            Column {
                Text(
                    "Makes an EPUB you can open in Kobo, Google Play Books or any e-reader, with $characterName's photo as the cover. " +
                        "Each chat, time skip and scene change starts a new chapter.",
                    fontSize = 13.5.sp, color = EmberColors.TextSoft,
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { onPick(false) }, modifier = Modifier.fillMaxWidth()) { Text("This chat", color = EmberColors.RedSoft) }
                if (chatCount > 1) {
                    TextButton(onClick = { onPick(true) }, modifier = Modifier.fillMaxWidth()) {
                        Text("All $chatCount chats with $characterName", color = EmberColors.RedSoft)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = EmberColors.Surface,
    )
}


/** A direction for the next reply: what she sees, what the model is told, and whether it starts a new chapter. */
private data class Direction(val label: String, val guide: String, val chapter: Boolean)

private fun directions(group: Boolean) = listOf(
    Direction("Stay in the scene", "Keep going in this moment; no jump in time or place.", false),
    Direction("Next scene", "Bring this scene to a natural close in a line or two, then jump to the next meaningful scene. Make the jump clear in the first line.", true),
    Direction("Time skip", "Jump ahead in time: at least several hours, often days or weeks, past the current scene. Open after the jump, say how much time has passed and what happened in between in a sentence or two, then land in a new moment.", true),
    Direction("Somewhere new", "Move the story to a new place that fits where things are heading, set through what the characters notice.", true),
    Direction("Something unexpected", "Something unexpected happens: an arrival, a call, news, a discovery, something going wrong. It must fit this world and raise the stakes.", false),
    Direction(if (group) "A new side of them" else "A new side of him",
        if (group) "One of them reveals something new about themselves: a secret, a memory, a want, a side not shown before; the others react."
        else "He reveals something new about himself, prompted by the moment: a secret, a memory, a want, a side not shown before.", false),
    Direction("Turn up the heat", "Raise the tension and desire, true to who they are and where the scene already is.", false),
)

private val MOODS = listOf("Tender", "Playful", "Flirty", "Cosy", "Tense", "Dramatic", "Spicy", "Dark", "Funny")

/**
 * "What next?": pick a direction, a mood, who to focus on (for groups) and add your own thoughts, then preview three
 * places the story could go. The first follows your choices closely, the second takes the same road differently,
 * the third offers another mood. Tap one and the next reply goes there.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun WhatNextSheet(vm: ChatViewModel, characterName: String, group: Boolean, members: List<String>, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dirs = remember(group) { directions(group) }
    var dir by remember { mutableStateOf<Direction?>(null) }
    var mood by remember { mutableStateOf<String?>(null) }
    var focus by remember { mutableStateOf(setOf<String>()) }
    var note by remember { mutableStateOf("") }
    fun choice() = ChatViewModel.WhatNextChoice(
        direction = dir?.label, directionGuide = dir?.guide, chapter = dir?.chapter == true,
        mood = mood, focus = focus.toList(), note = note,
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = EmberColors.Bg) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(bottom = 16.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("What next?", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Choose anything you like (or nothing), then preview where the story could go.", fontSize = 13.sp, color = EmberColors.Muted)

            Text("Direction", fontSize = 13.sp, color = EmberColors.TextSoft)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                dirs.forEach { d -> TagChip(d.label, on = dir == d) { dir = if (dir == d) null else d } }
            }
            Text("Mood", fontSize = 13.sp, color = EmberColors.TextSoft)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MOODS.forEach { m -> TagChip(m, on = mood == m) { mood = if (mood == m) null else m } }
            }
            if (group && members.isNotEmpty()) {
                Text("Focus on", fontSize = 13.sp, color = EmberColors.TextSoft)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    members.forEach { n -> TagChip(n, on = n in focus) { focus = if (n in focus) focus - n else focus + n } }
                    TagChip("Everyone", on = focus.size == members.size) { focus = if (focus.size == members.size) emptySet() else members.toSet() }
                }
            }
            TextField(
                value = note, onValueChange = { note = it }, maxLines = 4,
                trailingIcon = { MicButton({ note = appendDictation(note, it) }) },
                placeholder = { Text("Your thoughts (optional), e.g. Theo walks in and isn't happy to see Bram so close to her", color = EmberColors.Dim) },
                modifier = Modifier.fillMaxWidth(), colors = fieldColors(), shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Button(
                onClick = { vm.suggestWhatNext(more = false, choice = choice()) }, enabled = !vm.whatNextBusy,
                modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmberColors.Red),
            ) { Text(if (vm.whatNextBusy && vm.whatNext.isEmpty()) "Thinking…" else "Show me options", fontWeight = FontWeight.SemiBold) }

            vm.whatNext.forEachIndexed { i, (title, what) ->
                val tag = if (vm.whatNext.size >= 3 && i % 3 == 2 && (dir != null || mood != null || note.isNotBlank() || focus.isNotEmpty())) "Something different · " else ""
                Column(
                    Modifier.fillMaxWidth().background(EmberColors.Surface, RoundedCornerShape(14.dp))
                        .border(1.dp, EmberColors.Line, RoundedCornerShape(14.dp))
                        .clickable {
                            onDismiss()
                            vm.takeWhatNext(title, what)
                        }.padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(tag + title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(what, fontSize = 13.5.sp, lineHeight = 19.sp, color = EmberColors.TextSoft)
                }
            }
            vm.whatNextError?.let { Text(it, color = EmberColors.RedSoft, fontSize = 13.sp) }
            if (vm.whatNext.isNotEmpty()) {
                OutlinedButton(
                    onClick = { vm.suggestWhatNext(more = true, choice = choice()) }, enabled = !vm.whatNextBusy,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                ) { Text(if (vm.whatNextBusy) "Thinking…" else "3 more", color = EmberColors.Text) }
            }
        }
    }
}
