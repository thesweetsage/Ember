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
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Rule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sell
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
import app.ember.data.Tags
import coil.compose.AsyncImage
import java.io.File

private enum class Tab(val label: String) { All("All"), Favourites("Favourites"), Recent("Recent"), Archived("Archived") }

@Composable
fun LibraryScreen(
    openChat: (String) -> Unit,
    editCharacter: (String) -> Unit,
    openRules: () -> Unit,
    openSettings: () -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.All) }
    var confirmDelete by remember { mutableStateOf<Character?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var tagFilter by remember { mutableStateOf(listOf<String>()) }
    var pickTags by remember { mutableStateOf(false) }

    val all = Store.characters.filter { !it.archived }
    val q = query.trim().lowercase()
    val shown = when (tab) {
        Tab.All -> all.sortedBy { it.name.lowercase() }
        Tab.Favourites -> all.filter { it.favourite }.sortedBy { it.name.lowercase() }
        Tab.Recent -> all.sortedByDescending { maxOf(Store.lastActivity(it.id), it.createdAt) }
        Tab.Archived -> Store.characters.filter { it.archived }.sortedBy { it.name.lowercase() }
    }.filter { c -> q.isEmpty() || c.name.lowercase().contains(q) || c.tags.any { Tags.name(it).lowercase().contains(q) } }
        .filter { c -> tagFilter.all { it in c.tags } }
    val pinned = Store.character(Store.settings.pinnedCharacterId ?: "")?.takeIf { !it.archived }
    val showBanner = pinned != null && tab == Tab.All && q.isEmpty() && tagFilter.isEmpty()
    val gridItems = if (showBanner) shown.filter { it.id != pinned?.id } else shown

    Scaffold(
        containerColor = EmberColors.Bg,
        bottomBar = bottomBar,
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Ember", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = EmberColors.Text)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { searching = !searching; if (!searching) query = "" }) { Icon(Icons.Outlined.Search, "Search", tint = EmberColors.Text) }
                IconButton(onClick = { pickTags = true }) {
                    Icon(Icons.Outlined.Sell, "Filter by tags", tint = if (tagFilter.isEmpty()) EmberColors.Text else EmberColors.RedSoft)
                }
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
            if (searching) {
                androidx.compose.material3.TextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text("Search names or tags", color = EmberColors.Dim) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = fieldColors(), shape = RoundedCornerShape(24.dp),
                )
            }
            if (tagFilter.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(tagFilter) { t -> TagChip(Tags.name(t) + "  ✕", on = true) { tagFilter = tagFilter - t } }
                    item { TagChip("Clear", on = false) { tagFilter = emptyList() } }
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
            if (gridItems.isEmpty() && !showBanner) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        when {
                            q.isNotEmpty() || tagFilter.isNotEmpty() -> "No matches."
                            tab == Tab.Favourites -> "No favourites yet."
                            tab == Tab.Archived -> "Nothing archived."
                            else -> "No characters yet."
                        },
                        color = EmberColors.Muted, fontSize = 16.sp,
                    )
                    if (tab == Tab.All && q.isEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { editCharacter("new") }) { Text("Create your first character") }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    if (showBanner && pinned != null) {
                        item(key = "pinned", span = { GridItemSpan(maxLineSpan) }) {
                            PinnedBanner(
                                pinned,
                                onOpen = { openChat(pinned.id) },
                                onEdit = { editCharacter(pinned.id) },
                                onUnpin = { Store.setPinned(null) },
                            )
                        }
                    }
                    items(gridItems, key = { it.id }) { c ->
                        CharacterCard(
                            c,
                            isPinned = c.id == pinned?.id,
                            onPin = { Store.setPinned(if (c.id == pinned?.id) null else c.id) },
                            onOpen = { openChat(c.id) },
                            onEdit = { editCharacter(c.id) },
                            onFavourite = { Store.saveCharacter(c.copy(favourite = !c.favourite)) },
                            onArchive = { Store.saveCharacter(c.copy(archived = !c.archived)) },
                            onDelete = { confirmDelete = c },
                        )
                    }
                }
            }
        }
    }

    if (pickTags) {
        TagPickerSheet(
            title = "Show characters with these tags", initial = tagFilter, doneLabel = "Show",
            onDone = { tagFilter = it; pickTags = false }, onDismiss = { pickTags = false },
        )
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
    isPinned: Boolean,
    onPin: () -> Unit,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onFavourite: () -> Unit,
    onArchive: () -> Unit,
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
                DropdownMenuItem(text = { Text(if (isPinned) "Unpin from top" else "Pin to top") }, onClick = { menu = false; onPin() })
                DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit() })
                DropdownMenuItem(
                    text = { Text(if (c.favourite) "Remove from favourites" else "Add to favourites") },
                    onClick = { menu = false; onFavourite() },
                )
                DropdownMenuItem(text = { Text(if (c.archived) "Unarchive" else "Archive") }, onClick = { menu = false; onArchive() })
                DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

/** Wide banner for the pinned character: photo, last line of the chat, and a way straight back in. */
@Composable
private fun PinnedBanner(c: Character, onOpen: () -> Unit, onEdit: () -> Unit, onUnpin: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val chat = Store.activeChat(c.id)
    val last = chat?.messages?.lastOrNull()?.text?.replace("\n", " ")?.trim().orEmpty()
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp).height(190.dp)
            .clip(RoundedCornerShape(18.dp)).background(EmberColors.Field).clickable(onClick = onOpen)
    ) {
        val photo = c.chatBg ?: c.avatar
        if (photo != null) {
            AsyncImage(model = File(photo), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0x33140807), Color(0x99140807), Color(0xF0140807)))
            )
        )
        Text(
            "PINNED", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                .background(EmberColors.Red, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
        )
        Box(Modifier.align(Alignment.TopEnd)) {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More options for ${c.name}", tint = Color.White) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Unpin from top") }, onClick = { menu = false; onUnpin() })
                DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit() })
            }
        }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp)) {
            Text(c.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (last.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(last, color = EmberColors.TextSoft, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(EmberColors.Red)
                        .clickable(onClick = onOpen).padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Continue chat", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.width(12.dp))
                Icon(Icons.AutoMirrored.Outlined.Chat, null, tint = EmberColors.TextSoft, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("${Store.messageCount(c.id)}", color = EmberColors.TextSoft, fontSize = 13.sp)
            }
        }
    }
}
