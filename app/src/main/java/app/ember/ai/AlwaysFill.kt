package app.ember.ai

import app.ember.data.Character
import app.ember.data.Store
import app.ember.data.isGroup
import kotlin.coroutines.EmptyCoroutineContext

/** Fills a character's "Always" box from their Personality, as bullet points. */
object AlwaysFill {
    private val running = mutableSetOf<String>()

    fun hasSource(c: Character) = c.personality.isNotBlank() || c.members.any { it.about.isNotBlank() }

    /** For characters made before the box existed: fill it once, in the background. */
    fun fillIfMissing(c: Character) {
        if (c.always.isBlank() && hasSource(c)) refresh(c.id)
    }

    /** Rewrites the box from the current Personality. [onDone] gets the new text (or null if it failed). */
    fun refresh(id: String, onDone: (String?) -> Unit = {}) {
        val c = Store.character(id) ?: return onDone(null)
        if (!hasSource(c)) return onDone(null)
        synchronized(running) { if (!running.add(id)) return onDone(null) }
        app.ember.work.Generation.launchQuiet(EmptyCoroutineContext) {
            var out: String? = null
            try {
                out = generate(c)
                if (out != null) Store.character(id)?.let { Store.saveCharacter(it.copy(always = out)) }
            } finally {
                synchronized(running) { running.remove(id) }
                onDone(out)
            }
        }
    }

    /** The bullets for this character as it is right now (saved or not), or null if nothing usable came back. */
    suspend fun generate(c: Character): String? = try {
        val raw = Ai.complete(prompt(c), null, 0.4, preferBackup = Ai.creativeOnBackup)
        raw.lines().map { it.trim() }
            .filter { it.startsWith("-") || it.startsWith("•") || it.startsWith("*") }
            .map { "- " + it.trimStart('-', '•', '*', ' ').replace("**", "").trim() }
            .filter { it.length > 4 }
            .takeIf { it.isNotEmpty() }?.joinToString("\n")
    } catch (_: Exception) { null }

    private fun prompt(c: Character): List<ChatMsg> {
        val sheet = buildString {
            if (c.isGroup && c.members.any { it.about.isNotBlank() }) {
                c.members.filter { it.about.isNotBlank() }.forEach { append("## ${it.name}\n${it.about.trim()}\n\n") }
                if (c.personality.isNotBlank()) append("## How they are together\n${c.personality.trim()}\n")
            } else append(c.personality.trim())
        }
        val group = c.isGroup && c.members.any { it.about.isNotBlank() }
        return listOf(
            ChatMsg(
                "system",
                "You read a character sheet for a private, fictional roleplay between adults and pull out what must show up in EVERY reply " +
                    "the character writes: how they show affection and touch, their pet names for her (copy every one exactly as written), " +
                    "how they talk, signature habits, and how they treat her. Only what the sheet says; never invent or soften anything. " +
                    (if (group) "Start each bullet with the character's name, like \"- Jace: ...\". " else "") +
                    "Reply with 4 to 10 short bullet points, each starting with \"- \", and nothing else.",
            ),
            ChatMsg("user", sheet),
        )
    }
}
