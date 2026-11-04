package app.ember.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.AlertDialog
import app.ember.data.Store
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ember.data.Tags

@Composable
fun TagChip(label: String, on: Boolean, onLongClick: (() -> Unit)? = null, onClick: (() -> Unit)?) {
    Box(
        Modifier.clip(RoundedCornerShape(16.dp))
            .background(if (on) EmberColors.Red else EmberColors.Field)
            .let { if (onClick != null) it.combinedClickable(onClick = onClick, onLongClick = onLongClick) else it }
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(label, fontSize = 13.sp, color = if (on) Color.White else EmberColors.TextSoft, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
    }
}

/** Read-only display of a character's tags, grouped by category. */
@Composable
fun TagSummary(tags: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Tags.grouped(tags).forEach { (cat, list) ->
            Column {
                Text(cat, fontSize = 12.sp, color = EmberColors.Muted)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    list.forEach { TagChip(Tags.name(it), on = false, onClick = null) }
                }
            }
        }
    }
}

/** Full-height picker: every category, built-in suggestions, your own tags. No limit. */
@Composable
fun TagPickerSheet(title: String, initial: List<String>, onDone: (List<String>) -> Unit, onDismiss: () -> Unit, doneLabel: String = "Done") {
    var selected by remember { mutableStateOf(initial) }
    var filter by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf<String?>(null) }
    var newTag by remember { mutableStateOf("") }
    var removing by remember { mutableStateOf<String?>(null) }

    fun toggle(tag: String) {
        selected = if (tag in selected) selected - tag else selected + tag
    }

    // A full-screen page (not a swipeable sheet), so scrolling can never close it.
    // Closing it with the back button keeps your picks.
    Dialog(
        onDismissRequest = { onDone(selected) },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
      Surface(Modifier.fillMaxSize(), color = EmberColors.Bg) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
                .padding(horizontal = 16.dp).padding(top = 12.dp)
        ) {
            removing?.let { tag ->
                AlertDialog(
                    onDismissRequest = { removing = null },
                    title = { Text("Remove \"${Tags.name(tag)}\"?") },
                    text = { Text("It stops being suggested. Characters that already have it keep it.") },
                    confirmButton = {
                        TextButton(onClick = { Store.removeCustomTag(tag); selected = selected - tag; removing = null }) {
                            Text("Remove", color = EmberColors.RedSoft)
                        }
                    },
                    dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
                    containerColor = EmberColors.Surface,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${selected.size} selected", fontSize = 13.sp, color = EmberColors.Muted)
            }
            Spacer(Modifier.height(10.dp))
            TextField(
                value = filter, onValueChange = { filter = it }, singleLine = true,
                placeholder = { Text("Find a tag", color = EmberColors.Dim) },
                modifier = Modifier.fillMaxWidth(), colors = fieldColors(), shape = RoundedCornerShape(24.dp),
            )
            Text(
                "Tags you add are saved for every character. Long-press one of your own tags to remove it.",
                fontSize = 12.sp, color = EmberColors.Dim, modifier = Modifier.padding(top = 6.dp),
            )
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Tags.categories.forEach { cat ->
                    val own = selected.filter { Tags.category(it) == cat.name }.map { Tags.name(it) }
                    val extra = Store.extraTags(cat.name)
                    val all = (cat.suggestions + extra + own).distinct()
                        .filter { filter.isBlank() || it.contains(filter.trim(), ignoreCase = true) }
                    val count = own.size
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(cat.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            if (count > 0) Text("$count", fontSize = 13.sp, color = EmberColors.RedSoft)
                        }
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            all.forEach { name ->
                                val tag = Tags.make(cat.name, name)
                                val isCustom = name in extra
                                TagChip(
                                    name, on = tag in selected,
                                    onLongClick = if (isCustom) ({ removing = tag }) else null,
                                ) { toggle(tag) }
                            }
                            Box(
                                Modifier.clip(RoundedCornerShape(16.dp)).border(1.dp, EmberColors.Outline, RoundedCornerShape(16.dp))
                                    .clickable { adding = cat.name; newTag = filter.trim() }.padding(horizontal = 12.dp, vertical = 6.dp),
                            ) { Text("+ Add your own", fontSize = 13.sp, color = EmberColors.Muted) }
                        }
                        if (adding == cat.name) {
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextField(
                                    value = newTag, onValueChange = { newTag = it }, singleLine = true,
                                    placeholder = { Text("New ${cat.name.lowercase()} tag", color = EmberColors.Dim) },
                                    modifier = Modifier.weight(1f), colors = fieldColors(), shape = RoundedCornerShape(20.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                TextButton(onClick = {
                                    if (newTag.isNotBlank()) {
                                        val tag = Tags.make(cat.name, newTag)
                                        Store.addCustomTag(tag)
                                        if (tag !in selected) selected = selected + tag
                                    }
                                    newTag = ""; adding = null
                                }) { Text("Add", color = EmberColors.RedSoft) }
                            }
                        }
                    }
                }
            }
            Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = { selected = emptyList() }) { Text("Clear all", color = EmberColors.Muted) }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { onDone(selected) },
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmberColors.Red),
                ) { Text(doneLabel) }
            }
        }
    }
      }
}
