package app.ember.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ember.ai.LlmClient
import app.ember.data.Settings
import app.ember.data.Store
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val s = Store.settings
    var userName by rememberSaveable { mutableStateOf(s.userName) }
    var userDesc by rememberSaveable { mutableStateOf(s.userDescription) }
    var baseUrl by rememberSaveable { mutableStateOf(s.baseUrl) }
    var model by rememberSaveable { mutableStateOf(s.model) }
    var temperature by rememberSaveable { mutableStateOf(s.temperature.toFloat()) }
    var newKey by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf<String?>(null) }
    var testing by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun commit() {
        Store.updateSettings(
            Settings(
                baseUrl = baseUrl.trim().ifBlank { Settings().baseUrl },
                model = model.trim().ifBlank { Settings().model },
                temperature = (temperature * 10).roundToInt() / 10.0,
                userName = userName.trim(),
                userDescription = userDesc.trim(),
            )
        )
        if (newKey.isNotBlank()) { Store.setApiKey(newKey); newKey = "" }
    }

    BackHandler { commit(); onBack() }

    Scaffold(
        containerColor = EmberColors.Bg,
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { commit(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EmberColors.Bg),
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SectionLabel("Your character")
            EmberField("Name", userName, { userName = it }, placeholder = "e.g. Evelyn", singleLine = true)
            EmberField(
                "Description", userDesc, { userDesc = it }, minLines = 3,
                placeholder = "Who you play: look, personality, anything the character should know about you.",
            )
            Text(
                "Used in every chat for now. Separate personas you can switch per chat come in the next update.",
                color = EmberColors.Dim, fontSize = 13.sp,
            )

            Spacer(Modifier.height(4.dp))
            SectionLabel("Model")
            EmberField(
                "API key", newKey, { newKey = it; status = null }, singleLine = true,
                placeholder = if (Store.hasApiKey) "Saved. Type a new one to replace it." else "Paste your DeepSeek key (sk-…)",
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingInfo = if (Store.hasApiKey) "Saved, encrypted" else "Not set",
            )
            EmberField(
                "Provider address", baseUrl, { baseUrl = it; status = null }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, capitalization = KeyboardCapitalization.None),
            )
            EmberField(
                "Model name", model, { model = it; status = null }, singleLine = true,
                placeholder = "deepseek-chat",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
            )

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Temperature", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("%.1f".format(temperature), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Slider(
                    value = temperature, onValueChange = { temperature = it }, valueRange = 0f..2f, steps = 19,
                    colors = SliderDefaults.colors(thumbColor = EmberColors.Text, activeTrackColor = EmberColors.Red),
                )
                Text("Higher is more creative and surprising, lower is steadier.", color = EmberColors.Dim, fontSize = 13.sp)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        commit()
                        testing = true; status = null
                        scope.launch {
                            val key = Store.apiKey()
                            status = if (key.isBlank()) "Add your API key first."
                            else LlmClient.test(Store.settings.baseUrl, key, Store.settings.model) ?: "Connected. You're ready to chat."
                            testing = false
                        }
                    },
                    enabled = !testing,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(25.dp),
                ) { Text(if (testing) "Testing…" else "Test connection", color = EmberColors.Text) }
                Button(
                    onClick = { commit(); status = "Saved." },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmberColors.Red),
                ) { Text("Save") }
            }
            status?.let { Text(it, color = EmberColors.TextSoft, fontSize = 14.sp) }

            if (Store.hasApiKey) {
                androidx.compose.material3.TextButton(onClick = { Store.setApiKey(""); status = "API key removed." }) {
                    Text("Remove saved API key", color = EmberColors.RedSoft)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
