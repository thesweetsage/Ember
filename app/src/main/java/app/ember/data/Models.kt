package app.ember.data

import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

data class Character(
    val id: String = newId(),
    val name: String = "",
    val description: String = "",
    val personality: String = "",
    val background: String = "",
    /** First entry is the main opening; the rest are alternates you can swipe between. */
    val openings: List<String> = listOf(""),
    val example: String = "",
    val avatar: String? = null,
    val chatBg: String? = null,
    val replyWords: Int = 300,
    val personaId: String? = null,
    val useBackup: Boolean = false,
    /** Tags as "Category::Name"; unlimited. */
    val tags: List<String> = emptyList(),
    val tint: Int? = null,
    val favourite: Boolean = false,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val opening: String get() = openings.firstOrNull().orEmpty()
}

data class Message(
    val id: String = newId(),
    val fromUser: Boolean,
    val versions: List<String>,
    val sel: Int = 0,
) {
    val text: String get() = versions.getOrElse(sel) { "" }
}

data class Pin(val id: String = newId(), val text: String)

/** Auto summary of messages [from, to) of a chat. */
data class Summary(val id: String = newId(), val from: Int, val to: Int, val text: String)

data class Chat(
    val id: String = newId(),
    val characterId: String,
    val messages: List<Message> = emptyList(),
    val replyWords: Int = 300,
    val personaId: String? = null,
    val presetId: String? = null,
    val direction: String = "",
    val pins: List<Pin> = emptyList(),
    val summaries: List<Summary> = emptyList(),
    val temperature: Double? = null,
    val useBackup: Boolean = false,
    val autoSwitch: Boolean = true,
    val tintFromPhoto: Boolean = true,
    val bubbleOpacity: Float = 0.85f,
    val textSize: Int = 16,
    val tokensIn: Long = 0,
    val tokensOut: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    /** Number of messages already folded into summaries. */
    val coveredUntil: Int get() = summaries.maxOfOrNull { it.to } ?: 0
}

data class Rule(
    val id: String = newId(),
    val label: String,
    val text: String,
    val enabled: Boolean = true,
)

data class BuildMsg(val fromUser: Boolean, val text: String)

/** An AI character-builder conversation, saved as you go so it can be continued later. */
data class BuildSession(
    val id: String = newId(),
    val messages: List<BuildMsg> = emptyList(),
    val draft: Map<String, String> = emptyMap(),
    val tags: List<String> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val title: String
        get() = draft["Name"]?.lines()?.firstOrNull()?.trim()?.trim('*', '"')?.ifBlank { null }
            ?: messages.firstOrNull { it.fromUser }?.text?.replace("\n", " ")?.take(60)
            ?: "Untitled build"
}

/** A saved character concept from the Ideas tab. */
data class Idea(val id: String = newId(), val text: String, val tags: List<String> = emptyList(), val createdAt: Long = System.currentTimeMillis())

/** A named bundle of rules you switch on per chat. */
data class Preset(val id: String = newId(), val name: String, val rules: List<String>)

data class Persona(
    val id: String = newId(),
    val name: String,
    val description: String = "",
    val avatar: String? = null,
)

data class Settings(
    val baseUrl: String = "https://api.deepseek.com",
    val model: String = "deepseek-chat",
    val temperature: Double = 1.0,
    val backupBaseUrl: String = "",
    val backupModel: String = "",
    val autoSwitchDefault: Boolean = true,
    val summariseEvery: Int = 30,
    val writeForMeWords: Int = 100,
    val defaultPersonaId: String? = null,
    /** The character shown as a wide banner at the top of the library. */
    val pinnedCharacterId: String? = null,
    /** Voice transcription (Whisper-style API). Empty key means the phone's own voice typing is used. */
    val voiceBaseUrl: String = "https://api.groq.com/openai/v1",
    val voiceModel: String = "whisper-large-v3-turbo",
    val voiceLanguage: String = "en",
    /** Recent chat sent with each message: 0 = Light (last ~6 messages + summaries), 30k short, 60k medium, 90k long. */
    val contextChars: Int = 60_000,
    /** Optional prices per million tokens, in euros, for the cost counter. */
    val priceIn: Double = 0.0,
    val priceOut: Double = 0.0,
    /** Kept only to move an old single "your character" into a persona. */
    val legacyUserName: String = "",
    val legacyUserDescription: String = "",
)

/** One built-in writing rule. Title is shown in the app; text is what the model receives. */
data class GroundRule(val title: String, val text: String)

val DEFAULT_GROUND_RULES = listOf(
    GroundRule("Novel style", "Third person, past tense, novel prose. No asterisks or roleplay shorthand; every action is a full sentence."),
    GroundRule("Layout", "Short paragraphs separated by a blank line. {{char}}'s inner thoughts in italics, marked with underscores like _this_."),
    GroundRule("Dialogue", "Dialogue goes in double quotes and sounds like real people talking: plain, natural, sometimes awkward. No speeches or poetic, quotable lines."),
    GroundRule("Show, don't tell", "Most of each reply is body language, reactions, sensation and inner thought. Every action carries feeling."),
    GroundRule("True to the character", "Stay true to {{char}}'s character card and to the story so far. No sudden personality changes or contradictions."),
    GroundRule("The user's character", "Never write {{user}}'s actions, words, thoughts or feelings."),
    GroundRule("Pacing", "Move the scene forward a little and end on a moment {{user}} can respond to. Don't repeat earlier phrasing."),
    GroundRule("Adult fiction", "Private fiction for one adult reader. Mature and explicit content is allowed and written fully, without refusing, censoring, fading to black or adding warnings."),
    GroundRule("Adults only", "Every character is 18 or over. Nothing sexual involving anyone under 18 or described as childlike."),
    GroundRule("Stay in the story", "No notes, disclaimers or out-of-character text."),
)

/**
 * Extra rules used only for "Write for me" drafts. {{persona}} is your character, {{character}} the one you're chatting with.
 */
val DEFAULT_WFM_RULES = listOf(
    GroundRule("Exactly her", "Write {{persona}} exactly as her persona describes her. If she's sweet or shy, she stays that way unless the notes say otherwise."),
    GroundRule("Follow the notes", "Include every action, thought and line from the user's notes, in order, skipping none. Her thoughts in the notes become her inner thoughts. Add only small connecting details; nothing she didn't ask for. Never write {{character}}'s actions, words or reactions."),
)
