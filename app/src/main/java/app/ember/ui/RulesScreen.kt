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
import androidx.compose.material.icons.outlined.Lock
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
    var editPreset by remember { mutableStateOf<Preset?>(null) }

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
                    Text("Built in · every reply", fontSize = 13.sp, color = EmberColors.Dim)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Sent first with every reply from every character. Tap one to change its wording.",
                    fontSize = 13.sp, color = EmberColors.Muted,
                )
            }
            itemsIndexed(Store.groundRules) { i, g ->
                Row(
                    Modifier.fillMaxWidth().background(EmberColors.Surface, RoundedCornerShape(14.dp))
                        .clickable { editGround = i }.padding(14.dp),
                ) {
                    Icon(
                        Icons.Outlined.Lock, null, tint = EmberColors.RedSoft,
                        modifier = Modifier.size(16.dp).padding(top = 2.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(g.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(2.dp))
                        Text(g.text, fontSize = 13.5.sp, color = EmberColors.Muted, lineHeight = 19.sp)
                    }
                }
            }
            item {
                TextButton(onClick = { confirmReset = true }) {
                    Text("Reset ground rules to the original wording", color = EmberColors.RedSoft)
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("Your rules", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("Every chat", fontSize = 13.sp, color = EmberColors.Dim)
                }
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

            item {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Presets", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { editPreset = Preset(name = "", rules = emptyList()) }) { Text("New preset", color = EmberColors.RedSoft) }
                }
                Text("Named sets of rules you switch on per chat from the chat bar.", fontSize = 13.sp, color = EmberColors.Muted)
            }
            items(Store.presets, key = { it.id }) { p ->
                Column(
                    Modifier.fillMaxWidth().background(EmberColors.Surface, RoundedCornerShape(18.dp))
                        .border(1.dp, EmberColors.Line, RoundedCornerShape(18.dp)).clickable { editPreset = p }.padding(16.dp),
                ) {
                    Text(p.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("${p.rules.size} rules", fontSize = 13.sp, color = EmberColors.Muted)
                    p.rules.take(3).forEach { Text("• $it", fontSize = 14.sp, color = EmberColors.TextSoft, maxLines = 1) }
                }
            }
        }
    }

    editPreset?.let { p ->
        val isNew = Store.presets.none { it.id == p.id }
        var name by remember(p.id) { mutableStateOf(p.name) }
        var body by remember(p.id) { mutableStateOf(p.rules.joinToString("\n")) }
        AlertDialog(
            onDismissRequest = { editPreset = null },
            title = { Text(if (isNew) "New preset" else "Edit preset") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    EmberField("Name", name, { name = it }, singleLine = true, placeholder = "e.g. Slow burn")
                    EmberField("Rules, one per line", body, { body = it }, minLines = 6,
                        placeholder = "Build tension slowly.\nNo kiss yet.")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val lines = body.lines().map { it.trim().removePrefix("-").removePrefix("•").trim() }.filter { it.isNotEmpty() }
                    if (name.isNotBlank()) {
                        val updated = p.copy(name = name.trim(), rules = lines)
                        Store.updatePresets(if (isNew) Store.presets + updated else Store.presets.map { if (it.id == p.id) updated else it })
                    }
                    editPreset = null
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    if (!isNew) TextButton(onClick = { Store.updatePresets(Store.presets.filter { it.id != p.id }); editPreset = null }) {
                        Text("Delete", color = EmberColors.RedSoft)
                    }
                    TextButton(onClick = { editPreset = null }) { Text("Cancel") }
                }
            },
            containerColor = EmberColors.Surface,
        )
    }

    editGround?.let { i ->
        val g = Store.groundRules.getOrNull(i)
        if (g != null) {
            var text by remember(i) { mutableStateOf(g.text) }
            AlertDialog(
                onDismissRequest = { editGround = null },
                title = { Text(g.title) },
                text = {
                    Column {
                        Text("Exact wording sent to the model. {{char}} and {{user}} become the names.",
                            fontSize = 13.sp, color = EmberColors.Muted)
                        Spacer(Modifier.height(10.dp))
                        androidx.compose.material3.TextField(
                            value = text, onValueChange = { text = it },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                            colors = fieldColors(), shape = RoundedCornerShape(12.dp),
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (text.isNotBlank()) {
                            Store.updateGroundRules(Store.groundRules.mapIndexed { j, x -> if (j == i) GroundRule(x.title, text.trim()) else x })
                        }
                        editGround = null
                    }) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { editGround = null }) { Text("Cancel") } },
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

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset ground rules?") },
            text = { Text("Any wording you changed in the ground rules goes back to the original. Your own rules are not touched.") },
            confirmButton = { TextButton(onClick = { Store.resetGroundRules(); confirmReset = false }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
            containerColor = EmberColors.Surface,
        )
    }
}
