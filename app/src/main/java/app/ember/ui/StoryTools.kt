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

/** One "Move it along" option: what you see, and what the model is told. */
private data class Nudge(val title: String, val sub: String, val instruction: (String) -> String, val chapter: Boolean)

private val NUDGES = listOf(
    Nudge("Skip to the next scene", "Wrap this moment up and jump to what comes next", { n ->
        "Bring this scene to a natural close in a line or two, then jump ahead to the next meaningful scene between $n and the user's character. Make the jump clear in the first line."
    }, true),
    Nudge("Time skip", "Hours or days later", { n ->
        "Skip ahead in time: hours, days or longer, whatever suits the story. Show in a sentence or two what happened in between, then land in a new moment with $n."
    }, true),
    Nudge("Somewhere new", "Move the story to a different place", { n ->
        "Move the story to a new location that fits where things are heading. Set the place vividly through what $n notices, then carry on from there."
    }, true),
    Nudge("Something unexpected", "An arrival, a call, news, a discovery", { _ ->
        "Something unexpected happens that changes the moment: someone arrives, a phone rings, news lands, something is discovered, or something goes wrong. It must fit this world and raise the stakes."
    }, false),
    Nudge("A new side of him", "A secret, a memory, something he's never shown", { n ->
        "$n reveals something new about himself, prompted naturally by the moment: a secret, a memory, a want or a side of him not shown before."
    }, false),
    Nudge("A new side of them", "One of them reveals a secret, a memory, something never shown", { n ->
        "One of $n reveals something new about themselves, prompted naturally by the moment: a secret, a memory, a want or a side not shown before. Show how the others react."
    }, false),
    Nudge("Turn up the heat", "More tension, more desire", { n ->
        "Raise the tension and desire between $n and the user's character, in a way true to who $n is and to where the scene already is."
    }, false),
)

/** "Move it along": quick ways to push the story forward. */
@Composable
fun MoveItAlongSheet(characterName: String, onPick: (instruction: String, chapter: Boolean) -> Unit, onDismiss: () -> Unit, group: Boolean = false) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var own by remember { mutableStateOf("") }
    var ownChapter by remember { mutableStateOf(false) }
    val name = characterName.ifBlank { "the character" }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = EmberColors.Bg) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(bottom = 16.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Move it along", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(if (group) "The next reply goes in this direction." else "$name writes the next reply in this direction.", fontSize = 13.sp, color = EmberColors.Muted)
            Spacer(Modifier.height(4.dp))
            NUDGES.filter { if (group) it.title != "A new side of him" else it.title != "A new side of them" }.forEach { n ->
                Column(
                    Modifier.fillMaxWidth().background(EmberColors.Surface, RoundedCornerShape(14.dp))
                        .border(1.dp, EmberColors.Line, RoundedCornerShape(14.dp))
                        .clickable { onPick(n.instruction(name), n.chapter) }.padding(horizontal = 14.dp, vertical = 11.dp),
                ) {
                    Text(n.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(n.sub + if (n.chapter) " · new chapter" else "", fontSize = 12.5.sp, color = EmberColors.Muted)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("Or your own", fontSize = 13.sp, color = EmberColors.Muted)
            TextField(
                value = own, onValueChange = { own = it },
                trailingIcon = { MicButton({ own = appendDictation(own, it) }) },
                placeholder = { Text("e.g. his ex shows up at the party", color = EmberColors.Dim) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp, max = 160.dp),
                colors = fieldColors(), shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = ownChapter, onCheckedChange = { ownChapter = it },
                    colors = CheckboxDefaults.colors(checkedColor = EmberColors.Red),
                )
                Text("Start a new chapter (for the book export)", fontSize = 13.sp, color = EmberColors.TextSoft)
            }
            Button(
                onClick = { onPick(own.trim(), ownChapter) }, enabled = own.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmberColors.Red),
            ) { Text("Go", fontWeight = FontWeight.SemiBold) }
        }
    }
}

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
