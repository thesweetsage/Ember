package app.ember.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.palette.graphics.Palette
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * All app data, kept in memory as Compose state and saved as JSON files in the app's
 * private folder. Nothing leaves the phone except the requests sent to the model provider.
 */
object Store {
    private lateinit var dir: File
    private lateinit var chatDir: File
    private lateinit var imageDir: File
    private lateinit var prefs: SharedPreferences
    private var writer = Executors.newSingleThreadExecutor()

    var characters by mutableStateOf(listOf<Character>())
        private set
    var chats by mutableStateOf(listOf<Chat>())
        private set
    var rules by mutableStateOf(listOf<Rule>())
        private set
    var presets by mutableStateOf(listOf<Preset>())
        private set
    var personas by mutableStateOf(listOf<Persona>())
        private set
    var ideas by mutableStateOf(listOf<Idea>())
        private set

    /** A concept handed from the Ideas tab to the AI character builder. */
    var builderSeed by mutableStateOf<Idea?>(null)

    /** A character drafted by the AI builder, waiting to be reviewed in the editor. */
    var editorDraft by mutableStateOf<Character?>(null)
    /** The builder conversation that [editorDraft] came from; removed once the character is created. */
    var editorDraftBuildId: String? = null

    var builds by mutableStateOf(listOf<BuildSession>())
        private set

    /** Tags you created yourself; they appear in every tag picker. */
    var customTags by mutableStateOf(listOf<String>())
        private set
    var groundRules by mutableStateOf(DEFAULT_GROUND_RULES)
        private set
    var wfmRules by mutableStateOf(DEFAULT_WFM_RULES)
        private set
    var settings by mutableStateOf(Settings())
        private set
    var hasApiKey by mutableStateOf(false)
        private set
    var hasBackupKey by mutableStateOf(false)
        private set
    var hasVoiceKey by mutableStateOf(false)
        private set
    private var activeChats by mutableStateOf(mapOf<String, String>())

    /** The exact text of the last request sent to the model, for "See what's sent". */
    var lastPrompt by mutableStateOf<String?>(null)

    /** A message to scroll to when a chat opens (set by search). */
    var focusMessageId by mutableStateOf<String?>(null)

    fun init(context: Context) {
        if (::dir.isInitialized) return
        dir = File(context.filesDir, "ember").apply { mkdirs() }
        chatDir = File(dir, "chats").apply { mkdirs() }
        imageDir = File(dir, "images").apply { mkdirs() }
        prefs = context.getSharedPreferences("ember", Context.MODE_PRIVATE)
        load()
    }

