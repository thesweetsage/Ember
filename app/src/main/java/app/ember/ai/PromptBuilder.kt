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
    private const val HISTORY_BUDGET_CHARS = 90_000

    private fun trimHistory(chat: Chat, history: List<Message>): List<Message> {
        val skip = chat.coveredUntil.coerceAtMost(maxOf(0, history.size - 4))
        val recent = history.drop(skip)
        val kept = ArrayList<Message>()
        var used = 0
        for (m in recent.asReversed()) {
            if (kept.isNotEmpty() && used + m.text.length > HISTORY_BUDGET_CHARS) break
            kept.add(m); used += m.text.length
        }
        kept.reverse()
        return kept
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
            append("Write $charName's next reply now, following every ground rule. ")
            append("Stay exactly true to $charName as the character card describes them. ")
            p.character.personality.trim().takeIf { it.isNotEmpty() }?.let {
                append("Reminder of who $charName is: ").append(fill(it.take(700)))
                append(if (it.length > 700) "… " else " ")
            }
            append("Dialogue for this reply: make it sound like real people actually talking, never a written speech. Most exchanges are brief and plain; people deflect, joke, trail off, interrupt each other and say less than they mean. When the moment really calls for it, like a confession, a fight or someone finally opening up, a character can talk for longer, but it must still sound spoken: simple words, restarts, pauses, hesitations and small physical beats breaking it up, never a polished, perfectly structured monologue or an eloquent essay about their feelings. Let the other person's presence and reactions be felt between the lines. ")
            if (replyWords <= 0) {
                append("Choose the length yourself to fit the moment: usually around 200 to 300 words, and longer ")
                append("(up to about 700 words) for important, emotional or intimate scenes that need room for detail and inner thoughts. ")
            } else {
                append("Aim for about $replyWords words and no more than ${replyWords + replyWords / 5}. ")
            }
            append("Format it like a page from a novel: split it into short paragraphs separated by a blank line, starting a new paragraph whenever the action, the speaker or the inner thought shifts. Never write one solid block of text. ")
            append("End on a complete sentence.")
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
            append("Write $userName's next reply now, following every ground rule and every rule for writing as $userName, as polished novel prose. ")
            p.persona?.description?.trim()?.takeIf { it.isNotEmpty() }?.let {
                append("Stay exactly true to who $userName is: ").append(fill(it.take(700)))
                append(if (it.length > 700) "… " else " ")
            }
            append("Dialogue for this reply: make it sound like real people actually talking, never a written speech. Most exchanges are brief and plain; people deflect, joke, trail off, interrupt each other and say less than they mean. When the moment really calls for it, like a confession, a fight or someone finally opening up, a character can talk for longer, but it must still sound spoken: simple words, restarts, pauses, hesitations and small physical beats breaking it up, never a polished, perfectly structured monologue or an eloquent essay about their feelings. Let the other person's presence and reactions be felt between the lines. ")
            if (notes.isNotBlank()) {
                append("Base it on these notes from the user. Include every action and line of dialogue they mention, ")
                append("in a sensible order, and expand them into full writing without adding big actions or moves they didn't ask for:\n")
                append(notes.trim()).append("\n")
            } else {
                append("Decide what $userName does and says next, true to their character and to the scene.\n")
            }
            if (!draft.isNullOrBlank() && !change.isNullOrBlank()) {
                append("\nHere is the current draft:\n").append(draft.trim()).append("\n")
                append("Rewrite it with this change: ").append(change.trim())
                append(". Keep everything else unless the change says otherwise.\n")
            }
            if (words <= 0) {
                append("Choose the length yourself to fit the notes and the moment: usually around 200 to 300 words, and longer ")
                append("(up to about 600 words) for important scenes that need detail and inner thoughts. ")
            } else {
                append("Aim for about $words words. ")
            }
            append("Format it like a page from a novel: split it into short paragraphs separated by a blank line, starting a new paragraph whenever the action, the speaker or the inner thought shifts. Never write one solid block of text. ")
            append("Only write $userName's reply, nothing else. End on a complete sentence.")
        })
        return out
    }

    /** Asks for a short summary of one block of the chat. */
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
    fun maxTokens(words: Int): Int = if (words <= 0) 1800 else (words * 2.2).toInt().coerceIn(120, 8000)
}
