package app.ember.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ember.data.Chat
import app.ember.data.Store
import kotlin.math.roundToInt

@Composable
fun ChatSettingsScreen(
    characterId: String,
    onBack: () -> Unit,
    openMemory: () -> Unit,
    openArchives: () -> Unit,
    openSearch: () -> Unit,
    openRules: () -> Unit,
    openSettings: () -> Unit,
) {
    val character = Store.character(characterId)
    val chat = Store.activeChat(characterId)
    if (character == null || chat == null) { LaunchedEffect(Unit) { onBack() }; return }
    fun update(f: (Chat) -> Chat) = Store.updateChat(chat.id) { f(it) }

    var pickPersona by remember { mutableStateOf(false) }
    var pickPreset by remember { mutableStateOf(false) }
    var editDirection by remember { mutableStateOf(false) }
    var pickSummaryEvery by remember { mutableStateOf(false) }
    var confirmNew by remember { mutableStateOf(false) }
    val s = Store.settings

    Scaffold(containerColor = EmberColors.Bg, topBar = { EmberTopBar("Chat settings", onBack) }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            SettingsGroup("Steering") {
                Column(Modifier.padding(vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Reply length (words)", fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Text("${chat.replyWords}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Slider(
                        value = chat.replyWords.toFloat(), valueRange = 50f..1000f,
                        onValueChange = { v -> update { it.copy(replyWords = ((v / 10).roundToInt() * 10)) } },
                        colors = SliderDefaults.colors(thumbColor = EmberColors.Text, activeTrackColor = EmberColors.Red),
                    )
                }
                HorizontalDivider(color = EmberColors.Line)
                SettingRow("Scene direction", chat.direction.ifBlank { "None" }) { editDirection = true }
                SettingRow("Rules preset", Store.preset(chat.presetId)?.name ?: "None") { pickPreset = true }
                SettingRow("Your persona", Store.personaFor(chat, character)?.name ?: "None", divider = false) { pickPersona = true }
            }

            SettingsGroup("Model") {
                val backupLabel = if (Store.backupReady) s.backupModel else "Not set up"
                ToggleRow(
                    "Use backup model for this chat", chat.useBackup,
                    sub = "Backup: $backupLabel",
                ) { on -> update { it.copy(useBackup = on) } }
                ToggleRow(
                    "Switch to backup on refusal", chat.autoSwitch,
                    sub = if (Store.backupReady) "Retries the turn with your backup model" else "Add a backup model in Settings first",
                ) { on -> update { it.copy(autoSwitch = on) } }
                Column(Modifier.padding(vertical = 14.dp)) {
                    val t = chat.temperature ?: s.temperature
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Temperature", fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Text("%.1f".format(t) + if (chat.temperature == null) " (default)" else "", fontSize = 15.sp, color = EmberColors.Muted)
                    }
                    Slider(
                        value = t.toFloat(), valueRange = 0f..2f, steps = 19,
                        onValueChange = { v -> update { it.copy(temperature = (v * 10).roundToInt() / 10.0) } },
                        colors = SliderDefaults.colors(thumbColor = EmberColors.Text, activeTrackColor = EmberColors.Red),
                    )
                    if (chat.temperature != null) {
                        TextButton(onClick = { update { it.copy(temperature = null) } }) { Text("Use default", color = EmberColors.RedSoft) }
                    }
                }
                HorizontalDivider(color = EmberColors.Line)
                SettingRow("Cost of this chat", costLabel(chat), sub = "${chat.tokensIn + chat.tokensOut} tokens used", divider = false)
            }

            SettingsGroup("History & memory") {
                SettingRow("Memories", "${chat.pins.size} pinned · ${chat.summaries.size} summaries", onClick = openMemory)
                SettingRow("Summarise every", if (s.summariseEvery > 0) "${s.summariseEvery} messages" else "Off") { pickSummaryEvery = true }
                SettingRow("Chat archives", onClick = openArchives)
                SettingRow("Search chat history", onClick = openSearch)
                SettingRow("Start new chat with ${character.name}", divider = false) { confirmNew = true }
            }

            SettingsGroup("Appearance") {
                ToggleRow(
                    "Colour from photo", chat.tintFromPhoto,
                    sub = if (character.tint == null) "Add a photo to the character to use this" else "Tints the bubbles to match ${character.name}",
                ) { on -> update { it.copy(tintFromPhoto = on) } }
                Column(Modifier.padding(vertical = 14.dp)) {
                    Row { Text("Bubble opacity", fontSize = 16.sp, modifier = Modifier.weight(1f)); Text("${(chat.bubbleOpacity * 100).roundToInt()}%", color = EmberColors.Muted) }
                    Slider(
                        value = chat.bubbleOpacity, valueRange = 0.3f..1f,
                        onValueChange = { v -> update { it.copy(bubbleOpacity = v) } },
                        colors = SliderDefaults.colors(thumbColor = EmberColors.Text, activeTrackColor = EmberColors.Red),
                    )
                }
                HorizontalDivider(color = EmberColors.Line)
                Column(Modifier.padding(vertical = 14.dp)) {
                    Row { Text("Text size", fontSize = 16.sp, modifier = Modifier.weight(1f)); Text("${chat.textSize}", color = EmberColors.Muted) }
                    Slider(
                        value = chat.textSize.toFloat(), valueRange = 13f..22f, steps = 8,
                        onValueChange = { v -> update { it.copy(textSize = v.roundToInt()) } },
                        colors = SliderDefaults.colors(thumbColor = EmberColors.Text, activeTrackColor = EmberColors.Red),
                    )
                }
            }

            Row {
                TextButton(onClick = openRules) { Text("Rules", color = EmberColors.RedSoft) }
                TextButton(onClick = openSettings) { Text("App settings", color = EmberColors.RedSoft) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (pickPersona) {
        PickerDialog(
            title = "Your persona in this chat", items = Store.personas, label = { it.name },
            sub = { it.description.take(80) },
            isSelected = { it.id == Store.personaFor(chat, character)?.id },
            onPick = { p -> if (p != null) update { it.copy(personaId = p.id) }; pickPersona = false },
            onDismiss = { pickPersona = false },
        )
    }
    if (pickPreset) {
        PickerDialog(
            title = "Rules preset", items = Store.presets, label = { it.name }, sub = { "${it.rules.size} rules" },
            isSelected = { it.id == chat.presetId }, noneLabel = "No preset", noneSelected = chat.presetId == null,
            onPick = { p -> update { it.copy(presetId = p?.id) }; pickPreset = false },
            onDismiss = { pickPreset = false },
        )
    }
    if (editDirection) {
        TextDialog(
            title = "Scene direction", initial = chat.direction,
            hint = "Where the story should go. Stays active until you clear it.",
            onSave = { t -> update { it.copy(direction = t.trim()) }; editDirection = false },
            onDismiss = { editDirection = false },
        )
    }
    if (pickSummaryEvery) {
        val options = listOf(0, 20, 30, 40, 60)
        PickerDialog(
            title = "Summarise every", items = options,
            label = { if (it == 0) "Off" else "$it messages" },
            isSelected = { it == s.summariseEvery },
            onPick = { v -> if (v != null) Store.updateSettings(s.copy(summariseEvery = v)); pickSummaryEvery = false },
            onDismiss = { pickSummaryEvery = false },
        )
    }
    if (confirmNew) {
        AlertDialog(
            onDismissRequest = { confirmNew = false },
            title = { Text("Start a new chat?") },
            text = { Text("This starts again from the opening message. The current chat stays in Chat archives.") },
            confirmButton = { TextButton(onClick = { Store.newChat(character); confirmNew = false; onBack() }) { Text("Start new chat") } },
            dismissButton = { TextButton(onClick = { confirmNew = false }) { Text("Cancel") } },
            containerColor = EmberColors.Surface,
        )
    }
}

fun costLabel(chat: Chat): String {
    val s = Store.settings
    if (s.priceIn <= 0 && s.priceOut <= 0) return "Set prices in Settings"
    val euros = chat.tokensIn / 1_000_000.0 * s.priceIn + chat.tokensOut / 1_000_000.0 * s.priceOut
    return "€" + "%.3f".format(euros)
}
