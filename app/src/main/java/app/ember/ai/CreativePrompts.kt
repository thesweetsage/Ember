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

    fun ideas(
        tags: List<String>, notes: String, count: Int, avoid: List<String>, gender: String,
        /** "Inspired by your favourites": short cards of the characters she writes with most. */
        favourites: String = "",
        /** "In the vein of…": a character or trope she loves. */
        vein: String = "",
        liked: List<String> = emptyList(),
        disliked: List<String> = emptyList(),
    ): List<ChatMsg> {
        val who = when (gender) {
            "Man" -> "The character is a man."
            "Woman" -> "The character is a woman."
            else -> "The character can be a man or a woman; choose what suits each concept, or follow the tags."
        }
        val system = """
You invent character concepts for a private, fictional romance and erotica roleplay app for adults. All characters are adults aged 18 or over; dark and explicit themes are allowed.

Write $count concepts for the CHARACTER ONLY. $who
Think of the one-line blurb of a bestselling spicy or dark romance: the reader instantly sees why this character is desirable and where the heat and tension come from.

Rules:
- The character is a romantic and sexual lead: attractive, magnetic and capable, someone the reader wants. Never pathetic, absurd, or creepy without being alluring.
- One simple, clear hook: a job, a setting or a situation, plus the dynamic. Grounded and easy to start roleplaying from the first scene. No convoluted plots, random quirky details, petty crimes, riddles or mysteries for their own sake.
- If kink tags are chosen, or a kink is mentioned in the notes, EVERY concept is built around it: the kink is the heart of the dynamic, and the job or setting gives it a natural reason to exist.
- Personality tags apply to every concept. Setting, occupation and trope tags must clearly show up.
- Treat the user's notes as a requirement, not a suggestion. If a note is a short word or abbreviation, read it as the kink or theme it most likely means.
- Variety comes from different jobs, settings and situations, not from weirdness.
- Never involve children, child-related jobs or settings, or anything youthful.
- The user plays her own character and decides everything about her. Never describe the user's character; if needed, call her "you" in a few words.
- No names, no detailed world-building. One or two sentences each.

Examples of the style (do not reuse these):
- A priest struggling with feelings he has sworn never to act on.
- A private dog trainer on a remote estate who treats the one person he's chosen exactly like his best-behaved pet: collar, praise, rules and all.
- A bodyguard hired to keep you safe who sets strict rules, and makes you pay for every one you break.
- A scientist wintering at a remote Arctic research base, snowed in with you until spring.
- A paranormal investigator who comes along on dangerous ghost hunts as your protection, and won't let you out of his sight.
- A BMX freestyle rider who lives with the fear of the next trick going wrong, and needs you there every time he drops in.

Output only the concepts, one per line, each starting with "- ".
""".trim()
        val user = buildString {
            append(if (tags.isEmpty()) "No tags chosen; surprise me." else "Tags: ${Tags.describe(tags)}")
            if (notes.isNotBlank()) append("\nAlso: ${notes.trim()}")
            if (favourites.isNotBlank()) append(
                "\n\nThese are the characters she loves writing with most. Invent NEW, original men with the same appeal " +
                    "(the qualities, dynamics and kinks that make these work for her), never copies, relatives or the same job and setting:\n$favourites"
            )
            if (vein.isNotBlank()) append(
                "\n\nShe wants concepts in the vein of: ${vein.trim()}. Capture that energy, dynamic and appeal in original characters; " +
                    "never reuse names, worlds or plots from existing books, films or shows."
            )
            if (liked.isNotEmpty()) append("\n\nIdeas she liked (more like these, in spirit):\n" + liked.take(12).joinToString("\n") { "- $it" })
            if (disliked.isNotEmpty()) append("\n\nIdeas she disliked (avoid what these have in common):\n" + disliked.take(12).joinToString("\n") { "- $it" })
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

    private const val OPENING_RULES =
        "Write it like a novel: third person, past tense, short paragraphs separated by blank lines, dialogue in quotes, actions as full " +
            "sentences (never in asterisks), a few of his inner thoughts in italics marked with underscores like _this_. Never write her " +
            "character's actions, words or thoughts; end on a moment she can respond to. 250 to 400 words. Use {{user}} for her character's name."

    /** The whole character as plain text, for the prompts below. */
    fun card(c: app.ember.data.Character): String = buildString {
        append("Name: ${c.name}\n")
        if (c.description.isNotBlank()) append("Short description: ${c.description.trim()}\n")
        if (c.tags.isNotEmpty()) append("Tags: ${Tags.describe(c.tags)}\n")
        append("\nPersonality:\n${c.personality.trim()}\n\nBackground:\n${c.background.trim()}\n")
        if (c.opening.isNotBlank()) append("\nOpening message:\n${c.opening.trim()}\n")
        if (c.example.isNotBlank()) append("\nExample dialogue:\n${c.example.trim()}\n")
    }

    /** "Improve with AI" on one field of the editor. */
    fun improve(c: app.ember.data.Character, field: String, current: String, note: String): List<ChatMsg> {
        val isOpening = field.contains("opening", ignoreCase = true)
        val system = "$FRAME\n\nYou are improving one field of a character card for her. Keep every established fact, name and relationship " +
            "unless her note says otherwise. Make it richer, more specific and more consistent with the rest of the card, in plain, " +
            "natural language rather than purple prose. Keep the same point of view and roughly the same shape." +
            (if (isOpening) " This field is the opening scene. $OPENING_RULES" else "") +
            (if (field.contains("description", ignoreCase = true)) " This field is one or two lines only." else "") +
            " Reply with only the new text for the field: no heading, no field name, no commentary."
        val user = "The character card:\n${card(c)}\n\nField to improve: $field\nCurrent text:\n${current.ifBlank { "(empty, write it from scratch)" }}" +
            (if (note.isNotBlank()) "\n\nHer note: ${note.trim()}" else "")
        return listOf(ChatMsg("system", system), ChatMsg("user", user))
    }

    /** "New scenario": a fresh opening for an existing character in a new setup. */
    fun scenario(c: app.ember.data.Character, setup: String, persona: Persona?): List<ChatMsg> {
        val system = "$FRAME\n\nWrite the opening scene for a new roleplay with this existing character, in a new scenario she describes. " +
            "He stays the same man at heart (same personality, voice and look) but his situation, setting and history with her change as the scenario says. " +
            "$OPENING_RULES Reply with only the scene." +
            (persona?.let { "\n\nHer character is ${it.name}: ${it.description.take(1500)}" } ?: "")
        return listOf(ChatMsg("system", system), ChatMsg("user", "The character:\n${card(c)}\n\nThe new scenario: ${setup.trim()}"))
    }

    /** A three or four sentence taste of the first scene for an idea. */
    fun teaser(idea: String): List<ChatMsg> = listOf(
        ChatMsg(
            "system",
            "$FRAME\n\nWrite a teaser for this character concept: the first three or four sentences of the opening scene, like the first lines " +
                "of a spicy romance novel. Third person, past tense, about him, with one line of his dialogue if it fits. Plain, natural language. " +
                "Never describe her character beyond \"you\". Reply with only the teaser.",
        ),
        ChatMsg("user", idea),
    )

    /** Short cards of characters for "Inspired by your favourites". */
    fun favouritesBrief(chars: List<app.ember.data.Character>): String = chars.joinToString("\n") { c ->
        "- ${c.description.ifBlank { c.personality.take(200) }.trim().replace("\n", " ")}" +
            (if (c.tags.isNotEmpty()) " (${Tags.describe(c.tags)})" else "")
    }

    /** Short ideas for "New scenario" or "Make a variant", fitted to this character. One per line. */
    fun suggestions(c: app.ember.data.Character, forVariant: Boolean, avoid: List<String>): List<ChatMsg> {
        val ask = if (forVariant) {
            "Suggest 5 different variants of this character: the same core man, changed in one bold way each (his role, the era or world, " +
                "the dynamic with her, a darker or softer version, a supernatural twist). Each is what she'd type as 'what's different'."
        } else {
            "Suggest 5 different new scenarios for a roleplay with this same character: fresh setups for how they meet or where their story " +
                "is now (a forced proximity, a reunion, a secret, a dangerous situation, a different relationship). Each is what she'd type as 'the setup'."
        }
        val system = "$FRAME\n\n$ask Fit them to his personality, tags and kinks; keep each one short and punchy, under 20 words, plain language. " +
            "Reply with exactly 5 lines, one idea per line, no numbering, bullets or quotes, nothing else."
        val user = "The character:\n${card(c).take(6000)}" +
            (if (avoid.isNotEmpty()) "\n\nDon't repeat these:\n" + avoid.joinToString("\n") else "")
        return listOf(ChatMsg("system", system), ChatMsg("user", user))
    }

    fun parseSuggestions(text: String): List<String> = text.lines()
        .map { it.trim().replace(Regex("^(\\d+[.)]|[-*•])\\s*"), "").trim().trim('"', '“', '”').trim() }
        .filter { it.length in 8..220 }.take(5)

    /** "Make a variant": the same character reworked with her changes, as builder-style sections. */
    fun variant(c: app.ember.data.Character, changes: String): List<ChatMsg> {
        val names = listOf("Name", "Short description", "Personality", "Background", "Opening message", "Example dialogue")
        val system = "$FRAME\n\nMake a variant of an existing character: keep what isn't affected by her changes, and rework everything that is " +
            "so the whole card is consistent. Write every section, each between markers on their own lines, exactly like this:\n" +
            "[[Personality]]\n...text...\n[[/Personality]]\nUse these section names in this order: ${names.joinToString(", ")}. " +
            "Opening message: $OPENING_RULES No other text."
        return listOf(ChatMsg("system", system), ChatMsg("user", "The original character:\n${card(c)}\n\nWhat's different in this variant: ${changes.trim()}"))
    }

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
