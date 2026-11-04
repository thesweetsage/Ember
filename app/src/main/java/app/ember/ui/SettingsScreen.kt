package app.ember.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ember.ai.Endpoint
import app.ember.ai.LlmClient
import app.ember.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(onBack: () -> Unit, openPersonas: () -> Unit) {
    val context = LocalContext.current
    val s = Store.settings
    var baseUrl by rememberSaveable { mutableStateOf(s.baseUrl) }
    var model by rememberSaveable { mutableStateOf(s.model) }
    var newKey by rememberSaveable { mutableStateOf("") }
    var backupUrl by rememberSaveable { mutableStateOf(s.backupBaseUrl) }
    var backupModel by rememberSaveable { mutableStateOf(s.backupModel) }
    var newBackupKey by rememberSaveable { mutableStateOf("") }
    var voiceUrl by rememberSaveable { mutableStateOf(s.voiceBaseUrl) }
    var voiceModel by rememberSaveable { mutableStateOf(s.voiceModel) }
    var voiceLang by rememberSaveable { mutableStateOf(s.voiceLanguage) }
    var newVoiceKey by rememberSaveable { mutableStateOf("") }
    var temperature by rememberSaveable { mutableStateOf(s.temperature.toFloat()) }
    var autoSwitch by rememberSaveable { mutableStateOf(s.autoSwitchDefault) }
    var wfmWords by rememberSaveable { mutableStateOf(s.writeForMeWords.toString()) }
    var priceIn by rememberSaveable { mutableStateOf(if (s.priceIn > 0) s.priceIn.toString() else "") }
    var priceOut by rememberSaveable { mutableStateOf(if (s.priceOut > 0) s.priceOut.toString() else "") }
    var status by rememberSaveable { mutableStateOf<String?>(null) }
    var backupStatus by rememberSaveable { mutableStateOf<String?>(null) }
    var testing by rememberSaveable { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    var dataStatus by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun commit() {
        Store.updateSettings(
            Store.settings.copy(
                baseUrl = baseUrl.trim().ifBlank { "https://api.deepseek.com" },
                model = model.trim().ifBlank { "deepseek-chat" },
                backupBaseUrl = backupUrl.trim(),
                backupModel = backupModel.trim(),
                voiceBaseUrl = voiceUrl.trim().ifBlank { "https://api.groq.com/openai/v1" },
                voiceModel = voiceModel.trim().ifBlank { "whisper-large-v3-turbo" },
                voiceLanguage = voiceLang.trim().lowercase(),
                temperature = (temperature * 10).roundToInt() / 10.0,
                autoSwitchDefault = autoSwitch,
                writeForMeWords = wfmWords.toIntOrNull()?.let { if (it <= 0) 0 else it.coerceIn(20, 2000) } ?: 0,
                priceIn = priceIn.replace(',', '.').toDoubleOrNull() ?: 0.0,
                priceOut = priceOut.replace(',', '.').toDoubleOrNull() ?: 0.0,
            )
        )
        if (newKey.isNotBlank()) { Store.setApiKey(newKey); newKey = "" }
        if (newBackupKey.isNotBlank()) { Store.setBackupKey(newBackupKey); newBackupKey = "" }
        if (newVoiceKey.isNotBlank()) { Store.setVoiceKey(newVoiceKey); newVoiceKey = "" }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            dataStatus = "Saving backup…"
            dataStatus = try {
                withContext(Dispatchers.IO) { Store.exportTo(context, uri) }
                "Backup saved. API keys are not included."
            } catch (e: Exception) { "Backup failed: ${e.message}" }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmRestore = uri
    }

    BackHandler { commit(); onBack() }

    Scaffold(containerColor = EmberColors.Bg, topBar = { EmberTopBar("Settings", { commit(); onBack() }) }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SettingsGroup("You") {
                SettingRow(
                    "Your personas", Store.persona(s.defaultPersonaId)?.name?.let { "Default: $it" } ?: "None yet",
                    divider = false, onClick = openPersonas,
                )
            }

            SectionLabel("Main model")
            EmberField(
                "API key", newKey, { newKey = it; status = null }, singleLine = true,
                placeholder = if (Store.hasApiKey) "Saved. Type a new one to replace it." else "Paste your DeepSeek key (sk-…)",
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingInfo = if (Store.hasApiKey) "Saved, encrypted" else "Not set",
            )
            EmberField("Provider address", baseUrl, { baseUrl = it; status = null }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, capitalization = KeyboardCapitalization.None))
            EmberField("Model name", model, { model = it; status = null }, singleLine = true, placeholder = "deepseek-chat",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = {
                        commit(); testing = true; status = null
                        scope.launch {
                            val key = Store.apiKey()
                            status = if (key.isBlank()) "Add your API key first."
                            else LlmClient.test(Endpoint(Store.settings.baseUrl, key, Store.settings.model)) ?: "Connected. You're ready to chat."
                            testing = false
                        }
                    },
                    enabled = !testing, shape = RoundedCornerShape(24.dp),
                ) { Text(if (testing) "Testing…" else "Test connection", color = EmberColors.Text) }
                if (Store.hasApiKey) TextButton(onClick = { Store.setApiKey(""); status = "API key removed." }) { Text("Remove key", color = EmberColors.RedSoft) }
            }
            status?.let { Text(it, color = EmberColors.TextSoft, fontSize = 14.sp) }

            SectionLabel("Backup model (optional)")
            Text(
                "Any provider that uses the standard chat API, e.g. OpenRouter (https://openrouter.ai/api/v1). " +
                    "It takes over when the main model refuses, or for chats you switch to it.",
                fontSize = 13.sp, color = EmberColors.Muted,
            )
            EmberField(
                "API key", newBackupKey, { newBackupKey = it; backupStatus = null }, singleLine = true,
                placeholder = if (Store.hasBackupKey) "Saved. Type a new one to replace it." else "Paste the backup provider's key",
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingInfo = if (Store.hasBackupKey) "Saved, encrypted" else "Not set",
            )
            EmberField("Provider address", backupUrl, { backupUrl = it; backupStatus = null }, singleLine = true,
                placeholder = "https://…/v1",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, capitalization = KeyboardCapitalization.None))
            EmberField("Model name", backupModel, { backupModel = it; backupStatus = null }, singleLine = true,
                placeholder = "The model's exact name at that provider",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = {
                        commit(); testing = true; backupStatus = null
                        scope.launch {
                            backupStatus = if (!Store.backupReady) "Fill in the key, address and model name first."
                            else LlmClient.test(Endpoint(Store.settings.backupBaseUrl, Store.backupKey(), Store.settings.backupModel)) ?: "Backup connected."
                            testing = false
                        }
                    },
                    enabled = !testing, shape = RoundedCornerShape(24.dp),
                ) { Text("Test backup", color = EmberColors.Text) }
                if (Store.hasBackupKey) TextButton(onClick = { Store.setBackupKey(""); backupStatus = "Backup key removed." }) { Text("Remove key", color = EmberColors.RedSoft) }
            }
            backupStatus?.let { Text(it, color = EmberColors.TextSoft, fontSize = 14.sp) }
            SectionLabel("Voice transcription (optional)")
            Text(
                "For accurate dictation with the mic buttons. Works with Groq (Whisper, set up below by default) or any provider " +
                    "with the same audio API, such as OpenAI. Without a key, the phone's own voice typing is used.",
                fontSize = 13.sp, color = EmberColors.Muted,
            )
            EmberField(
                "API key", newVoiceKey, { newVoiceKey = it }, singleLine = true,
                placeholder = if (Store.hasVoiceKey) "Saved. Type a new one to replace it." else "Paste your Groq key (gsk_…)",
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingInfo = if (Store.hasVoiceKey) "Saved, encrypted" else "Not set",
            )
            EmberField("Provider address", voiceUrl, { voiceUrl = it }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, capitalization = KeyboardCapitalization.None))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EmberField("Model name", voiceModel, { voiceModel = it }, singleLine = true, modifier = Modifier.weight(2f),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None))
                EmberField("Language", voiceLang, { voiceLang = it }, singleLine = true, modifier = Modifier.weight(1f),
                    placeholder = "en",
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None))
            }
            Text("Language is a two-letter code (en, pt…). Leave it empty to detect automatically.", fontSize = 12.5.sp, color = EmberColors.Muted)
            if (Store.hasVoiceKey) {
                TextButton(onClick = { Store.setVoiceKey("") }) { Text("Remove voice key", color = EmberColors.RedSoft) }
            }

            SettingsGroup("Defaults for new chats") {
                ToggleRow("Switch to backup on refusal", autoSwitch, divider = true) { autoSwitch = it }
                Column(Modifier.padding(vertical = 12.dp)) {
                    Row {
                        Text("Temperature", fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Text("%.1f".format(temperature), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Slider(
                        value = temperature, onValueChange = { temperature = it }, valueRange = 0f..2f, steps = 19,
                        colors = SliderDefaults.colors(thumbColor = EmberColors.Text, activeTrackColor = EmberColors.Red),
                    )
                    Text("Higher is more creative, lower is steadier.", fontSize = 12.5.sp, color = EmberColors.Muted)
                }
            }
            EmberField("\"Write for me\" default length (words, 0 = Auto)", wfmWords, { wfmWords = it.filter(Char::isDigit).take(4) },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))

            SectionLabel("Cost counter (optional)")
            Text(
                "Enter your provider's prices per million tokens and each chat shows what it has cost so far. Leave empty to only count tokens.",
                fontSize = 13.sp, color = EmberColors.Muted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EmberField("€ per 1M input", priceIn, { priceIn = it }, singleLine = true, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                EmberField("€ per 1M output", priceOut, { priceOut = it }, singleLine = true, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }

            SectionLabel("Backup & restore")
            Text(
                "Saves all characters, chats, photos, personas, rules and settings to one zip file. API keys are never included.",
                fontSize = 13.sp, color = EmberColors.Muted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = {
                    commit()
                    val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    exportLauncher.launch("ember-backup-$stamp.zip")
                }, shape = RoundedCornerShape(24.dp)) { Text("Save backup", color = EmberColors.Text) }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                    shape = RoundedCornerShape(24.dp)) { Text("Restore", color = EmberColors.Text) }
            }
            dataStatus?.let { Text(it, color = EmberColors.TextSoft, fontSize = 14.sp) }

            Button(
                onClick = { commit(); status = "Saved." },
                modifier = Modifier.height(50.dp),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmberColors.Red),
            ) { Text("Save settings") }
            Spacer(Modifier.height(24.dp))
        }
    }

    confirmRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text("Restore this backup?") },
            text = { Text("Everything currently in the app is replaced by the backup. Your API keys stay as they are.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = null
                    scope.launch {
                        dataStatus = "Restoring…"
                        dataStatus = try {
                            withContext(Dispatchers.IO) { Store.importFrom(context, uri) }
                            Store.reload()
                            "Backup restored."
                        } catch (e: Exception) { "Restore failed: ${e.message}" }
                    }
                }) { Text("Restore", color = EmberColors.RedSoft) }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text("Cancel") } },
            containerColor = EmberColors.Surface,
        )
    }
}
