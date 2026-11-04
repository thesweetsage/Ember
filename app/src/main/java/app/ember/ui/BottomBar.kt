package app.ember.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class Section { Characters, Chats, Ideas, Personas }

/** Where each bottom-bar button goes. */
class BottomNav(
    val characters: () -> Unit,
    val chats: () -> Unit,
    val ideas: () -> Unit,
    val personas: () -> Unit,
    val createManual: () -> Unit,
    val createWithAi: () -> Unit,
)

@Composable
fun EmberBottomBar(current: Section, nav: BottomNav) {
    var choose by remember { mutableStateOf(false) }
    val colors = NavigationBarItemDefaults.colors(
        selectedIconColor = EmberColors.Text, selectedTextColor = EmberColors.Text,
        unselectedIconColor = EmberColors.Dim, unselectedTextColor = EmberColors.Dim,
        indicatorColor = EmberColors.Field,
    )
    NavigationBar(containerColor = EmberColors.BgDeep) {
        NavigationBarItem(
            selected = current == Section.Characters, onClick = nav.characters, colors = colors,
            icon = { Icon(Icons.Outlined.GridView, null) }, label = { Text("Characters", fontSize = 11.sp) },
        )
        NavigationBarItem(
            selected = current == Section.Chats, onClick = nav.chats, colors = colors,
            icon = { Icon(Icons.AutoMirrored.Outlined.Chat, null) }, label = { Text("Chats", fontSize = 11.sp) },
        )
        NavigationBarItem(
            selected = false, onClick = { choose = true }, colors = colors,
            icon = {
                Box(Modifier.size(44.dp).background(EmberColors.Red, RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Add, "Create character", tint = Color.White)
                }
            },
        )
        NavigationBarItem(
            selected = current == Section.Ideas, onClick = nav.ideas, colors = colors,
            icon = { Icon(Icons.Outlined.Lightbulb, null) }, label = { Text("Ideas", fontSize = 11.sp) },
        )
        NavigationBarItem(
            selected = current == Section.Personas, onClick = nav.personas, colors = colors,
            icon = { Icon(Icons.Outlined.Person, null) }, label = { Text("Personas", fontSize = 11.sp) },
        )
    }
    if (choose) {
        AlertDialog(
            onDismissRequest = { choose = false },
            title = { Text("New character") },
            text = {
                Column {
                    TextButton(onClick = { choose = false; nav.createWithAi() }) {
                        Icon(Icons.Outlined.AutoAwesome, null, tint = EmberColors.RedSoft)
                        Text("  Build with AI, step by step", color = EmberColors.Text)
                    }
                    TextButton(onClick = { choose = false; nav.createManual() }) {
                        Text("Write it myself", color = EmberColors.Text)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choose = false }) { Text("Cancel") } },
            containerColor = EmberColors.Surface,
        )
    }
}
