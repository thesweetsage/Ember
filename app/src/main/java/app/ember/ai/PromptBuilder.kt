package app.ember.ai

import app.ember.data.Character
import app.ember.data.Chat
import app.ember.data.GroundRule
import app.ember.data.Message
import app.ember.data.Persona
import app.ember.data.Preset
import app.ember.data.Rule

/** Everything a prompt is built from. */
data class PromptContext(
    val character: Character,
    val persona: Persona?,
    val chat: Chat,
    val ground: List<GroundRule>,
    val rules: List<Rule>,
    val preset: Preset?,
    val wfmRules: List<GroundRule> = emptyList(),
)

/**
 * Builds what is sent to the model, in this order:
 * ground rules, your rules, preset rules, character card, your persona, pinned memories,
 * summaries, scene direction, recent chat, then the length instruction.
 */
object PromptBuilder {

    /** Roughly how much recent chat (in characters) is sent with each request. */
    /** How much recent chat (in characters) is sent; set in Settings → Context sent. */
    var historyBudgetChars = 60_000

    /**
     * Keeps the most recent messages that fit the budget. Older messages are dropped 10 at a time, so the
     * start of the request stays identical between replies and the provider's cache discount keeps applying.
     */
    private fun trimHistory(chat: Chat, history: List<Message>): List<Message> {
        val skip = chat.coveredUntil.coerceAtMost(maxOf(0, history.size - 4))
        val recent = history.drop(skip)
        var start = 0
        fun size(from: Int) = recent.subList(from, recent.size).sumOf { it.text.length }
        while (start < recent.size - 2 && size(start) > historyBudgetChars) start += 10
        return recent.subList(start.coerceAtMost(maxOf(0, recent.size - 2)), recent.size)
    }

    private fun StringBuilder.sharedSections(p: PromptContext, fill: (String) -> String) {
        append("# Ground rules (always follow)\n")
        p.ground.forEachIndexed { i, g -> append("${i + 1}. ${fill(g.text)}\n") }

        val active = p.rules.filter { it.enabled && it.text.isNotBlank() }
        if (active.isNotEmpty()) {
            append("\n# Additional rules from the user (always follow)\n")
            active.forEach { append("- ${fill(it.text.trim())}\n") }
        }
        val presetRules = p.preset?.rules.orEmpty().filter { it.isNotBlank() }
        if (presetRules.isNotEmpty()) {
            append("\n# Rules for this chat (${p.preset?.name})\n")
            presetRules.forEach { append("- ${fill(it.trim())}\n") }
        }
    }

    private fun StringBuilder.characterCard(c: Character, heading: String, fill: (String) -> String) {
        append("\n# $heading\n")
        if (c.description.isNotBlank()) append("Summary: ${fill(c.description.trim())}\n\n")
        if (c.personality.isNotBlank()) append("Personality:\n${fill(c.personality.trim())}\n\n")
        if (c.background.isNotBlank()) append("Background:\n${fill(c.background.trim())}\n\n")
        if (c.example.isNotBlank()) append("Example of how ${c.name} writes and speaks:\n${fill(c.example.trim())}\n\n")
        if (c.tags.isNotEmpty()) append("Tags: ${app.ember.data.Tags.describe(c.tags)}\n\n")
    }

    private fun StringBuilder.storySections(chat: Chat, fill: (String) -> String) {
        if (chat.pins.isNotEmpty()) {
            append("\n# Facts to always remember\n")
            chat.pins.forEach { append("- ${fill(it.text.trim())}\n") }
        }
        if (chat.summaries.isNotEmpty()) {
            append("\n# The story so far (earlier parts, summarised)\n")
            chat.summaries.sortedBy { it.from }.forEach { append(fill(it.text.trim())).append("\n\n") }
        }
        if (chat.sceneOn && chat.scene.isNotBlank()) {
            append("\n# Where things stand right now (keep consistent with this)\n")
            append(fill(chat.scene.trim())).append("\n")
        }
        if (chat.direction.isNotBlank()) {
            append("\n# Scene direction from the user (steer the story this way)\n")
            append(fill(chat.direction.trim())).append("\n")
        }
    }

    private fun conversation(system: String, history: List<Message>, flip: Boolean, fill: (String) -> String): ArrayList<ChatMsg> {
        val out = ArrayList<ChatMsg>()
        out.add(ChatMsg("system", system))
        val firstIsModel = history.firstOrNull()?.let { if (flip) it.fromUser else !it.fromUser } ?: false
        if (firstIsModel) out.add(ChatMsg("user", "[The story begins.]"))
        for (m in history) {
            val mine = if (flip) m.fromUser else !m.fromUser
            val role = if (mine) "assistant" else "user"
            val text = fill(m.text)
            val last = out.lastOrNull()
            if (last != null && last.role == role) out[out.size - 1] = ChatMsg(role, last.content + "\n\n" + text)
            else out.add(ChatMsg(role, text))
        }
        return out
    }

