package app.ember.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.ember.ai.Ai
import app.ember.ai.CreativePrompts
import app.ember.data.Store
import app.ember.data.isGroup
import kotlinx.coroutines.launch

/** "Playlist": songs that fit a character, saved with him, each opening in Spotify or YouTube. */
@Composable
fun PlaylistDialog(characterId: String, onDismiss: () -> Unit) {
    val c = Store.character(characterId) ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun make() {
        busy = true; error = null
        val scene = Store.activeChat(characterId)?.scene.orEmpty()
        scope.launch {
            try {
                val songs = CreativePrompts.parsePlaylist(Ai.complete(CreativePrompts.playlist(c, scene), null, 0.9, preferBackup = Ai.creativeOnBackup))
                if (songs.isEmpty()) error = "No songs came back. Try again."
                else Store.saveCharacter((Store.character(characterId) ?: c).copy(playlist = songs))
            } catch (e: Exception) {
                error = e.message ?: "Couldn't make a playlist."
            } finally {
                busy = false
            }
        }
    }

    fun open(url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = EmberColors.Bg) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${c.name}'s playlist", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                Text(
                    "Real songs that fit ${if (c.isGroup) "them" else "him"} and your story. Tap Spotify or YouTube to play one. The AI can occasionally get a song wrong, so a search might not find it.",
                    fontSize = 13.sp, color = EmberColors.Muted,
                )
                val songs = Store.character(characterId)?.playlist.orEmpty()
                songs.forEach { line ->
                    val parts = line.split(" – ").map { it.trim() }
                    val artist = parts.getOrNull(0).orEmpty()
                    val title = parts.getOrNull(1).orEmpty()
                    val why = parts.drop(2).joinToString(" – ")
                    val query = Uri.encode("$artist $title")
                    Column(Modifier.fillMaxWidth().background(EmberColors.Surface, RoundedCornerShape(14.dp)).padding(start = 14.dp, top = 12.dp, end = 6.dp)) {
                        Text(title.ifBlank { line }, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        if (artist.isNotBlank()) Text(artist, fontSize = 13.sp, color = EmberColors.TextSoft)
                        if (why.isNotBlank()) Text(why, fontSize = 12.5.sp, color = EmberColors.Muted, lineHeight = 17.sp)
                        Row {
                            TextButton(onClick = { open("https://open.spotify.com/search/$query") }) { Text("Spotify", color = EmberColors.RedSoft) }
                            TextButton(onClick = { open("https://music.youtube.com/search?q=$query") }) { Text("YouTube", color = EmberColors.RedSoft) }
                        }
                    }
                }
                error?.let { Text(it, color = EmberColors.RedSoft, fontSize = 13.sp) }
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { make() }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmberColors.Red),
                ) { Text(if (busy) "Picking songs…" else if (songs.isEmpty()) "Make ${if (c.isGroup) "their" else "his"} playlist" else "New playlist", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}
