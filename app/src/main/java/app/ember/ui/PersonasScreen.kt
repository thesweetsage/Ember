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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ember.data.Persona
import app.ember.data.Store
import coil.compose.AsyncImage
import java.io.File

@Composable
fun PersonasScreen(onBack: () -> Unit) {
    var editing by remember { mutableStateOf<Persona?>(null) }

    Scaffold(
        containerColor = EmberColors.Bg,
        topBar = { EmberTopBar("Your personas", onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = Persona(name = "") },
                containerColor = EmberColors.Red, contentColor = Color.White,
                icon = { Icon(Icons.Filled.Add, null) }, text = { Text("New persona") },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Text(
                "The characters you play. Pick a default here, a default per character when you edit it, or switch per chat from the chat bar.",
                fontSize = 13.sp, color = EmberColors.Muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            if (Store.personas.isEmpty()) {
                Text("No personas yet. Create the first one with the button below.", color = EmberColors.Muted, modifier = Modifier.padding(16.dp))
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(Store.personas, key = { it.id }) { p ->
                    val isDefault = p.id == Store.settings.defaultPersonaId
                    Column(Modifier.clickable { editing = p }) {
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(14.dp))
                                .background(EmberColors.Field)
                                .border(2.dp, if (isDefault) EmberColors.Red else Color.Transparent, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (p.avatar != null) AsyncImage(File(p.avatar), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            else Text(p.name.take(1).uppercase(), fontSize = 32.sp, color = EmberColors.Muted, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(p.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (isDefault) "Default" else "Tap to edit", fontSize = 12.5.sp, color = if (isDefault) EmberColors.RedSoft else EmberColors.Muted)
                    }
                }
            }
        }
    }

    editing?.let { PersonaEditor(it) { editing = null } }
}

@Composable
private fun PersonaEditor(start: Persona, close: () -> Unit) {
    val context = LocalContext.current
    val isNew = Store.persona(start.id) == null
    var name by remember { mutableStateOf(start.name) }
    var description by remember { mutableStateOf(start.description) }
    var avatar by remember { mutableStateOf(start.avatar) }
    var makeDefault by remember { mutableStateOf(Store.settings.defaultPersonaId == start.id || Store.personas.isEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) Store.importImage(context, uri)?.let { avatar = it }
    }

    AlertDialog(
        onDismissRequest = close,
        title = { Text(if (isNew) "New persona" else "Edit persona") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clickable { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Avatar(avatar, name.ifBlank { "?" }, 64.dp)
                    }
                    Spacer(Modifier.height(0.dp))
                    TextButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Text(if (avatar == null) "Add photo" else "Change photo", color = EmberColors.RedSoft)
                    }
                }
                EmberField("Name", name, { name = it }, required = true, singleLine = true, placeholder = "e.g. Evelyn")
                EmberField(
                    "Description", description, { description = it }, minLines = 5,
                    placeholder = "Look, personality, how she talks, history, anything the characters should know.",
                )
                ToggleRow("Use as my default persona", makeDefault, divider = false) { makeDefault = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    val p = start.copy(name = name.trim(), description = description.trim(), avatar = avatar)
                    Store.savePersona(p)
                    if (makeDefault) Store.updateSettings(Store.settings.copy(defaultPersonaId = p.id))
                    close()
                }
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = { confirmDelete = true }) { Text("Delete", color = EmberColors.RedSoft) }
                TextButton(onClick = close) { Text("Cancel") }
            }
        },
        containerColor = EmberColors.Surface,
    )

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${start.name}?") },
            text = { Text("Chats that used this persona will switch to your default persona.") },
            confirmButton = { TextButton(onClick = { Store.deletePersona(start.id); confirmDelete = false; close() }) { Text("Delete", color = EmberColors.RedSoft) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
            containerColor = EmberColors.Surface,
        )
    }
}
