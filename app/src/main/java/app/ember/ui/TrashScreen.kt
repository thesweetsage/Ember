package app.ember.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import app.ember.data.Store

/** Deleted characters and chats, kept for 30 days. */
@Composable
fun TrashScreen(onBack: () -> Unit) {
    var note by remember { mutableStateOf<String?>(null) }
    var forever by remember { mutableStateOf<Store.TrashItem?>(null) }
    Scaffold(containerColor = EmberColors.Bg, topBar = { EmberTopBar("Recently deleted", onBack) }) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text("Deleted characters and chats stay here for 30 days, then they're removed for good.", fontSize = 13.sp, color = EmberColors.Muted)
            }
            note?.let { item { Text(it, color = EmberColors.RedSoft, fontSize = 13.sp) } }
            if (Store.trash.isEmpty()) item { Text("Nothing here.", color = EmberColors.Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp)) }
            items(Store.trash, key = { it.id }) { t ->
                val daysLeft = (30 - (System.currentTimeMillis() - t.deletedAt) / 86_400_000L).coerceAtLeast(0)
                Column(Modifier.fillMaxWidth().background(EmberColors.Surface, RoundedCornerShape(14.dp)).padding(14.dp)) {
                    Text(
                        if (t.kind == "character") t.name.ifBlank { "Unnamed character" } else "Chat with ${t.name.ifBlank { "a character" }}",
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        (if (t.kind == "character") "Character · ${t.chatCount} chat${if (t.chatCount == 1) "" else "s"} · " else "Chat · ") +
                            "$daysLeft day${if (daysLeft == 1L) "" else "s"} left",
                        fontSize = 12.5.sp, color = EmberColors.Muted,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { note = Store.restore(t.id) }) { Text("Restore", color = EmberColors.RedSoft) }
                        TextButton(onClick = { forever = t }) { Text("Delete forever", color = EmberColors.Muted) }
                    }
                }
            }
        }
    }
    forever?.let { t ->
        AlertDialog(
            onDismissRequest = { forever = null },
            title = { Text("Delete forever?") },
            text = { Text("This can't be undone.") },
            confirmButton = { TextButton(onClick = { Store.deleteForever(t.id); forever = null }) { Text("Delete", color = EmberColors.RedSoft) } },
            dismissButton = { TextButton(onClick = { forever = null }) { Text("Cancel") } },
            containerColor = EmberColors.Surface,
        )
    }
}
