package app.ember

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.ember.ui.CostScreen
import app.ember.ui.LockScreen
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
import app.ember.ui.BottomNav
import app.ember.ui.BuilderScreen
import app.ember.ui.EmberBottomBar
import app.ember.ui.IdeasScreen
import app.ember.ui.Section
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

class MainActivity : FragmentActivity() {
    /** Shown over the app until you unlock, when the fingerprint lock is on. */
    private var locked by mutableStateOf(false)
    private var leftAt = 0L

    override fun onStop() {
        super.onStop()
        leftAt = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        // Lock again after being away for more than 30 seconds (not for quick trips to the photo picker).
        if (Store.settings.lockEnabled && leftAt > 0 && System.currentTimeMillis() - leftAt > 30_000) locked = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(applicationContext)
        locked = Store.settings.lockEnabled
        setContent {
            EmberTheme {
                Box(Modifier.fillMaxSize().background(EmberColors.Bg)) {
                    val nav = rememberNavController()
                    val idArg = listOf(navArgument("id") { type = NavType.StringType })
                    fun tab(route: String) = nav.navigate(route) { popUpTo("library"); launchSingleTop = true }
                    val bottom = BottomNav(
                        characters = { nav.popBackStack("library", inclusive = false) },
                        chats = { tab("chats") },
                        ideas = { tab("ideas") },
                        personas = { tab("personas") },
                        createManual = { Store.editorDraft = null; nav.navigate("edit/new") },
                        createWithAi = { nav.navigate("builder") },
                        continueBuild = { nav.navigate("builder/$it") },
                    )
                    NavHost(navController = nav, startDestination = "library") {
                        composable("library") {
                            LibraryScreen(
                                openChat = { nav.navigate("chat/$it") },
                                editCharacter = { nav.navigate("edit/$it") },
                                openRules = { nav.navigate("rules") },
                                openSettings = { nav.navigate("settings") },
                                bottomBar = { EmberBottomBar(Section.Characters, bottom) },
                                openWrapped = { nav.navigate("wrapped") },
                            )
                        }
                        composable("chats") {
                            ChatsScreen(
                                openChat = { nav.navigate("chat/$it") },
                                bottomBar = { EmberBottomBar(Section.Chats, bottom) },
                            )
                        }
                        composable("ideas") {
                            IdeasScreen(
                                buildFrom = { idea -> Store.builderSeed = idea; nav.navigate("builder") },
                                bottomBar = { EmberBottomBar(Section.Ideas, bottom) },
                                openChat = { nav.navigate("chat/$it") },
                            )
                        }
                        composable("builder") {
                            BuilderScreen(null, onBack = { nav.popBackStack() }, openEditor = { nav.navigate("edit/new") })
                        }
                        composable("builder/{id}", arguments = idArg) { entry ->
                            BuilderScreen(entry.arguments?.getString("id"), onBack = { nav.popBackStack() }, openEditor = { nav.navigate("edit/new") })
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
                                openNewDraft = { nav.navigate("edit/new") },
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
                                openCosts = { nav.navigate("costs") },
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
                        composable("personas") {
                            PersonasScreen(onBack = { nav.popBackStack() }, bottomBar = { EmberBottomBar(Section.Personas, bottom) })
                        }
                        composable("rules") { RulesScreen(onBack = { nav.popBackStack() }) }
                        composable("settings") {
                            SettingsScreen(onBack = { nav.popBackStack() }, openPersonas = { nav.navigate("personas") }, openCosts = { nav.navigate("costs") }, openTrash = { nav.navigate("trash") })
                        }
                        composable("trash") { app.ember.ui.TrashScreen(onBack = { nav.popBackStack() }) }
                        composable("wrapped") { app.ember.ui.WrappedScreen(onBack = { nav.popBackStack() }) }
                        composable("costs") { CostScreen(onBack = { nav.popBackStack() }) }
                    }
                    if (locked) LockScreen(this@MainActivity) { locked = false }
                }
            }
        }
    }
}