    /**
     * Adds this reply's instructions to the end of the latest user message, where the model pays the most
     * attention, rather than as a separate note far from the rules.
     */
    private fun attachInstruction(out: ArrayList<ChatMsg>, instruction: String) {
        val note = "[Instructions for this reply only. Not part of the story; follow them silently.]\n$instruction"
        val last = out.lastOrNull()
        if (last != null && last.role == "user") out[out.size - 1] = ChatMsg("user", last.content + "\n\n" + note)
        else out.add(ChatMsg("user", note))
    }

    /** Splits the user's Write for me notes into numbered beats, one per sentence or line. */
    fun noteBeats(notes: String): List<String> =
        notes.trim().split(Regex("(?<=[.!?])\\s+|\\n+")).map { it.trim().trimEnd('.') }.filter { it.isNotEmpty() }

    /** Readable copy of a prompt, for "See what's sent". */
    fun describe(prompt: List<ChatMsg>): String =
        prompt.joinToString("\n\n────────\n\n") { m -> "[" + m.role.uppercase() + "]\n" + m.content }

    /** The character's next reply. */
    fun forCharacter(p: PromptContext, history: List<Message>, replyWords: Int, extraInstruction: String? = null): List<ChatMsg> {
        val charName = p.character.name.ifBlank { "the character" }
        val userName = p.persona?.name?.ifBlank { null } ?: "the user"
        val fill = { s: String ->
            s.replace("{{char}}", charName).replace("{{user}}", userName)
                .replace("{{character}}", charName).replace("{{persona}}", userName)
        }

        val system = buildString {
            append("You are writing an ongoing interactive story with the user. You write as $charName; the user writes as $userName.\n\n")
            sharedSections(p, fill)
            characterCard(p.character, charName, fill)
            append("\n# $userName (the user's character; never write for them)\n")
            append(p.persona?.description?.trim()?.let(fill)?.ifBlank { null } ?: "No description given.")
            append("\n")
            storySections(p.chat, fill)
        }
        val out = conversation(system, trimHistory(p.chat, history), flip = false, fill = fill)
        attachInstruction(out, buildString {
            append("Write $charName's next reply, following the rules. ")
            p.character.personality.trim().takeIf { it.isNotEmpty() }?.let {
                append("Stay true to who $charName is: ").append(fill(it.take(500))).append(if (it.length > 500) "… " else " ")
            }
            append("Dialogue: plain and natural, only what the moment needs, no speeches or poetic lines. ")
            append("Show $charName's body reacting, what they notice about $userName, and their thoughts. ")
            if (replyWords <= 0) append("Length: fit the moment, usually 200 to 300 words, up to about 450 for big scenes, never more. ")
            else append("Length: about $replyWords words, between ${replyWords * 85 / 100} and ${replyWords * 115 / 100}. Reach it by deepening body language, what $charName notices and their thoughts, not by adding events. ")
            append("Write only $charName's part and stop at the first moment $userName could respond; never write $userName's actions, words or thoughts. ")
            append("Split it into short paragraphs separated by blank lines, never one block of text. End on a complete sentence.")
            if (!extraInstruction.isNullOrBlank()) append("\n").append(extraInstruction)
        })
        return out
    }

