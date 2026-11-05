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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ember.data.GroundRule
import app.ember.data.Preset
import app.ember.data.Rule
import app.ember.data.Store

@Composable
fun RulesScreen(onBack: () -> Unit) {
    var editGround by remember { mutableStateOf<Int?>(null) }
    var editRule by remember { mutableStateOf<Rule?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var editWfm by remember { mutableStateOf(false) }
    var confirmResetWfm by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = EmberColors.Bg,
        topBar = {
            TopAppBar(
                title = { Text("Rules", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EmberColors.Bg),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editRule = Rule(label = "", text = "") },
                containerColor = EmberColors.Red, contentColor = Color.White,
                icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Add rule") },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("Ground rules", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("Always on · every chat", fontSize = 13.sp, color = EmberColors.Dim)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Sent first with every reply from every character. Tap any rule to reword it, rename it or delete it.",
                    fontSize = 13.sp, color = EmberColors.Muted,
                )
            }
            itemsIndexed(Store.groundRules) { i, g ->
                Row(
                    Modifier.fillMaxWidth().background(EmberColors.Surface, RoundedCornerShape(14.dp))
                        .clickable { editWfm = false; editGround = i }.padding(14.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(g.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(2.dp))
                        Text(g.text, fontSize = 13.5.sp, color = EmberColors.Muted, lineHeight = 19.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Outlined.Edit, "Edit", tint = EmberColors.RedSoft, modifier = Modifier.size(18.dp))
                }
            }
            item {
                Row {
                    TextButton(onClick = { editWfm = false; editGround = -1 }) { Text("+ Add ground rule", color = EmberColors.RedSoft) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { confirmReset = true }) { Text("Reset to original", color = EmberColors.Muted) }
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("Write for me rules", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("When it writes as you", fontSize = 13.sp, color = EmberColors.Dim)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Used only for Write for me drafts, on top of the ground rules. {{persona}} is your character and {{character}} is the one you're chatting with. Tap to edit.",
                    fontSize = 13.sp, color = EmberColors.Muted,
                )
            }
            itemsIndexed(Store.wfmRules) { i, g ->
                Row(
                    Modifier.fillMaxWidth().background(EmberColors.Surface, RoundedCornerShape(14.dp))
                        .clickable { editWfm = true; editGround = i }.padding(14.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(g.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(2.dp))
                        Text(g.text, fontSize = 13.5.sp, color = EmberColors.Muted, lineHeight = 19.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Outlined.Edit, "Edit", tint = EmberColors.RedSoft, modifier = Modifier.size(18.dp))
                }
            }
            item {
                Row {
                    TextButton(onClick = { editWfm = true; editGround = -1 }) { Text("+ Add rule", color = EmberColors.RedSoft) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { confirmResetWfm = true }) { Text("Reset to original", color = EmberColors.Muted) }
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("Your rules", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("Every chat", fontSize = 13.sp, color = EmberColors.Dim)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Every rule that's switched on applies to all your chats. Tap a rule to edit or delete it.",
                    fontSize = 13.sp, color = EmberColors.Muted,
                )
            }
            if (Store.rules.isEmpty()) {
                item {
                    Text(
                        "No rules of your own yet. Add one with the button below, for example \"Use British spelling.\"",
                        color = EmberColors.Muted, fontSize = 14.sp,
                    )
                }
            }
            items(Store.rules, key = { it.id }) { r ->
                Column(
                    Modifier.fillMaxWidth()
                        .background(EmberColors.Surface, RoundedCornerShape(18.dp))
                        .border(1.dp, EmberColors.Line, RoundedCornerShape(18.dp))
                        .clickable { editRule = r }
                        .padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (r.label.isNotBlank()) {
                            Box(
                                Modifier.background(EmberColors.Field, RoundedCornerShape(14.dp))
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) { Text(r.label, fontSize = 13.sp, color = EmberColors.TextSoft) }
                        }
                        Spacer(Modifier.weight(1f))
                        Switch(
                            checked = r.enabled,
                            onCheckedChange = { on -> Store.updateRules(Store.rules.map { if (it.id == r.id) it.copy(enabled = on) else it }) },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = EmberColors.Red, checkedThumbColor = Color.White,
                                uncheckedTrackColor = EmberColors.Outline, uncheckedThumbColor = EmberColors.Muted,
                            ),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        r.text, fontSize = 15.5.sp, lineHeight = 22.sp,
                        color = if (r.enabled) EmberColors.Text else EmberColors.Dim,
                    )
                }
            }

        }
    }

    editGround?.let { i ->
        val source = if (editWfm) Store.wfmRules else Store.groundRules
        val saveList: (List<GroundRule>) -> Unit = { if (editWfm) Store.updateWfmRules(it) else Store.updateGroundRules(it) }
        val g = if (i < 0) GroundRule("", "") else source.getOrNull(i)
        if (g != null) {
            var title by remember(i) { mutableStateOf(g.title) }
            var text by remember(i) { mutableStateOf(g.text) }
            AlertDialog(
                onDismissRequest = { editGround = null },
                title = { Text((if (i < 0) "New " else "Edit ") + if (editWfm) "Write for me rule" else "ground rule") },
                text = {
                    Column(
                        Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        EmberField("Title", title, { title = it }, singleLine = true, placeholder = "e.g. Novel style")
                        EmberField(
                            "Rule", text, { text = it }, minLines = 5, maxLines = 12,
                            placeholder = "The exact wording sent to the model. {{char}} and {{user}} become the names.",
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (text.isNotBlank()) {
                            val updated = GroundRule(title.trim().ifBlank { text.trim().take(30) }, text.trim())
                            saveList(
                                if (i < 0) source + updated
                                else source.mapIndexed { j, x -> if (j == i) updated else x }
                            )
                        }
                        editGround = null
                    }) { Text("Save") }
                },
                dismissButton = {
                    Row {
                        if (i >= 0) TextButton(onClick = {
                            saveList(source.filterIndexed { j, _ -> j != i })
                            editGround = null
                        }) { Text("Delete", color = EmberColors.RedSoft) }
                        TextButton(onClick = { editGround = null }) { Text("Cancel") }
                    }
                },
                containerColor = EmberColors.Surface,
            )
        }
    }

    editRule?.let { r ->
        val isNew = Store.rules.none { it.id == r.id }
        var label by remember(r.id) { mutableStateOf(r.label) }
        var text by remember(r.id) { mutableStateOf(r.text) }
        AlertDialog(
            onDismissRequest = { editRule = null },
            title = { Text(if (isNew) "New rule" else "Edit rule") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    EmberField("Label", label, { label = it }, placeholder = "e.g. Spelling", singleLine = true)
                    EmberField("Rule", text, { text = it }, placeholder = "e.g. Use British spelling.", minLines = 3)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (text.isNotBlank()) {
                        val updated = r.copy(label = label.trim(), text = text.trim())
                        Store.updateRules(if (isNew) Store.rules + updated else Store.rules.map { if (it.id == r.id) updated else it })
                    }
                    editRule = null
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    if (!isNew) {
                        TextButton(onClick = { Store.updateRules(Store.rules.filter { it.id != r.id }); editRule = null }) {
                            Text("Delete", color = EmberColors.RedSoft)
                        }
                    }
                    TextButton(onClick = { editRule = null }) { Text("Cancel") }
                }
            },
            containerColor = EmberColors.Surface,
        )
    }

    if (confirmResetWfm) {
        AlertDialog(
            onDismissRequest = { confirmResetWfm = false },
            title = { Text("Reset Write for me rules?") },
            text = { Text("They go back to the original four, with their original wording.") },
            confirmButton = { TextButton(onClick = { Store.resetWfmRules(); confirmResetWfm = false }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { confirmResetWfm = false }) { Text("Cancel") } },
            containerColor = EmberColors.Surface,
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset ground rules?") },
            text = { Text("The ground rules go back to the original 20, with their original wording. Ground rules you added or deleted are lost or restored. Your own rules are not touched.") },
            confirmButton = { TextButton(onClick = { Store.resetGroundRules(); confirmReset = false }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
            containerColor = EmberColors.Surface,
        )
    }
}