    private fun load() {
        characters = read("characters.json")?.let { Json.parseList(it) { o -> Json.character(o) } } ?: emptyList()
        rules = read("rules.json")?.let { Json.parseList(it) { o -> Json.rule(o) } } ?: emptyList()
        presets = read("presets.json")?.let { Json.parseList(it) { o -> Json.preset(o) } } ?: emptyList()
        personas = read("personas.json")?.let { Json.parseList(it) { o -> Json.persona(o) } } ?: emptyList()
        ideas = read("ideas.json")?.let { Json.parseList(it) { o -> Json.idea(o) } } ?: emptyList()
        builds = read("builds.json")?.let { Json.parseList(it) { o -> Json.build(o) } } ?: emptyList()
        customTags = read("tags.json")?.let { t ->
            runCatching { org.json.JSONArray(t).let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrNull()
        } ?: emptyList()
        groundRules = read("ground.json")?.let { Json.parseList(it) { o -> Json.ground(o) } }
            ?.takeIf { it.isNotEmpty() }?.let { withNewGroundRules(it) } ?: DEFAULT_GROUND_RULES
        markGroundSeen()
        wfmRules = read("wfm_rules.json")?.let { Json.parseList(it) { o -> Json.ground(o) } } ?: DEFAULT_WFM_RULES
        settings = read("settings.json")?.let { runCatching { Json.settings(JSONObject(it)) }.getOrNull() } ?: Settings()
        chats = chatDir.listFiles().orEmpty().filter { it.name.endsWith(".json") }.mapNotNull { f ->
            runCatching { Json.chat(JSONObject(f.readText())) }.getOrNull()
        }
        activeChats = prefs.all.filterKeys { it.startsWith("active_") }
            .mapKeys { it.key.removePrefix("active_") }.mapValues { it.value.toString() }
        hasApiKey = prefs.contains("apiKey")
        hasBackupKey = prefs.contains("backupKey")
        hasVoiceKey = prefs.contains("voiceKey")
        migrateLegacyUser()
        addStarterPresets()
    }

    /** Adds built-in ground rules introduced after you edited your copy, at their usual position. */
    private fun withNewGroundRules(saved: List<GroundRule>): List<GroundRule> {
        // Only built-in rules you've never had are added, so renamed or deleted ones don't come back.
        val seen = prefs.getStringSet("groundSeen", null)
        val out = saved.toMutableList()
        DEFAULT_GROUND_RULES.forEachIndexed { i, d ->
            val isNew = if (seen != null) d.title !in seen else out.none { it.title == d.title }
            if (isNew && out.none { it.title == d.title }) out.add(i.coerceAtMost(out.size), d)
        }
        return out
    }

    private fun markGroundSeen() {
        prefs.edit().putStringSet("groundSeen", DEFAULT_GROUND_RULES.map { it.title }.toSet()).apply()
    }

    /** Older builds had one "your character" in Settings; it becomes your first persona. */
    private fun migrateLegacyUser() {
        val s = settings
        if (personas.isEmpty() && s.legacyUserName.isNotBlank()) {
            val p = Persona(name = s.legacyUserName, description = s.legacyUserDescription)
            personas = listOf(p); savePersonas()
            updateSettings(s.copy(defaultPersonaId = p.id, legacyUserName = "", legacyUserDescription = ""))
        }
    }

    /**
     * Presets were removed: every rule is now a general rule you switch on or off, applying to all chats.
     * Rules from older presets are moved into that list (only the explicit-scene ones start switched on).
     */
    private fun addStarterPresets() {
        if (presets.isNotEmpty()) {
            rules = rules + presets.flatMap { p ->
                p.rules.filter { it.isNotBlank() }.map { Rule(label = p.name, text = it, enabled = p.name == "Explicit scene") }
            }
            presets = emptyList()
            saveRules()
            File(dir, "presets.json").delete()
        } else if (!prefs.getBoolean("starterPresets", false) && rules.isEmpty()) {
            rules = listOf(
                Rule(label = "Explicit scene", text = "This scene is intimate. Write it explicitly and without fading to black."),
                Rule(label = "Explicit scene", text = "Slow the pacing down and stay in the moment, describing each touch and reaction."),
                Rule(label = "Explicit scene", text = "Keep dialogue in the scene; let the characters talk to each other."),
                Rule(label = "Explicit scene", text = "Stay true to each character's personality, even in intimacy."),
                Rule(label = "Slow burn", text = "Build tension slowly through glances, near-touches and charged dialogue.", enabled = false),
                Rule(label = "Slow burn", text = "Do not rush to a kiss or anything physical; let it simmer.", enabled = false),
                Rule(label = "Slow burn", text = "Keep the attraction mostly in subtext and inner thoughts.", enabled = false),
                Rule(label = "Short and punchy", text = "Favour dialogue over description.", enabled = false),
                Rule(label = "Short and punchy", text = "Keep paragraphs short and the pace quick.", enabled = false),
            )
            saveRules()
        }
        prefs.edit().putBoolean("starterPresets", true).apply()
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
    private fun savePresets() = write(File(dir, "presets.json"), Json.list(presets) { Json.preset(it) })
    private fun savePersonas() = write(File(dir, "personas.json"), Json.list(personas) { Json.persona(it) })
    private fun saveBuilds() = write(File(dir, "builds.json"), Json.list(builds) { Json.build(it) })

    fun build(id: String?): BuildSession? = id?.let { bid -> builds.firstOrNull { it.id == bid } }

    fun saveBuild(b: BuildSession) {
        val updated = b.copy(updatedAt = System.currentTimeMillis())
        builds = listOf(updated) + builds.filter { it.id != b.id }
        saveBuilds()
    }

    fun deleteBuild(id: String) { builds = builds.filter { it.id != id }; saveBuilds() }

    private fun saveIdeas() = write(File(dir, "ideas.json"), Json.list(ideas) { Json.idea(it) })

    fun saveIdea(i: Idea) { if (ideas.none { it.id == i.id }) { ideas = listOf(i) + ideas; saveIdeas() } }
    fun deleteIdea(id: String) { ideas = ideas.filter { it.id != id }; saveIdeas() }

    private fun saveCustomTags() = write(File(dir, "tags.json"), org.json.JSONArray(customTags).toString())

    fun addCustomTag(tag: String) {
        if (tag !in customTags) { customTags = customTags + tag; saveCustomTags() }
    }

    fun removeCustomTag(tag: String) { customTags = customTags - tag; saveCustomTags() }

    /** Tags beyond the built-in suggestions: ones you created, plus any already used on a character. */
    fun extraTags(category: String): List<String> {
        val builtIn = Tags.categories.firstOrNull { it.name == category }?.suggestions.orEmpty()
        return (customTags + characters.flatMap { it.tags } + ideas.flatMap { it.tags })
            .filter { Tags.category(it) == category && Tags.name(it) !in builtIn }
            .map { Tags.name(it) }.distinct()
    }

    /** Every tag used by any character, for the library's tag filter. */
    val allTags: List<String> get() = characters.flatMap { it.tags }.distinct()

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

    /** A dark colour taken from a photo, used to tint that character's chat. */
    fun computeTint(path: String?): Int? = runCatching {
        if (path == null) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 300 || bounds.outHeight / sample > 300) sample *= 2
        val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val p = Palette.from(bmp).generate()
        val sw = p.mutedSwatch ?: p.darkMutedSwatch ?: p.darkVibrantSwatch ?: p.dominantSwatch ?: return null
        val hsv = FloatArray(3)
        Color.colorToHSV(sw.rgb, hsv)
        hsv[1] = hsv[1].coerceAtMost(0.55f)
        hsv[2] = hsv[2].coerceIn(0.28f, 0.42f)
        Color.HSVToColor(hsv)
    }.getOrNull()

    // ---------- API keys ----------

    fun apiKey(): String = prefs.getString("apiKey", null)?.let { KeyVault.decrypt(it) }.orEmpty()
    fun backupKey(): String = prefs.getString("backupKey", null)?.let { KeyVault.decrypt(it) }.orEmpty()

    fun setApiKey(key: String) {
        if (key.isBlank()) prefs.edit().remove("apiKey").apply()
        else prefs.edit().putString("apiKey", KeyVault.encrypt(key.trim())).apply()
        hasApiKey = key.isNotBlank()
    }

    fun setBackupKey(key: String) {
        if (key.isBlank()) prefs.edit().remove("backupKey").apply()
        else prefs.edit().putString("backupKey", KeyVault.encrypt(key.trim())).apply()
        hasBackupKey = key.isNotBlank()
    }

    fun voiceKey(): String = prefs.getString("voiceKey", null)?.let { KeyVault.decrypt(it) }.orEmpty()

    fun setVoiceKey(key: String) {
        if (key.isBlank()) prefs.edit().remove("voiceKey").apply()
        else prefs.edit().putString("voiceKey", KeyVault.encrypt(key.trim())).apply()
        hasVoiceKey = key.isNotBlank()
    }

    val voiceReady: Boolean
        get() = hasVoiceKey && settings.voiceBaseUrl.isNotBlank() && settings.voiceModel.isNotBlank()

    val backupReady: Boolean
        get() = hasBackupKey && settings.backupBaseUrl.isNotBlank() && settings.backupModel.isNotBlank()

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

    /** Pins one character to the top of the library (null unpins). */
    fun setPinned(id: String?) = updateSettings(settings.copy(pinnedCharacterId = id))

    fun messageCount(characterId: String): Int =
        chats.filter { it.characterId == characterId }.sumOf { it.messages.size }

    fun lastActivity(characterId: String): Long =
        chats.filter { it.characterId == characterId }.maxOfOrNull { it.updatedAt } ?: 0L

    // ---------- personas ----------

    fun persona(id: String?): Persona? = id?.let { pid -> personas.firstOrNull { it.id == pid } }

    fun savePersona(p: Persona) {
        val old = persona(p.id)
        if (old != null && old.avatar != p.avatar) deleteImage(old.avatar)
        personas = if (old == null) personas + p else personas.map { if (it.id == p.id) p else it }
        savePersonas()
        if (settings.defaultPersonaId == null) updateSettings(settings.copy(defaultPersonaId = p.id))
    }

    fun deletePersona(id: String) {
        persona(id)?.let { deleteImage(it.avatar) }
        personas = personas.filter { it.id != id }
        savePersonas()
        if (settings.defaultPersonaId == id) updateSettings(settings.copy(defaultPersonaId = personas.firstOrNull()?.id))
    }

    /** The persona a chat uses: its own choice, else the character's default, else your default. */
    fun personaFor(chat: Chat?, character: Character?): Persona? =
        persona(chat?.personaId) ?: persona(character?.personaId) ?: persona(settings.defaultPersonaId)

    // ---------- chats ----------

    fun chat(id: String): Chat? = chats.firstOrNull { it.id == id }

    fun chatsFor(characterId: String): List<Chat> =
        chats.filter { it.characterId == characterId }.sortedByDescending { it.updatedAt }

    /** The chat that opens for a character: the one you last picked, else the newest. */
    fun activeChat(characterId: String): Chat? =
        activeChats[characterId]?.let { chat(it) } ?: chatsFor(characterId).firstOrNull()

    fun setActiveChat(characterId: String, chatId: String) {
        activeChats = activeChats + (characterId to chatId)
        prefs.edit().putString("active_$characterId", chatId).apply()
    }

    fun newChat(c: Character): Chat {
        val openings = c.openings.map { it.trim() }.filter { it.isNotEmpty() }
        val chat = Chat(
            characterId = c.id,
            replyWords = c.replyWords,
            useBackup = c.useBackup,
            autoSwitch = settings.autoSwitchDefault,
            messages = if (openings.isEmpty()) emptyList()
            else listOf(Message(fromUser = false, versions = openings)),
        )
        putChat(chat, persist = true)
        setActiveChat(c.id, chat.id)
        return chat
    }

    fun deleteChat(id: String) {
        val c = chat(id) ?: return
        File(chatDir, "$id.json").delete()
        chats = chats.filter { it.id != id }
        if (activeChats[c.characterId] == id) {
            activeChats = activeChats - c.characterId
            prefs.edit().remove("active_${c.characterId}").apply()
        }
    }

    /** Replaces a chat in memory; persist = false while a reply is still streaming in. */
    fun putChat(chat: Chat, persist: Boolean = true, touch: Boolean = true) {
        val updated = if (touch) chat.copy(updatedAt = System.currentTimeMillis()) else chat
        chats = if (chats.any { it.id == chat.id }) chats.map { if (it.id == chat.id) updated else it }
        else chats + updated
        if (persist) persistChat(chat.id)
    }

    fun updateChat(id: String, persist: Boolean = true, f: (Chat) -> Chat) {
        val c = chat(id) ?: return
        putChat(f(c), persist)
    }

    fun persistChat(id: String) {
        val c = chat(id) ?: return
        write(File(chatDir, c.id + ".json"), Json.chat(c).toString())
    }

    fun updateMessage(chatId: String, messageId: String, persist: Boolean, f: (Message) -> Message) {
        updateChat(chatId, persist) { c -> c.copy(messages = c.messages.map { if (it.id == messageId) f(it) else it }) }
    }

    // ---------- rules, presets & settings ----------

    fun updateRules(list: List<Rule>) { rules = list; saveRules() }
    fun updatePresets(list: List<Preset>) { presets = list; savePresets() }
    fun preset(id: String?): Preset? = id?.let { pid -> presets.firstOrNull { it.id == pid } }
    fun updateGroundRules(list: List<GroundRule>) { groundRules = list; saveGround() }
    fun resetGroundRules() = updateGroundRules(DEFAULT_GROUND_RULES)
    fun updateWfmRules(list: List<GroundRule>) {
        wfmRules = list
        write(File(dir, "wfm_rules.json"), Json.list(list) { Json.ground(it) })
    }
    fun resetWfmRules() = updateWfmRules(DEFAULT_WFM_RULES)
    fun updateSettings(s: Settings) { settings = s; saveSettings() }

    // ---------- backup & restore ----------

    private fun flushWrites() {
        writer.shutdown()
        writer.awaitTermination(10, TimeUnit.SECONDS)
        writer = Executors.newSingleThreadExecutor()
    }

    /** Writes characters, chats, photos, personas, rules and settings into one zip. API keys are not included. */
    fun exportTo(context: Context, uri: Uri) {
        flushWrites()
        context.contentResolver.openOutputStream(uri)!!.use { out ->
            ZipOutputStream(out).use { zip ->
                dir.walkTopDown().filter { it.isFile && !it.name.endsWith(".tmp") }.forEach { f ->
                    zip.putNextEntry(ZipEntry(f.relativeTo(dir).path))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    /** Replaces everything in the app with the contents of a backup zip made by [exportTo]. */
    fun importFrom(context: Context, uri: Uri) {
        flushWrites()
        val staging = File(dir.parentFile, "ember_restore").apply { deleteRecursively(); mkdirs() }
        val root = staging.canonicalPath + File.separator
        var sawData = false
        context.contentResolver.openInputStream(uri)!!.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    val target = File(staging, e.name)
                    if (!target.canonicalPath.startsWith(root)) continue
                    if (e.isDirectory) { target.mkdirs(); continue }
                    target.parentFile?.mkdirs()
                    target.outputStream().use { zip.copyTo(it) }
                    if (e.name == "characters.json") sawData = true
                }
            }
        }
        require(sawData) { "That file isn't an Ember backup." }
        dir.deleteRecursively()
        staging.renameTo(dir)
        chatDir = File(dir, "chats").apply { mkdirs() }
        imageDir = File(dir, "images").apply { mkdirs() }
        prefs.edit().apply { prefs.all.keys.filter { it.startsWith("active_") }.forEach { remove(it) } }.apply()
    }

    /** Reloads everything from disk (call on the main thread after a restore). */
    fun reload() = load()
}
