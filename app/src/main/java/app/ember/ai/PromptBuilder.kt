package app.ember.ai

import app.ember.data.Character
import app.ember.data.GroundRule
import app.ember.data.Message
import app.ember.data.Rule
import app.ember.data.Settings

/**
 * Builds what is sent to the model, in this order:
 * ground rules, your rules, character card, your character, recent chat, then the length instruction.
 */
object PromptBuilder {

    /** Roughly how much chat history (in characters) is sent with each reply. */
    private const val HISTORY_BUDGET_CHARS = 90_000

    fun build(
        character: Character,
        settings: Settings,
        ground: List<GroundRule>,
        rules: List<Rule>,
        history: List<Message>,
        replyWords: Int,
        extraInstruction: String? = null,
    ): List<ChatMsg> {
        val charName = character.name.ifBlank { "the character" }
        val userName = settings.userName.ifBlank { "the user" }
        fun fill(s: String) = s.replace("{{char}}", charName).replace("{{user}}", userName)

        val system = buildString {
            append("You are writing an ongoing interactive story with the user. You write as $charName.\n\n")

            append("# Ground rules (always follow)\n")
            ground.forEachIndexed { i, g -> append("${i + 1}. ${fill(g.text)}\n") }

            val active = rules.filter { it.enabled && it.text.isNotBlank() }
            if (active.isNotEmpty()) {
                append("\n# Additional rules from the user (always follow)\n")
                active.forEach { append("- ${fill(it.text.trim())}\n") }
            }

            append("\n# $charName\n")
            if (character.description.isNotBlank()) append("Summary: ${fill(character.description.trim())}\n\n")
            if (character.personality.isNotBlank()) append("Personality:\n${fill(character.personality.trim())}\n\n")
            if (character.background.isNotBlank()) append("Background:\n${fill(character.background.trim())}\n\n")
            if (character.example.isNotBlank()) append("Example of how $charName writes and speaks:\n${fill(character.example.trim())}\n\n")

            append("# The user's character\n")
            append("The user plays $userName.")
            if (settings.userDescription.isNotBlank()) append(" ${fill(settings.userDescription.trim())}")
            append("\n")
        }

        // Keep the most recent messages that fit the budget.
        val kept = ArrayList<Message>()
        var used = 0
        for (m in history.asReversed()) {
            val len = m.text.length
            if (kept.isNotEmpty() && used + len > HISTORY_BUDGET_CHARS) break
            kept.add(m); used += len
        }
        kept.reverse()

        val out = ArrayList<ChatMsg>()
        out.add(ChatMsg("system", system))
        if (kept.firstOrNull()?.fromUser == false) {
            out.add(ChatMsg("user", "[The story begins.]"))
        }
        for (m in kept) {
            val role = if (m.fromUser) "user" else "assistant"
            val text = fill(m.text)
            val last = out.lastOrNull()
            if (last != null && last.role == role) out[out.size - 1] = ChatMsg(role, last.content + "\n\n" + text)
            else out.add(ChatMsg(role, text))
        }

        val instruction = buildString {
            append("Write $charName's next reply now, following every ground rule. ")
            append("Aim for about $replyWords words and no more than ${replyWords + replyWords / 5}. ")
            append("End on a complete sentence.")
            if (!extraInstruction.isNullOrBlank()) append("\n").append(extraInstruction)
        }
        out.add(ChatMsg("system", instruction))
        return out
    }

    /** API-side cap, generous enough that the model finishes its last sentence. */
    fun maxTokens(replyWords: Int): Int = (replyWords * 2.2).toInt().coerceIn(120, 8000)
}
