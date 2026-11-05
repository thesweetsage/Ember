package app.ember.data

import org.json.JSONArray
import org.json.JSONObject

/** Plain org.json (built into Android) conversions. Missing fields fall back to defaults, so old files still load. */
object Json {

    private fun JSONObject.str(key: String): String = if (!has(key) || isNull(key)) "" else optString(key, "")
    private fun JSONObject.strOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).ifEmpty { null }
    private fun JSONObject.strings(key: String): List<String> {
        val arr = optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).map { arr.optString(it, "") }
    }
    private fun <T> JSONObject.objects(key: String, f: (JSONObject) -> T): List<T> {
        val arr = optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { runCatching { f(it) }.getOrNull() } }
    }
    private fun <T> arr(items: List<T>, f: (T) -> Any): JSONArray = JSONArray().apply { items.forEach { put(f(it)) } }
    private fun nul(v: Any?): Any = v ?: JSONObject.NULL

    // ----- character -----
    fun character(c: Character): JSONObject = JSONObject()
        .put("id", c.id).put("name", c.name).put("description", c.description)
        .put("personality", c.personality).put("background", c.background)
        .put("openings", JSONArray(c.openings)).put("example", c.example)
        .put("avatar", nul(c.avatar)).put("chatBg", nul(c.chatBg))
        .put("replyWords", c.replyWords).put("personaId", nul(c.personaId)).put("useBackup", c.useBackup)
        .put("tags", JSONArray(c.tags)).put("tint", nul(c.tint))
        .put("favourite", c.favourite).put("archived", c.archived).put("createdAt", c.createdAt)

    fun character(o: JSONObject): Character {
        val openings = o.strings("openings").ifEmpty { listOf(o.str("opening")) }
        return Character(
            id = o.str("id"), name = o.str("name"), description = o.str("description"),
            personality = o.str("personality"), background = o.str("background"),
            openings = openings, example = o.str("example"),
            avatar = o.strOrNull("avatar"), chatBg = o.strOrNull("chatBg"),
            replyWords = o.optInt("replyWords", 300), personaId = o.strOrNull("personaId"),
            useBackup = o.optBoolean("useBackup", false),
            tags = if (o.optJSONArray("tags") != null) o.strings("tags") else Tags.fromLegacy(o.str("tags")),
            tint = if (o.has("tint") && !o.isNull("tint")) o.optInt("tint") else null,
            favourite = o.optBoolean("favourite", false), archived = o.optBoolean("archived", false),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        )
    }

    // ----- message / chat -----
    fun message(m: Message): JSONObject = JSONObject()
        .put("id", m.id).put("fromUser", m.fromUser).put("sel", m.sel)
        .put("versions", JSONArray(m.versions))

    fun message(o: JSONObject): Message {
        val versions = o.strings("versions").ifEmpty { listOf("") }
        return Message(
            id = o.str("id"), fromUser = o.optBoolean("fromUser", false),
            versions = versions, sel = o.optInt("sel", 0).coerceIn(0, versions.size - 1),
        )
    }

    fun chat(c: Chat): JSONObject = JSONObject()
        .put("id", c.id).put("characterId", c.characterId).put("replyWords", c.replyWords)
        .put("personaId", nul(c.personaId)).put("presetId", nul(c.presetId)).put("direction", c.direction)
        .put("pins", arr(c.pins) { JSONObject().put("id", it.id).put("text", it.text) })
        .put("summaries", arr(c.summaries) {
            JSONObject().put("id", it.id).put("from", it.from).put("to", it.to).put("text", it.text)
        })
        .put("temperature", nul(c.temperature)).put("useBackup", c.useBackup).put("autoSwitch", c.autoSwitch)
        .put("tintFromPhoto", c.tintFromPhoto).put("bubbleOpacity", c.bubbleOpacity.toDouble())
        .put("textSize", c.textSize).put("tokensIn", c.tokensIn).put("tokensOut", c.tokensOut)
        .put("createdAt", c.createdAt).put("updatedAt", c.updatedAt)
        .put("messages", arr(c.messages) { message(it) })

    fun chat(o: JSONObject) = Chat(
        id = o.str("id"), characterId = o.str("characterId"),
        messages = o.objects("messages") { message(it) },
        replyWords = o.optInt("replyWords", 300),
        personaId = o.strOrNull("personaId"), presetId = o.strOrNull("presetId"),
        direction = o.str("direction"),
        pins = o.objects("pins") { Pin(it.str("id").ifEmpty { newId() }, it.str("text")) },
        summaries = o.objects("summaries") {
            Summary(it.str("id").ifEmpty { newId() }, it.optInt("from"), it.optInt("to"), it.str("text"))
        },
        temperature = if (o.has("temperature") && !o.isNull("temperature")) o.optDouble("temperature") else null,
        useBackup = o.optBoolean("useBackup", false), autoSwitch = o.optBoolean("autoSwitch", true),
        tintFromPhoto = o.optBoolean("tintFromPhoto", true),
        bubbleOpacity = o.optDouble("bubbleOpacity", 0.85).toFloat(),
        textSize = o.optInt("textSize", 16),
        tokensIn = o.optLong("tokensIn", 0), tokensOut = o.optLong("tokensOut", 0),
        createdAt = o.optLong("createdAt", o.optLong("updatedAt", 0L)),
        updatedAt = o.optLong("updatedAt", 0L),
    )

    // ----- rules, presets, personas -----
    fun rule(r: Rule): JSONObject = JSONObject()
        .put("id", r.id).put("label", r.label).put("text", r.text).put("enabled", r.enabled)

    fun rule(o: JSONObject) = Rule(
        id = o.str("id"), label = o.str("label"), text = o.str("text"), enabled = o.optBoolean("enabled", true),
    )

    fun preset(p: Preset): JSONObject = JSONObject().put("id", p.id).put("name", p.name).put("rules", JSONArray(p.rules))
    fun preset(o: JSONObject) = Preset(o.str("id"), o.str("name"), o.strings("rules"))

    fun persona(p: Persona): JSONObject = JSONObject()
        .put("id", p.id).put("name", p.name).put("description", p.description).put("avatar", nul(p.avatar))
    fun persona(o: JSONObject) = Persona(o.str("id"), o.str("name"), o.str("description"), o.strOrNull("avatar"))

    fun idea(i: Idea): JSONObject = JSONObject().put("id", i.id).put("text", i.text).put("tags", JSONArray(i.tags)).put("createdAt", i.createdAt)
    fun idea(o: JSONObject) = Idea(o.str("id"), o.str("text"), o.strings("tags"), o.optLong("createdAt", 0L))

    fun build(b: BuildSession): JSONObject = JSONObject()
        .put("id", b.id).put("updatedAt", b.updatedAt).put("tags", JSONArray(b.tags))
        .put("messages", arr(b.messages) { JSONObject().put("fromUser", it.fromUser).put("text", it.text) })
        .put("draft", JSONObject().apply { b.draft.forEach { (k, v) -> put(k, v) } })

    fun build(o: JSONObject): BuildSession {
        val d = o.optJSONObject("draft")
        val draft = d?.keys()?.asSequence()?.associateWith { d.optString(it, "") }.orEmpty()
        return BuildSession(
            id = o.str("id"),
            messages = o.objects("messages") { BuildMsg(it.optBoolean("fromUser"), it.str("text")) },
            draft = draft, tags = o.strings("tags"), updatedAt = o.optLong("updatedAt", 0L),
        )
    }

    fun ground(g: GroundRule): JSONObject = JSONObject().put("title", g.title).put("text", g.text)
    fun ground(o: JSONObject) = GroundRule(o.str("title"), o.str("text"))

    // ----- settings -----
    fun settings(s: Settings): JSONObject = JSONObject()
        .put("baseUrl", s.baseUrl).put("model", s.model).put("temperature", s.temperature)
        .put("backupBaseUrl", s.backupBaseUrl).put("backupModel", s.backupModel)
        .put("autoSwitchDefault", s.autoSwitchDefault).put("summariseEvery", s.summariseEvery)
        .put("writeForMeWords", s.writeForMeWords).put("defaultPersonaId", nul(s.defaultPersonaId))
        .put("pinnedCharacterId", nul(s.pinnedCharacterId))
        .put("voiceBaseUrl", s.voiceBaseUrl).put("voiceModel", s.voiceModel).put("voiceLanguage", s.voiceLanguage).put("contextChars", s.contextChars).put("wfmUseBackup", s.wfmUseBackup)
        .put("priceIn", s.priceIn).put("priceOut", s.priceOut)
        .put("legacyUserName", s.legacyUserName).put("legacyUserDescription", s.legacyUserDescription)

    fun settings(o: JSONObject): Settings {
        val d = Settings()
        return Settings(
            baseUrl = o.str("baseUrl").ifBlank { d.baseUrl },
            model = o.str("model").ifBlank { d.model },
            temperature = o.optDouble("temperature", 1.0),
            backupBaseUrl = o.str("backupBaseUrl"), backupModel = o.str("backupModel"),
            autoSwitchDefault = o.optBoolean("autoSwitchDefault", true),
            summariseEvery = o.optInt("summariseEvery", 30),
            writeForMeWords = o.optInt("writeForMeWords", 100),
            defaultPersonaId = o.strOrNull("defaultPersonaId"),
            pinnedCharacterId = o.strOrNull("pinnedCharacterId"),
            voiceBaseUrl = o.str("voiceBaseUrl").ifBlank { d.voiceBaseUrl },
            voiceModel = o.str("voiceModel").ifBlank { d.voiceModel },
            voiceLanguage = if (o.has("voiceLanguage")) o.str("voiceLanguage") else d.voiceLanguage,
            contextChars = o.optInt("contextChars", d.contextChars),
            wfmUseBackup = o.optBoolean("wfmUseBackup", true),
            priceIn = o.optDouble("priceIn", 0.0), priceOut = o.optDouble("priceOut", 0.0),
            // Older versions stored "userName"/"userDescription"; they become your first persona.
            legacyUserName = o.str("legacyUserName").ifEmpty { o.str("userName") },
            legacyUserDescription = o.str("legacyUserDescription").ifEmpty { o.str("userDescription") },
        )
    }

    fun <T> list(items: List<T>, f: (T) -> JSONObject): String =
        JSONArray().apply { items.forEach { put(f(it)) } }.toString()

    fun <T> parseList(text: String, f: (JSONObject) -> T): List<T> {
        val arr = JSONArray(text)
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { runCatching { f(it) }.getOrNull() }
        }
    }
}
