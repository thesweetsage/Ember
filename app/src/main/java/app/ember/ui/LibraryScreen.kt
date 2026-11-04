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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Rule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ember.data.Character
import app.ember.data.Store
import coil.compose.AsyncImage
import java.io.File

private enum class Tab(val label: String) { All("All"), Favourites("Favourites"), Recent("Recent") }

@Composable
fun LibraryScreen(
    openChat: (String) -> Unit,
    editCharacter: (String) -> Unit,
    openRules: () -> Unit,
    openSettings: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.All) }
    var confirmDelete by remember { mutableStateOf<Character?>(null) }

    val all = Store.characters
    val shown = when (tab) {
        Tab.All -> all.sortedBy { it.name.lowercase() }
        Tab.Favourites -> all.filter { it.favourite }.sortedBy { it.name.lowercase() }
        Tab.Recent -> all.sortedByDescending { maxOf(Store.lastActivity(it.id), it.createdAt) }
    }

    Scaffold(
        containerColor = EmberColors.Bg,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editCharacter("new") },
                containerColor = EmberColors.Red, contentColor = Color.White, shape = CircleShape,
            ) { Icon(Icons.Filled.Add, contentDescription = "New character") }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Ember", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = EmberColors.Text)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = openRules) { Icon(Icons.Outlined.Rule, "Rules", tint = EmberColors.Text) }
                IconButton(onClick = openSettings) { Icon(Icons.Outlined.Settings, "Settings", tint = EmberColors.Text) }
            }
            if (!Store.hasApiKey) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth()
                        .background(EmberColors.Surface, RoundedCornerShape(12.dp))
                        .clickable(onClick = openSettings).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Add your DeepSeek API key in Settings to start chatting.",
                        color = EmberColors.TextSoft, fontSize = 14.sp, modifier = Modifier.weight(1f),
                    )
                    Text("Open", color = EmberColors.RedSoft, fontWeight = FontWeight.SemiBold)
                }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(Tab.entries.toList()) { t ->
                    val on = t == tab
                    val label = if (t == Tab.All) "All ${all.size}" else t.label
                    Box(
                        Modifier.height(36.dp).clip(RoundedCornerShape(18.dp))
                            .background(if (on) EmberColors.Text else EmberColors.Field)
                            .clickable { tab = t }.padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label, fontSize = 14.sp,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (on) EmberColors.Bg else EmberColors.TextSoft,
                        )
                    }
                }
            }
            if (shown.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (tab == Tab.Favourites) "No favourites yet." else "No characters yet.",
                        color = EmberColors.Muted, fontSize = 16.sp,
                    )
                    if (tab != Tab.Favourites) {
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { editCharacter("new") }) { Text("Create your first character") }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    items(shown, key = { it.id }) { c ->
                        CharacterCard(
                            c,
                            onOpen = { openChat(c.id) },
                            onEdit = { editCharacter(c.id) },
                            onFavourite = { Store.saveCharacter(c.copy(favourite = !c.favourite)) },
                            onDelete = { confirmDelete = c },
                        )
                    }
                }
            }
        }
    }

    confirmDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete ${c.name}?") },
            text = { Text("This deletes the character and all of their chats. It can't be undone.") },
            confirmButton = {
                TextButton(onClick = { Store.deleteCharacter(c.id); confirmDelete = null }) {
                    Text("Delete", color = EmberColors.RedSoft)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
            containerColor = EmberColors.Surface,
        )
    }
}

@Composable
private fun CharacterCard(
    c: Character,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onFavourite: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxWidth().aspectRatio(3f / 4f).background(EmberColors.Field).clickable(onClick = onOpen)
    ) {
        if (c.avatar != null) {
            AsyncImage(
                model = File(c.avatar), contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                c.name.take(1).uppercase(), fontSize = 40.sp, fontWeight = FontWeight.Bold,
                color = EmberColors.Muted, modifier = Modifier.align(Alignment.Center),
            )
        }
        Box(
            Modifier.fillMaxWidth().height(90.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEB140807))))
        )
        if (c.favourite) {
            Icon(
                Icons.Filled.Favorite, contentDescription = "Favourite", tint = EmberColors.RedSoft,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp).size(16.dp),
            )
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 10.dp, end = 30.dp)) {
            Text(
                c.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Outlined.Chat, null, tint = EmberColors.TextSoft,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("${Store.messageCount(c.id)}", color = EmberColors.TextSoft, fontSize = 12.sp)
            }
        }
        Box(Modifier.align(Alignment.BottomEnd)) {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.MoreVert, "More options for ${c.name}", tint = Color.White)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit() })
                DropdownMenuItem(
                    text = { Text(if (c.favourite) "Remove from favourites" else "Add to favourites") },
                    onClick = { menu = false; onFavourite() },
                )
                DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
            }
        }
    }
}
