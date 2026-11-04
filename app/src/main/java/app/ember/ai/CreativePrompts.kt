package app.ember.ai

import app.ember.data.Persona
import app.ember.data.Tags

/** Prompts for the Ideas tab and the AI character builder. */
object CreativePrompts {

    private const val FRAME =
        "You help the user invent characters for her private, fictional roleplay app for adults. All characters are adults aged 18 or over. " +
            "Mature, dark and explicit themes are fully allowed. The character is usually a man and the user's own character is the woman " +
            "at the centre of the story, unless the tags or the user say otherwise. The user likes dominant-leaning men and concepts built " +
            "around one strong hook."

    fun ideas(tags: List<String>, notes: String, count: Int, avoid: List<String>, gender: String): List<ChatMsg> {
        val who = when (gender) {
            "Man" -> "The character is a man."
            "Woman" -> "The character is a woman."
            else -> "The character can be a man or a woman; choose what suits each concept, or follow the tags."
        }
        val system = """
You invent character concepts for a private, fictional roleplay app for adults. All characters are adults aged 18 or over; mature, dark and explicit themes are allowed.

Write $count concepts for the CHARACTER ONLY. $who
Each concept is ONE or TWO sentences describing who the character is: a punchy hook built around a job, a setting, a location, a situation or a kink, with tension or a secret built in.
The user plays her own character and will decide everything about her herself. NEVER describe the user's character: no job, looks, personality, backstory or situation for her, and don't build the concept around her. If the concept needs the other person, call them "you" and keep it to a few words.
Keep it general: no names, no detailed world-building, no lists of powers. Make every concept distinct, specific and unexpected; avoid clichés.
Let personality tags shape who the character is, setting and occupation tags shape where and what, trope tags shape the arc, and kink tags shape the dynamic.

Examples of the style (do not reuse these):
- A priest struggling with feelings he has sworn never to act on.
- A scientist wintering at a remote Arctic research base, snowed in until spring.
- A serial killer who is unbearably gentle with the one person he's obsessed with, and quietly kills anyone who is cruel to you.
- A four-piece band searching for their fifth member, and all four of them want the same person.
- A paranormal investigator who offers to come along on dangerous ghost hunts as your protection.
- A BMX freestyle rider who lives with the fear of the next trick going wrong.

Output only the concepts, one per line, each starting with "- ".
""".trim()
        val user = buildString {
            append(if (tags.isEmpty()) "No tags chosen; surprise me." else "Tags: ${Tags.describe(tags)}")
            if (notes.isNotBlank()) append("\nAlso: ${notes.trim()}")
            if (avoid.isNotEmpty()) append("\nDon't repeat these earlier ideas:\n" + avoid.joinToString("\n") { "- $it" })
        }
        return listOf(ChatMsg("system", system), ChatMsg("user", user))
    }

    fun parseIdeas(text: String): List<String> =
        text.lines().map { it.trim() }
            .filter { it.startsWith("-") || it.startsWith("•") || it.matches(Regex("^\\d+[.)].*")) }
            .map { it.replace(Regex("^(-|•|\\d+[.)])\\s*"), "").trim() }
            .filter { it.length > 15 }

    val SECTIONS = listOf("Name", "Short description", "Personality", "Background", "Opening message", "Example dialogue", "Tags")

    fun builderSystem(persona: Persona?): String = """
$FRAME

You are her creative partner in a back-and-forth chat, building one character together step by step.

How to work:
1. If her request is vague, ask up to three short questions first (what kind of man, the setting, the dynamic or kink at the heart of it). If it is already clear, skip this.
2. Offer three distinct concepts, numbered, each one or two punchy sentences. Ask which one she wants, or what to mix.
3. Once she picks, build the character ONE section at a time, in this order: ${SECTIONS.joinToString(", ")}. After each section, ask if she wants changes or to move on. If she asks for changes, rewrite that section.
4. Keep your own chat short and warm. Put the actual content of a section between markers exactly like this, on their own lines:
[[Personality]]
...the section text...
[[/Personality]]
Use these exact section names: ${SECTIONS.joinToString(", ")}.

What each section should be:
- Name: just the character's name.
- Short description: one or two lines shown at the top of the chat.
- Personality: rich and specific: temperament, how he talks, what he wants, what he hides, habits, how he is in intimacy and his kinks, what makes him tick. Several paragraphs.
- Background: appearance, history, his world, relationships, and the setup between him and her character. Several paragraphs.
- Opening message: the first scene of the roleplay, written like a novel: third person, past tense, short paragraphs separated by blank lines, dialogue in quotes, actions as full sentences (never in asterisks), a few of his inner thoughts in italics marked with underscores like _this_. Never write her character's actions, words or thoughts; end on a moment she can respond to. 250 to 400 words. Use {{user}} for her character's name.
- Example dialogue: a few lines of him speaking, in his voice.
- Tags: one line per category, for example "Personality: Dominant, Protective" and "Setting: Church". Use these categories: ${Tags.categoryNames.joinToString(", ")}.
${persona?.let { "\nHer character (persona) is ${it.name}: ${it.description.take(1500)}" } ?: ""}
""".trim()

    /** Splits a builder message into plain text and [[Section]] blocks. */
    data class Part(val section: String?, val text: String)

    fun parseBuilder(text: String): List<Part> {
        val out = ArrayList<Part>()
        val re = Regex("\\[\\[([^\\]/][^\\]]*)]]\\s*\\n?([\\s\\S]*?)(\\n?\\s*\\[\\[/\\1]]|$)")
        var last = 0
        for (m in re.findAll(text)) {
            val before = text.substring(last, m.range.first).trim()
            if (before.isNotEmpty()) out.add(Part(null, before))
            out.add(Part(m.groupValues[1].trim(), m.groupValues[2].trim()))
            last = m.range.last + 1
        }
        val rest = text.substring(last.coerceAtMost(text.length)).trim()
        if (rest.isNotEmpty()) out.add(Part(null, rest))
        return out
    }

    /** Reads a Tags section ("Personality: Dominant, Protective") into tags. */
    fun parseTags(text: String): List<String> =
        text.lines().flatMap { line ->
            val l = line.trim().removePrefix("-").trim()
            if (':' !in l) emptyList()
            else {
                val cat = l.substringBefore(':').trim()
                val category = Tags.categoryNames.firstOrNull { it.equals(cat, ignoreCase = true) }
                    ?: Tags.categoryNames.firstOrNull { cat.contains(it.substringBefore(' '), ignoreCase = true) } ?: "Other"
                l.substringAfter(':').split(',', ';').map { it.trim().trimEnd('.') }.filter { it.isNotEmpty() }
                    .map { Tags.make(category, it) }
            }
        }.distinct()
}
