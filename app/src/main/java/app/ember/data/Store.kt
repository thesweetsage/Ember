package app.ember.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.concurrent.Executors

/**
 * All app data, kept in memory as Compose state and saved as JSON files in the app's
 * private folder. Nothing leaves the phone except the requests sent to the model provider.
 */
object Store {
    private lateinit var dir: File
    private lateinit var chatDir: File
    private lateinit var imageDir: File
    private lateinit var prefs: SharedPreferences
    private val writer = Executors.newSingleThreadExecutor()

    var characters by mutableStateOf(listOf<Character>())

        private set
    var chats by mutableStateOf(listOf<Chat>())
        private set
    var rules by mutableStateOf(listOf<Rule>())
        private set
    var groundRules by mutableStateOf(DEFAULT_GROUND_RULES)
        private set
    var settings by mutableStateOf(Settings())
        private set
    var hasApiKey by mutableStateOf(false)
        private set

    fun init(context: Context) {
        if (::dir.isInitialized) return
        dir = File(context.filesDir, "ember").apply { mkdirs() }
        chatDir = File(dir, "chats").apply { mkdirs() }
        imageDir = File(dir, "images").apply { mkdirs() }
        prefs = context.getSharedPreferences("ember", Context.MODE_PRIVATE)

        characters = read("characters.json")?.let { Json.parseList(it) { o -> Json.character(o) } } ?: emptyList()
        rules = read("rules.json")?.let { Json.parseList(it) { o -> Json.rule(o) } } ?: emptyList()
        groundRules = read("ground.json")?.let { Json.parseList(it) { o -> Json.ground(o) } }
            ?.takeIf { it.isNotEmpty() } ?: DEFAULT_GROUND_RULES
        settings = read("settings.json")?.let { runCatching { Json.settings(org.json.JSONObject(it)) }.getOrNull() }
            ?: Settings()
        chats = chatDir.listFiles().orEmpty().filter { it.name.endsWith(".json") }.mapNotNull { f ->
            runCatching { Json.chat(org.json.JSONObject(f.readText())) }.getOrNull()
        }
        hasApiKey = prefs.contains("apiKey")
    }

    // ---------- files ----------

    private fun read(name: String): String? = File(dir, name).takeIf { it.exists() }?.readText()

    private fun write(file: File, text: String) {
        writer.execute {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) {
                file.delete(); tmp.renameTo(file)
            }
        }
    }

    private fun saveCharacters() = write(File(dir, "characters.json"), Json.list(characters) { Json.character(it) })
    private fun saveRules() = write(File(dir, "rules.json"), Json.list(rules) { Json.rule(it) })
    private fun saveGround() = write(File(dir, "ground.json"), Json.list(groundRules) { Json.ground(it) })
    private fun saveSettings() = write(File(dir, "settings.json"), Json.settings(settings).toString())

    /** Copies a picked photo into the app's own folder and returns its path. */
    fun importImage(context: Context, uri: Uri): String? = runCatching {
        val out = File(imageDir, newId() + ".img")
        context.contentResolver.openInputStream(uri)!!.use { input ->
            out.outputStream().use { input.copyTo(it) }
        }
        out.absolutePath
    }.getOrNull()

    private fun deleteImage(path: String?) {
        if (path != null && path.startsWith(imageDir.absolutePath)) File(path).delete()
    }

    // ---------- API key ----------

    fun apiKey(): String = prefs.getString("apiKey", null)?.let { KeyVault.decrypt(it) }.orEmpty()

    fun setApiKey(key: String) {
        if (key.isBlank()) prefs.edit().remove("apiKey").apply()
        else prefs.edit().putString("apiKey", KeyVault.encrypt(key.trim())).apply()
        hasApiKey = key.isNotBlank()
    }

    // ---------- characters ----------

    fun character(id: String): Character? = characters.firstOrNull { it.id == id }

    fun saveCharacter(c: Character) {
        val old = character(c.id)
        if (old != null) {
            if (old.avatar != c.avatar) deleteImage(old.avatar)
            if (old.chatBg != c.chatBg) deleteImage(old.chatBg)
        }
        characters = if (old == null) characters + c else characters.map { if (it.id == c.id) c else it }
        saveCharacters()
    }

    fun deleteCharacter(id: String) {
        val c = character(id) ?: return
        deleteImage(c.avatar); deleteImage(c.chatBg)
        chats.filter { it.characterId == id }.forEach { File(chatDir, it.id + ".json").delete() }
        chats = chats.filter { it.characterId != id }
        characters = characters.filter { it.id != id }
        saveCharacters()
    }

    fun messageCount(characterId: String): Int =
        chats.filter { it.characterId == characterId }.sumOf { it.messages.size }

    fun lastActivity(characterId: String): Long =
        chats.filter { it.characterId == characterId }.maxOfOrNull { it.updatedAt } ?: 0L

    // ---------- chats ----------

    fun chat(id: String): Chat? = chats.firstOrNull { it.id == id }

    fun latestChat(characterId: String): Chat? =
        chats.filter { it.characterId == characterId }.maxByOrNull { it.updatedAt }

    fun newChat(c: Character): Chat {
        val opening = c.opening.trim()
        val chat = Chat(
            characterId = c.id,
            replyWords = c.replyWords,
            messages = if (opening.isEmpty()) emptyList()
            else listOf(Message(fromUser = false, versions = listOf(opening))),
        )
        putChat(chat, persist = true)
        return chat
    }

    /** Replaces a chat in memory; persist = false while a reply is still streaming in. */
    fun putChat(chat: Chat, persist: Boolean = true) {
        val updated = chat.copy(updatedAt = System.currentTimeMillis())
        chats = if (chats.any { it.id == chat.id }) chats.map { if (it.id == chat.id) updated else it }
        else chats + updated
        if (persist) persistChat(chat.id)
    }

    fun persistChat(id: String) {
        val c = chat(id) ?: return
        write(File(chatDir, c.id + ".json"), Json.chat(c).toString())
    }

    fun updateMessage(chatId: String, messageId: String, persist: Boolean, f: (Message) -> Message) {
        val c = chat(chatId) ?: return
        putChat(c.copy(messages = c.messages.map { if (it.id == messageId) f(it) else it }), persist)
    }

    // ---------- rules & settings ----------

    fun updateRules(list: List<Rule>) { rules = list; saveRules() }
    fun updateGroundRules(list: List<GroundRule>) { groundRules = list; saveGround() }
    fun resetGroundRules() = updateGroundRules(DEFAULT_GROUND_RULES)
    fun updateSettings(s: Settings) { settings = s; saveSettings() }
}
