package app.ember.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DRAFT_LENGTHS = listOf(50, 100, 200)

@Composable
fun WriteForMeSheet(vm: ChatViewModel, personaName: String, onSent: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var customLength by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val draft = vm.wfmDrafts.getOrNull(vm.wfmSel)

    ModalBottomSheet(
        onDismissRequest = { vm.wfmOpen = false },
        sheetState = sheet,
        containerColor = EmberColors.Bg,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(bottom = 16.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Draw, null, tint = EmberColors.RedSoft, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Write for me", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    "as $personaName", fontSize = 13.sp, color = EmberColors.TextSoft,
                    modifier = Modifier.background(EmberColors.Field, RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }

            Text("Your notes: what you do and say", fontSize = 13.sp, color = EmberColors.Muted)
            TextField(
                value = vm.wfmNotes, onValueChange = { vm.wfmNotes = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp, max = 220.dp),
                placeholder = { Text("e.g. she laughs, grabs his sleeve, says she wasn't admiring anything. Leave empty and it decides.", color = EmberColors.Dim) },
                colors = fieldColors(), shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Length", fontSize = 13.sp, color = EmberColors.Muted)
                DraftChip("Auto", vm.wfmWords == 0) { vm.wfmWords = 0 }
                DRAFT_LENGTHS.forEach { w -> DraftChip("$w", vm.wfmWords == w) { vm.wfmWords = w } }
                val custom = vm.wfmWords > 0 && vm.wfmWords !in DRAFT_LENGTHS
                DraftChip(if (custom) "${vm.wfmWords}" else "Custom", custom) { customLength = true }
            }

            if (draft != null) {
                Column(
                    Modifier.fillMaxWidth().background(EmberColors.Surface, RoundedCornerShape(16.dp))
                        .border(1.5.dp, EmberColors.Red, RoundedCornerShape(16.dp)).padding(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Draft ${vm.wfmSel + 1} of ${vm.wfmDrafts.size} · ${wordCount(draft)} words",
                            fontSize = 12.5.sp, color = EmberColors.Muted, modifier = Modifier.weight(1f),
                        )
                        if (vm.wfmDrafts.size > 1) {
                            IconButton(onClick = { vm.wfmSel -= 1 }, enabled = vm.wfmSel > 0 && !vm.wfmBusy, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.ChevronLeft, "Previous draft")
                            }
                            IconButton(onClick = { vm.wfmSel += 1 }, enabled = vm.wfmSel < vm.wfmDrafts.size - 1 && !vm.wfmBusy, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.ChevronRight, "Next draft")
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    if (draft.isEmpty() && vm.wfmBusy) Text("Writing…", color = EmberColors.Muted)
                    else Text(formatStory(draft), fontSize = 15.sp, lineHeight = 22.sp, color = EmberColors.Text)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = vm.wfmChange, onValueChange = { vm.wfmChange = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Change something: shorter, flirtier, add…", color = EmberColors.Dim) },
                        colors = fieldColors(), shape = RoundedCornerShape(24.dp), singleLine = true,
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { vm.draft(rewrite = vm.wfmChange.isNotBlank()) },
                        enabled = !vm.wfmBusy,
                        modifier = Modifier.size(48.dp).border(1.dp, EmberColors.Outline, RoundedCornerShape(24.dp)),
                    ) { Icon(Icons.Filled.Refresh, if (vm.wfmChange.isNotBlank()) "Rewrite with change" else "Write another draft") }
                }
            }

            vm.wfmError?.let { Text(it, color = EmberColors.RedSoft, fontSize = 13.sp) }

            if (draft == null) {
                Button(
                    onClick = { vm.draft(rewrite = false) },
                    enabled = !vm.wfmBusy && !vm.busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmberColors.Red),
                ) { Text(if (vm.wfmBusy) "Writing…" else "Write it", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { editing = true }, enabled = !vm.wfmBusy,
                        modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(26.dp),
                    ) { Text("Edit", color = EmberColors.Text) }
                    Button(
                        onClick = { vm.sendDraft(); onSent() },
                        enabled = !vm.wfmBusy && !vm.busy && draft.isNotBlank(),
                        modifier = Modifier.weight(1.4f).height(52.dp), shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmberColors.Red),
                    ) { Text("Send as $personaName", fontWeight = FontWeight.SemiBold, maxLines = 1) }
                }
                TextButton(onClick = { vm.resetWriteForMe() }, enabled = !vm.wfmBusy) {
                    Text("Start over", color = EmberColors.Muted)
                }
            }
        }
    }

    if (editing && draft != null) {
        TextDialog(title = "Edit draft", initial = draft, onSave = { vm.editDraft(it); editing = false }, onDismiss = { editing = false })
    }

    if (customLength) {
        var value by remember { mutableStateOf(if (vm.wfmWords > 0) vm.wfmWords.toString() else "") }
        AlertDialog(
            onDismissRequest = { customLength = false },
            title = { Text("Draft length") },
            text = {
                TextField(
                    value = value, onValueChange = { value = it.filter(Char::isDigit).take(4) },
                    singleLine = true, colors = fieldColors(), shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(onClick = { value.toIntOrNull()?.let { vm.wfmWords = it.coerceIn(20, 2000) }; customLength = false }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { customLength = false }) { Text("Cancel") } },
            containerColor = EmberColors.Surface,
        )
    }
}

@Composable
private fun DraftChip(label: String, on: Boolean, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        Modifier.height(32.dp)
            .background(if (on) EmberColors.Text else EmberColors.Field, RoundedCornerShape(16.dp))
            .let { m -> m.then(Modifier.padding(0.dp)) }
            .padding(horizontal = 0.dp),
    ) {
        TextButton(onClick = onClick, modifier = Modifier.height(32.dp)) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (on) EmberColors.Bg else EmberColors.TextSoft)
        }
    }
}
