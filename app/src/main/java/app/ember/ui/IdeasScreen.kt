package app.ember.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ember.ai.Ai
import app.ember.ai.CreativePrompts
import app.ember.data.Idea
import app.ember.data.Store
import kotlinx.coroutines.launch

class IdeasViewModel : ViewModel() {
    var tags by mutableStateOf(listOf<String>())
    var notes by mutableStateOf("")
    /** "Man", "Woman" or "Any". */
    var gender by mutableStateOf("Man")
    var results by mutableStateOf(listOf<String>())
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)

    fun generate(more: Boolean) {
        if (busy) return
        busy = true; error = null
        val avoid = if (more) results else emptyList()
        viewModelScope.launch {
            try {
                val text = Ai.complete(CreativePrompts.ideas(tags, notes, 6, avoid, gender), 1200, 1.1)
                val parsed = CreativePrompts.parseIdeas(text)
                if (parsed.isEmpty()) error = "No ideas came back. Try again."
                results = if (more) results + parsed else parsed
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong."
            } finally {
                busy = false
            }
        }
    }
}

@Composable
fun IdeasScreen(
    buildFrom: (Idea) -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    val vm: IdeasViewModel = viewModel()
    var pickTags by remember { mutableStateOf(false) }

    Scaffold(containerColor = EmberColors.Bg, bottomBar = bottomBar) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Ideas", fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.statusBarsPadding().padding(top = 12.dp),
                )
                Text(
                    "Pick tags and get short, punchy character concepts. Build any of them with the AI.",
                    fontSize = 13.sp, color = EmberColors.Muted,
                )
            }
            item {
                Column(
                    Modifier.fillMaxWidth().background(EmberColors.Surface, RoundedCornerShape(18.dp)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Tags", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { pickTags = true }) {
                            Text(if (vm.tags.isEmpty()) "+ Choose tags" else "Edit (${vm.tags.size})", color = EmberColors.RedSoft)
                        }
                    }
                    if (vm.tags.isNotEmpty()) TagSummary(vm.tags)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Character", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        listOf("Man", "Woman", "Any").forEach { g -> TagChip(g, on = vm.gender == g) { vm.gender = g } }
                    }
                    EmberField(
                        "Anything else (optional)", vm.notes, { vm.notes = it }, minLines = 2, maxLines = 4,
                        placeholder = "e.g. built around pet play, set in a church, he's a BMX rider",
                    )
                    Button(
                        onClick = { vm.generate(more = false) }, enabled = !vm.busy,
                        modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(25.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmberColors.Red),
                    ) { Text(if (vm.busy) "Thinking…" else "Give me ideas", fontWeight = FontWeight.SemiBold) }
                    vm.error?.let { Text(it, color = EmberColors.RedSoft, fontSize = 13.sp) }
                }
            }
            items(vm.results) { text ->
                IdeaCard(
                    text = text,
                    saved = Store.ideas.any { it.text == text },
                    onSave = {
                        val existing = Store.ideas.firstOrNull { it.text == text }
                        if (existing != null) Store.deleteIdea(existing.id) else Store.saveIdea(Idea(text = text, tags = vm.tags))
                    },
                    onBuild = { buildFrom(Idea(text = text, tags = vm.tags)) },
                )
            }
            if (vm.results.isNotEmpty()) {
                item {
                    OutlinedButton(
                        onClick = { vm.generate(more = true) }, enabled = !vm.busy,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                    ) { Text(if (vm.busy) "Thinking…" else "More ideas", color = EmberColors.Text) }
                }
            }
            if (Store.ideas.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("Saved ideas", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                items(Store.ideas, key = { it.id }) { idea ->
                    IdeaCard(
                        text = idea.text, saved = true,
                        onSave = { Store.deleteIdea(idea.id) },
                        onBuild = { buildFrom(idea) },
                        deleteIcon = true,
                    )
                }
            }
        }
    }

    if (pickTags) {
        TagPickerSheet(
            title = "Tags for new ideas", initial = vm.tags,
            onDone = { vm.tags = it; pickTags = false }, onDismiss = { pickTags = false },
        )
    }
}

@Composable
private fun IdeaCard(text: String, saved: Boolean, onSave: () -> Unit, onBuild: () -> Unit, deleteIcon: Boolean = false) {
    Column(
        Modifier.fillMaxWidth().background(EmberColors.Surface, RoundedCornerShape(16.dp))
            .border(1.dp, EmberColors.Line, RoundedCornerShape(16.dp)).padding(start = 16.dp, top = 14.dp, end = 6.dp, bottom = 6.dp),
    ) {
        Row {
            Text(text, fontSize = 15.5.sp, lineHeight = 22.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onSave) {
                when {
                    deleteIcon -> Icon(Icons.Outlined.Delete, "Remove from saved", tint = EmberColors.Muted)
                    saved -> Icon(Icons.Filled.Bookmark, "Saved", tint = EmberColors.RedSoft)
                    else -> Icon(Icons.Outlined.BookmarkBorder, "Save idea", tint = EmberColors.Muted)
                }
            }
        }
        TextButton(onClick = onBuild) { Text("Build this character with AI ›", color = EmberColors.RedSoft) }
    }
}
