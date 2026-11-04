package app.ember.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ember.data.Character
import app.ember.data.Store
import coil.compose.AsyncImage
import java.io.File

@Composable
fun EditCharacterScreen(characterId: String, onBack: () -> Unit, onCreated: (String) -> Unit) {
    val context = LocalContext.current
    val existing = remember(characterId) { Store.character(characterId) }
    val isNew = existing == null
    val base = existing ?: Character()

    var name by rememberSaveable { mutableStateOf(base.name) }
    var description by rememberSaveable { mutableStateOf(base.description) }
    var personality by rememberSaveable { mutableStateOf(base.personality) }
    var background by rememberSaveable { mutableStateOf(base.background) }
    var opening by rememberSaveable { mutableStateOf(base.opening) }
    var example by rememberSaveable { mutableStateOf(base.example) }
    var replyWords by rememberSaveable { mutableStateOf(base.replyWords.toString()) }
    var avatar by rememberSaveable { mutableStateOf(base.avatar) }
    var chatBg by rememberSaveable { mutableStateOf(base.chatBg) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) Store.importImage(context, uri)?.let { avatar = it }
    }
    val pickBg = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) Store.importImage(context, uri)?.let { chatBg = it }
    }
    val imageOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

    fun save() {
        if (name.isBlank()) { error = "Give your character a name."; return }
        val words = replyWords.toIntOrNull()?.coerceIn(30, 2000) ?: 300
        val c = base.copy(
            name = name.trim(), description = description, personality = personality,
            background = background, opening = opening, example = example,
            avatar = avatar, chatBg = chatBg, replyWords = words,
        )
        Store.saveCharacter(c)
        if (isNew) onCreated(c.id) else onBack()
    }

    Scaffold(
        containerColor = EmberColors.Bg,
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "New character" else "Edit ${base.name}", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EmberColors.Bg),
            )
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().background(EmberColors.Bg).navigationBarsPadding().imePadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f).height(54.dp)
                        .border(1.5.dp, EmberColors.Outline, RoundedCornerShape(27.dp)),
                ) { Text("Cancel", color = EmberColors.Text, fontSize = 16.sp) }
                Button(
                    onClick = { save() },
                    modifier = Modifier.weight(1f).height(54.dp),
                    shape = RoundedCornerShape(27.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmberColors.Red),
                ) { Text(if (isNew) "Create" else "Save", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            }
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ImageSlot("Avatar", avatar, required = false, hint = "Upload photo", Modifier.weight(1f)) {
                    pickAvatar.launch(imageOnly)
                }
                ImageSlot("Chat background", chatBg, required = false, hint = "Optional\nUses avatar if empty", Modifier.weight(1f)) {
                    pickBg.launch(imageOnly)
                }
            }
            if (chatBg != null) {
                TextButton(onClick = { chatBg = null }) { Text("Remove chat background", color = EmberColors.RedSoft) }
            }

            EmberField("Name", name, { name = it; error = null }, required = true,
                placeholder = "Character name", singleLine = true)
            EmberField("Short description", description, { description = it },
                placeholder = "One or two lines, shown at the top of the chat.", minLines = 2)
            EmberField("Personality", personality, { personality = it },
                placeholder = "Temperament, how they talk, what they want, what they hide, habits.",
                minLines = 5, trailingInfo = "No limit")
            EmberField("Background", background, { background = it },
                placeholder = "Appearance, history, world, relationships, the setup between them and your character. Paste as much as you like.",
                minLines = 7,
                trailingInfo = "${wordCount(background)} words · ~${background.length / 4} tokens")
            EmberField("Opening message", opening, { opening = it },
                placeholder = "Write the opening scene like a novel: third person, \"quotes\" for dialogue, actions as full sentences.",
                minLines = 5, trailingInfo = "No limit")
            EmberField("Example dialogue", example, { example = it },
                placeholder = "Optional. A few lines in their voice so the model copies the style.", minLines = 3)
            EmberField("Default reply length (words)", replyWords, { replyWords = it.filter(Char::isDigit).take(4) },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Text(
                "Tip: write {{char}} for the character's name and {{user}} for yours.",
                color = EmberColors.Dim, fontSize = 13.sp,
            )
            error?.let { Text(it, color = EmberColors.RedSoft) }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ImageSlot(
    label: String,
    path: String?,
    required: Boolean,
    hint: String,
    modifier: Modifier = Modifier,
    onPick: () -> Unit,
) {
    Column(modifier) {
        Text(
            if (required) "$label *" else label,
            fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = EmberColors.Text,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(14.dp))
                .background(EmberColors.Surface)
                .border(1.5.dp, EmberColors.Outline, RoundedCornerShape(14.dp))
                .clickable(onClick = onPick),
            contentAlignment = Alignment.Center,
        ) {
            if (path != null) {
                AsyncImage(
                    model = File(path), contentDescription = label,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.AddPhotoAlternate, null, tint = EmberColors.Muted)
                    Spacer(Modifier.height(8.dp))
                    Text(hint, color = EmberColors.Muted, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
}
