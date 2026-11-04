package app.ember

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.ember.data.Store
import app.ember.ui.ArchivesScreen
import app.ember.ui.ChatScreen
import app.ember.ui.ChatSettingsScreen
import app.ember.ui.ChatsScreen
import app.ember.ui.EditCharacterScreen
import app.ember.ui.EmberColors
import app.ember.ui.EmberTheme
import app.ember.ui.LibraryScreen
import app.ember.ui.MemoryScreen
import app.ember.ui.PersonasScreen
import app.ember.ui.RulesScreen
import app.ember.ui.SearchScreen
import app.ember.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(applicationContext)
        setContent {
            EmberTheme {
                Box(Modifier.fillMaxSize().background(EmberColors.Bg)) {
                    val nav = rememberNavController()
                    val idArg = listOf(navArgument("id") { type = NavType.StringType })
                    NavHost(navController = nav, startDestination = "library") {
                        composable("library") {
                            LibraryScreen(
                                openChat = { nav.navigate("chat/$it") },
                                editCharacter = { nav.navigate("edit/$it") },
                                openRules = { nav.navigate("rules") },
                                openSettings = { nav.navigate("settings") },
                                openPersonas = { nav.navigate("personas") },
                                openChats = { nav.navigate("chats") { popUpTo("library"); launchSingleTop = true } },
                            )
                        }
                        composable("chats") {
                            ChatsScreen(
                                openChat = { nav.navigate("chat/$it") },
                                openCharacters = { nav.popBackStack("library", inclusive = false) },
                                create = { nav.navigate("edit/new") },
                                openPersonas = { nav.navigate("personas") },
                                openRules = { nav.navigate("rules") },
                            )
                        }
                        composable("edit/{id}", arguments = idArg) { entry ->
                            val id = entry.arguments?.getString("id") ?: "new"
                            EditCharacterScreen(
                                characterId = id,
                                onBack = { nav.popBackStack() },
                                onCreated = { newId ->
                                    nav.popBackStack()
                                    nav.navigate("chat/$newId")
                                },
                            )
                        }
                        composable("chat/{id}", arguments = idArg) { entry ->
                            val id = entry.arguments?.getString("id") ?: ""
                            ChatScreen(
                                characterId = id,
                                onBack = { nav.popBackStack() },
                                editCharacter = { nav.navigate("edit/$id") },
                                openRules = { nav.navigate("rules") },
                                openChatSettings = { nav.navigate("chatsettings/$id") },
                                openMemory = { nav.navigate("memory/$id") },
                                openArchives = { nav.navigate("archives/$id") },
                                openSearch = { nav.navigate("search/$id") },
                                openPersonas = { nav.navigate("personas") },
                            )
                        }
                        composable("chatsettings/{id}", arguments = idArg) { entry ->
                            val id = entry.arguments?.getString("id") ?: ""
                            ChatSettingsScreen(
                                characterId = id,
                                onBack = { nav.popBackStack() },
                                openMemory = { nav.navigate("memory/$id") },
                                openArchives = { nav.navigate("archives/$id") },
                                openSearch = { nav.navigate("search/$id") },
                                openRules = { nav.navigate("rules") },
                                openSettings = { nav.navigate("settings") },
                            )
                        }
                        composable("memory/{id}", arguments = idArg) { entry ->
                            MemoryScreen(entry.arguments?.getString("id") ?: "", onBack = { nav.popBackStack() })
                        }
                        composable("archives/{id}", arguments = idArg) { entry ->
                            val id = entry.arguments?.getString("id") ?: ""
                            ArchivesScreen(id, onBack = { nav.popBackStack("chat/$id", inclusive = false) || nav.popBackStack() })
                        }
                        composable("search/{id}", arguments = idArg) { entry ->
                            val id = entry.arguments?.getString("id") ?: ""
                            SearchScreen(id, onBack = { nav.popBackStack("chat/$id", inclusive = false) || nav.popBackStack() })
                        }
                        composable("personas") { PersonasScreen(onBack = { nav.popBackStack() }) }
                        composable("rules") { RulesScreen(onBack = { nav.popBackStack() }) }
                        composable("settings") {
                            SettingsScreen(onBack = { nav.popBackStack() }, openPersonas = { nav.navigate("personas") })
                        }
                    }
                }
            }
        }
    }
}
