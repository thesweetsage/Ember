package app.ember.data

import org.json.JSONArray
import org.json.JSONObject

/** Plain org.json (built into Android) conversions, so no serialization plugin is needed. */
object Json {

    private fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key, "")
    private fun JSONObject.strOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).ifEmpty { null }

    fun character(c: Character): JSONObject = JSONObject()
        .put("id", c.id).put("name", c.name).put("description", c.description)
        .put("personality", c.personality).put("background", c.background)
        .put("opening", c.opening).put("example", c.example)
        .put("avatar", c.avatar ?: JSONObject.NULL).put("chatBg", c.chatBg ?: JSONObject.NULL)
        .put("replyWords", c.replyWords).put("favourite", c.favourite).put("createdAt", c.createdAt)

    fun character(o: JSONObject) = Character(
        id = o.str("id"), name = o.str("name"), description = o.str("description"),
        personality = o.str("personality"), background = o.str("background"),
        opening = o.str("opening"), example = o.str("example"),
        avatar = o.strOrNull("avatar"), chatBg = o.strOrNull("chatBg"),
        replyWords = o.optInt("replyWords", 300), favourite = o.optBoolean("favourite", false),
        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
    )

    fun message(m: Message): JSONObject = JSONObject()
        .put("id", m.id).put("fromUser", m.fromUser).put("sel", m.sel)
        .put("versions", JSONArray(m.versions))

    fun message(o: JSONObject): Message {
        val arr = o.optJSONArray("versions") ?: JSONArray()
        val versions = (0 until arr.length()).map { arr.optString(it, "") }.ifEmpty { listOf("") }
        return Message(
            id = o.str("id"), fromUser = o.optBoolean("fromUser", false),
            versions = versions, sel = o.optInt("sel", 0).coerceIn(0, versions.size - 1),
        )
    }

    fun chat(c: Chat): JSONObject = JSONObject()
        .put("id", c.id).put("characterId", c.characterId).put("replyWords", c.replyWords)
        .put("updatedAt", c.updatedAt)
        .put("messages", JSONArray().apply { c.messages.forEach { put(message(it)) } })

    fun chat(o: JSONObject): Chat {
        val arr = o.optJSONArray("messages") ?: JSONArray()
        return Chat(
            id = o.str("id"), characterId = o.str("characterId"),
            messages = (0 until arr.length()).map { message(arr.getJSONObject(it)) },
            replyWords = o.optInt("replyWords", 300),
            updatedAt = o.optLong("updatedAt", 0L),
        )
    }

    fun rule(r: Rule): JSONObject = JSONObject()
        .put("id", r.id).put("label", r.label).put("text", r.text).put("enabled", r.enabled)

    fun rule(o: JSONObject) = Rule(
        id = o.str("id"), label = o.str("label"), text = o.str("text"),
        enabled = o.optBoolean("enabled", true),
    )

    fun ground(g: GroundRule): JSONObject = JSONObject().put("title", g.title).put("text", g.text)
    fun ground(o: JSONObject) = GroundRule(o.str("title"), o.str("text"))

    fun settings(s: Settings): JSONObject = JSONObject()
        .put("baseUrl", s.baseUrl).put("model", s.model).put("temperature", s.temperature)
        .put("userName", s.userName).put("userDescription", s.userDescription)

    fun settings(o: JSONObject) = Settings(
        baseUrl = o.str("baseUrl").ifBlank { Settings().baseUrl },
        model = o.str("model").ifBlank { Settings().model },
        temperature = o.optDouble("temperature", 1.0),
        userName = o.str("userName"),
        userDescription = o.str("userDescription"),
    )

    fun <T> list(items: List<T>, f: (T) -> JSONObject): String =
        JSONArray().apply { items.forEach { put(f(it)) } }.toString()

    fun <T> parseList(text: String, f: (JSONObject) -> T): List<T> {
        val arr = JSONArray(text)
        return (0 until arr.length()).mapNotNull { runCatching { f(arr.getJSONObject(it)) }.getOrNull() }
    }
}