    /** "Write for me": the same story, written from the user's side, as their persona. */
    fun forUser(
        p: PromptContext, history: List<Message>, words: Int,
        notes: String, draft: String?, change: String?,
    ): List<ChatMsg> {
        val charName = p.character.name.ifBlank { "the character" }
        val userName = p.persona?.name?.ifBlank { null } ?: "the user's character"
        // Names are swapped so every ground rule now applies to the user's side.
        val fill = { s: String ->
            s.replace("{{char}}", userName).replace("{{user}}", charName)
                .replace("{{character}}", charName).replace("{{persona}}", userName)
        }

        val system = buildString {
            append("You are helping the user write their own part in an ongoing interactive story. ")
            append("You write only as $userName. $charName is played by someone else.\n\n")
            sharedSections(p, fill)
            val wfm = p.wfmRules.filter { it.text.isNotBlank() }
            if (wfm.isNotEmpty()) {
                append("\n# Rules for writing as $userName (always follow)\n")
                wfm.forEach { append("- ${fill(it.text.trim())}\n") }
            }
            append("\n# $userName (the character you write)\n")
            append(p.persona?.description?.trim()?.let(fill)?.ifBlank { null } ?: "No description given; infer from the story.")
            append("\n")
            characterCard(p.character, "$charName (the other character; never write their actions, dialogue or thoughts)", fill)
            storySections(p.chat, fill)
        }
        val out = conversation(system, trimHistory(p.chat, history), flip = true, fill = fill)
        attachInstruction(out, buildString {
            append("Write $userName's next reply, following the rules. ")
            p.persona?.description?.trim()?.takeIf { it.isNotEmpty() }?.let {
                append("Stay true to who $userName is: ").append(fill(it.take(500))).append(if (it.length > 500) "… " else " ")
            }
            if (notes.isNotBlank()) {
                val beats = noteBeats(notes)
                append("Turn ALL of these notes into her reply. Write every beat below, in this order, and skip none: ")
                append("each action happens, each thought becomes her inner thought (keep close to the user's wording), ")
                append("and each line she speaks is said (lightly polished at most). Add only small connecting details ")
                append("such as a breath, a glance or a blush; never new actions, feelings, lines or moves that aren't in the notes.\n")
                beats.forEachIndexed { i, b -> append("${i + 1}. ").append(b).append("\n") }
            } else {
                append("No notes were given, so write a short, simple reply: $userName's natural reaction to exactly what $charName just said and did in the last message. ")
                append("Keep track of what has already happened (who is touching whom, what has already been said or asked) and don't repeat or contradict it. ")
                append("One reaction and at most one or two short lines from her; no new topics, no back-and-forth, no running ahead. ")
            }
            if (!draft.isNullOrBlank() && !change.isNullOrBlank()) {
                append("\nCurrent draft:\n").append(draft.trim()).append("\n")
                append("Rewrite it with this change: ").append(change.trim()).append(". Keep everything else.\n")
            }
            append("Dialogue: plain and natural, no speeches or poetic lines. ")
            if (words <= 0 && notes.isBlank()) append("Length: about 80 to 150 words. ")
            else if (words <= 0) append("Length: whatever it takes to cover every beat in the notes, and no more. Never pad. ")
            else append("Length: about $words words, between ${words * 85 / 100} and ${words * 115 / 100}. Reach it by deepening her body language, what she notices and her thoughts around the beats, never by adding new actions or lines. ")
            append("Write only $userName's part and stop there; never write $charName's actions, words or reactions. ")
            append("Split it into short paragraphs separated by blank lines, never one block of text. End on a complete sentence.")
        })
        return out
    }

    /** Asks for a short summary of one block of the chat. */
    /** Scene tracker: a tiny, cheap request that updates where, when, what they're wearing and the mood. */
    fun forScene(p: PromptContext, current: String, recent: List<Message>): List<ChatMsg> {
        val charName = p.character.name.ifBlank { "the character" }
        val userName = p.persona?.name?.ifBlank { null } ?: "the user"
        val transcript = recent.joinToString("\n\n") { (if (it.fromUser) "$userName: " else "$charName: ") + it.text.take(1500) }
        return listOf(
            ChatMsg(
                "system",
                "You track continuity for a private, fictional story between adults. Using the latest messages, update the scene card. " +
                    "Reply with exactly these four lines and nothing else, each under 20 words, plain and factual:\n" +
                    "Place: ...\nTime: ...\nWearing: $charName: ...; $userName: ...\nMood: ...\n" +
                    "Wearing means what each has on right now, including anything removed. If something isn't known, keep what the old card says.",
            ),
            ChatMsg("user", "Old card:\n${current.ifBlank { "(none yet)" }}\n\nLatest messages:\n$transcript"),
        )
    }

    fun forSummary(p: PromptContext, block: List<Message>): List<ChatMsg> {
        val charName = p.character.name.ifBlank { "the character" }
        val userName = p.persona?.name?.ifBlank { null } ?: "the user"
        val transcript = block.joinToString("\n\n") { (if (it.fromUser) "$userName: " else "$charName: ") + it.text }
        return listOf(
            ChatMsg(
                "system",
                "You keep the memory for a private, fictional story between adults. Summarise the passage you are given in 4 to 7 sentences, " +
                    "third person, past tense. Keep names, key events, decisions, promises, revealed secrets, injuries, " +
                    "locations and how the relationship between $charName and $userName changed. Mention intimate events plainly " +
                    "without detail. No commentary, headings or lists."
            ),
            ChatMsg("user", transcript),
        )
    }

    /** API-side cap, generous enough that the model finishes its last sentence. */
    // Generous on purpose: "thinking" models spend part of this before they write a word.
    // Auto sends no cap at all. A fixed length keeps a generous cap, with room for "thinking" models to reason first.
    /** No token cap for any length: the prompt and the app's word stop keep replies the right size. */
    @Suppress("UNUSED_PARAMETER")
    fun maxTokens(words: Int): Int? = null
}
