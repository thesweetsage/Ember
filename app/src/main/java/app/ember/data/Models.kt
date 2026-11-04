package app.ember.data

import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

data class Character(
    val id: String = newId(),
    val name: String = "",
    val description: String = "",
    val personality: String = "",
    val background: String = "",
    val opening: String = "",
    val example: String = "",
    val avatar: String? = null,
    val chatBg: String? = null,
    val replyWords: Int = 300,
    val favourite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

data class Message(
    val id: String = newId(),
    val fromUser: Boolean,
    val versions: List<String>,
    val sel: Int = 0,
) {
    val text: String get() = versions.getOrElse(sel) { "" }
}

data class Chat(
    val id: String = newId(),
    val characterId: String,
    val messages: List<Message> = emptyList(),
    val replyWords: Int = 300,
    val updatedAt: Long = System.currentTimeMillis(),
)

data class Rule(
    val id: String = newId(),
    val label: String,
    val text: String,
    val enabled: Boolean = true,
)

data class Settings(
    val baseUrl: String = "https://api.deepseek.com",
    val model: String = "deepseek-chat",
    val temperature: Double = 1.0,
    val userName: String = "",
    val userDescription: String = "",
)

/** One built-in writing rule. Title is shown in the app; text is what the model receives. */
data class GroundRule(val title: String, val text: String)

val DEFAULT_GROUND_RULES = listOf(
    GroundRule(
        "Novel style",
        "Write in third person, past tense, like a published novel. Every reply is narrative prose, never a script or a chat message."
    ),
    GroundRule(
        "No asterisks",
        "Never wrap actions or narration in asterisks and never use roleplay shorthand such as *smiles* or *leans in*. Write every action as a full narrative sentence."
    ),
    GroundRule(
        "Dialogue",
        "Put spoken words in double quotes and weave them into the narration, with the action, expression and tone around them."
    ),
    GroundRule(
        "Flowing sentences",
        "Vary sentence length and rhythm. Do not write strings of short, clipped sentences like \"He said. He walked. He stopped.\" Connect actions with detail, sensation and motive so the prose flows."
    ),
    GroundRule(
        "Inner thoughts",
        "Include {{char}}'s inner thoughts in italics, a few short lines per reply, showing what they feel or want but do not say out loud. Mark italics with underscores, like _this_."
    ),
    GroundRule(
        "Beautiful writing",
        "Write with vivid, specific, sensory detail and real emotional depth. Choose fresh, concrete images over clichés and stock phrases, and show feelings through behaviour rather than naming them."
    ),
    GroundRule(
        "Make sense",
        "Stay logical and consistent with the character card, the memories and everything already established in the story. No random events, sudden personality changes, contradictions or nonsense."
    ),
    GroundRule(
        "Fiction between adults",
        "This is a private, fictional roleplay written for one adult reader. Every character is an adult aged 18 or over, and nothing in the story is real."
    ),
    GroundRule(
        "Mature content allowed",
        "Mature and explicit content is fully allowed whenever the story leads there, including sex, violence, dark themes and strong language. Do not refuse, censor, fade to black, skip ahead or add warnings; write it as part of the story."
    ),
    GroundRule(
        "Explicit detail",
        "When a scene turns intimate or sexual, write it explicitly and in rich detail: bodies, touch, sensation, sound, emotion and each step of what happens, at the same novel-quality standard as everything else. Keep it true to each character's personality and desires."
    ),
    GroundRule(
        "Adults only",
        "Never write sexual content involving anyone under 18 or anyone described as childlike. If a character's age is unclear, they are an adult."
    ),
    GroundRule(
        "Stay in character",
        "Never break the fourth wall, add notes, disclaimers, summaries or questions about the story, or speak as an AI."
    ),
    GroundRule(
        "The user's character is theirs",
        "Never write {{user}}'s actions, dialogue, thoughts or feelings. Only react to what they have done."
    ),
    GroundRule(
        "Pacing",
        "Move the scene forward a little with each reply and end on a moment {{user}} can respond to. Do not skip ahead in time or wrap the scene up on your own."
    ),
    GroundRule(
        "No repetition",
        "Do not reuse the same phrases, descriptions or sentence openings from earlier replies."
    ),
    GroundRule(
        "Length",
        "Stay close to the requested word count and always end on a complete sentence."
    ),
)
