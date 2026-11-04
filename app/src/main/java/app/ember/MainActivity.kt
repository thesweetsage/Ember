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
import app.ember.ui.ChatScreen
import app.ember.ui.EditCharacterScreen
import app.ember.ui.EmberColors
import app.ember.ui.EmberTheme
import app.ember.ui.LibraryScreen
import app.ember.ui.RulesScreen
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
                    NavHost(navController = nav, startDestination = "library") {
                        composable("library") {
                            LibraryScreen(
                                openChat = { nav.navigate("chat/$it") },
                                editCharacter = { nav.navigate("edit/$it") },
                                openRules = { nav.navigate("rules") },
                                openSettings = { nav.navigate("settings") },
                            )
                        }
                        composable(
                            "edit/{id}",
                            arguments = listOf(navArgument("id") { type = NavType.StringType }),
                        ) { entry ->
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
                        composable(
                            "chat/{id}",
                            arguments = listOf(navArgument("id") { type = NavType.StringType }),
                        ) { entry ->
                            val id = entry.arguments?.getString("id") ?: ""
                            ChatScreen(
                                characterId = id,
                                onBack = { nav.popBackStack() },
                                editCharacter = { nav.navigate("edit/$id") },
                                openRules = { nav.navigate("rules") },
                                openSettings = { nav.navigate("settings") },
                            )
                        }
                        composable("rules") { RulesScreen(onBack = { nav.popBackStack() }) }
                        composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
                    }
                }
            }
        }
    }
}
