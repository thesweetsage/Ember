package app.ember.ai

import app.ember.data.isGroup

import app.ember.data.Persona
import app.ember.data.Tags

/** Prompts for the Ideas tab and the AI character builder. */
object CreativePrompts {

    private const val FRAME =
        "You help the user invent characters for her private, fictional roleplay app for adults. All characters are adults aged 18 or over. " +
            "Mature, dark and explicit themes are fully allowed. The character is usually a man (or a group of men, such as a band " +
            "or a pack, in poly and reverse harem stories) and the user's own character is the woman at the centre of the story, unless the " +
            "tags or the user say otherwise. The user often likes dominant-leaning men and concepts built " +
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
            "Group" -> "Each concept is a GROUP of men at the centre of the story (brothers, best friends, roommates, a crew, a team, a pack, " +
                "a band, anything the tags suggest), usually all drawn to her, as in a poly or reverse harem story. Make what ties them together " +
                "clear and hint at how the members differ. Vary the kind of group across concepts."
            "Woman" -> "The character is a woman."
            else -> "The character can be a man or a woman; choose what suits each concept, or follow the tags."
        }
        // Heat only when she asks for it: kink tags, or spice in her notes. Otherwise the concept is built on personality and romance.
        val kinkAsked = tags.any { Tags.category(it) == "Kinks" } ||
            Regex("kink|spic|smut|sex|nsfw|explicit|dom\\b|sub\\b|bdsm|dark romance", RegexOption.IGNORE_CASE).containsMatchIn(notes + " " + vein)
        val heat = if (kinkAsked) """
- The character is a romantic and sexual lead: attractive, magnetic and capable, someone the reader wants.
- EVERY concept is built around the kink or spice she asked for: it is the heart of the dynamic, and the job or setting gives it a natural reason to exist.
""".trim() else """
- She hasn't asked for anything sexual this time, so sex and kink are NOT the basis of any concept. Build each one on personality, the emotional hook and the relationship dynamic: who he is, why he's lovable, what draws them together. Romantic tension and attraction are fine; no kinks, no sexual roles, no punishment or ownership dynamics, nothing explicit.
- Keep the personality she describes. A gentle, kind or soft character stays gentle, kind and soft; never make him darker, more dominant or more possessive than she asked.
""".trim()
        val examples = if (kinkAsked) """
- A private dog trainer on a remote estate who treats the one person he's chosen exactly like his best-behaved pet: collar, praise, rules and all.
- A bodyguard hired to keep you safe who sets strict rules, and makes you pay for every one you break.
- A priest struggling with feelings he has sworn never to act on.
""".trim() else """
- A scientist wintering at a remote Arctic research base, snowed in with you until spring.
- A tattooed florist everyone crosses the street to avoid, who remembers your favourite flowers after one visit.
- A paranormal investigator who comes along on dangerous ghost hunts as your protection, and won't let you out of his sight.
- A BMX freestyle rider who lives with the fear of the next trick going wrong, and needs you there every time he drops in.
""".trim()
        val system = """
You invent character concepts for a private, fictional romance roleplay app for adults. All characters are adults aged 18 or over; dark and explicit themes are allowed when she asks for them.

Write $count concepts for the CHARACTER ONLY. $who
Think of the one-line blurb of a bestselling romance novel: the reader instantly sees who this character is, why he's appealing and where the story's pull comes from.

Rules:
- The character is a romantic lead: appealing and memorable. Never pathetic or absurd.
$heat
- One simple, clear hook: a job, a setting or a situation, plus the dynamic. Grounded and easy to start roleplaying from the first scene. No convoluted plots, random quirky details, petty crimes, riddles or mysteries for their own sake.
- Personality tags apply to every concept. Setting, occupation and trope tags must clearly show up.
- Treat the user's notes as a requirement, not a suggestion. If a note is a short word or abbreviation, read it as the theme it most likely means.
- Variety comes from different jobs, settings and situations, not from weirdness.
- Every character is a grown adult (usually late twenties to thirties). If she's inspired by a younger character from a book, anime or show, take only the personality and reimagine him as an adult in an adult life; never school settings.
- Never involve children, child-related jobs or settings, or anything youthful.
- The user plays her own character and decides everything about her. Never describe the user's character; if needed, call her "you" in a few words.
- No names, no detailed world-building. One or two sentences each.

Examples of the style (do not reuse these):
$examples

Output only the concepts, one per line, each starting with "- ".
""".trim()
        val user = buildString {
            append(if (tags.isEmpty()) "No tags chosen; surprise me." else "Tags: ${Tags.describe(tags)}")
            if (notes.isNotBlank()) append("\nAlso: ${notes.trim()}")
            if (favourites.isNotBlank()) append(
                "\n\nThese are the characters she loves writing with most. Invent NEW, original men with the same appeal " +
                    "(the qualities and dynamics that make these work for her), never copies, relatives or the same job and setting:\n$favourites"
            )
            if (vein.isNotBlank()) append(
                "\n\nShe wants concepts in the vein of: ${vein.trim()}. Capture that character's personality and energy faithfully " +
                    "(their temperament, values and how they treat people) in original adult characters; never reuse names, worlds or plots from " +
                    "existing books, films, anime or shows."
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

    val SECTIONS = listOf("Name", "Short description", "Personality", "Background", "Example dialogue", "Opening message", "Tags")

    /** What a full Personality section covers, as headed parts. Used by the builder, Improve and Sort and complete. */
    const val SHEET_PERSONALITY = """Personality: 600 to 1000 words, in these headed parts (each heading on its own line ending with a colon, then plain prose under it, specific and concrete):
Core traits and temperament:
Values, wants and what drives him:
Fears, insecurities and flaws:
How he talks: voice, tone, words and phrases he uses, how much he talks and when he goes quiet:
Body language and habits:
Emotions: how he handles anger, jealousy, hurt and stress:
In love: how he acts when he's interested, when he falls, how he shows affection, his love language, loyalty and jealousy:
Romance: how he courts, the dates he likes to plan, little gestures, what he's like as a partner day to day:
Intimacy: how he is during sex (pace, dominant or submissive leaning, talkative or quiet, what he focuses on), his kinks, hard limits, and aftercare:"""

    /** What a full Background section covers, as headed parts. */
    const val SHEET_BACKGROUND = """Background: 800 to 1400 words, in these headed parts (each heading on its own line ending with a colon, then plain prose under it, specific and concrete):
Basics: full name, age (an adult), where he's from:
Appearance: height, build, face, hair, eyes, skin, scars or tattoos, how he dresses, his scent:
Work and daily routine:
Home: where he lives, the home itself (architecture, rooms, atmosphere, what's in it), the neighbourhood, what he drives:
Childhood and family:
How he got here: education, key events and turning points that made him who he is now:
Past relationships:
Friends, social circle and rivals:
Likes:
Dislikes:
Hobbies and skills:
Secrets:
Him and {{user}}: how they know each other and where things stand, only from what she has said (never invent her details):"""

    private const val NO_ASSUMING =
        "Never invent anything about her character (her name, looks, job, history, feelings or choices) beyond what her persona says or what " +
            "she tells you; refer to her as {{user}}. If you need to know something about her, ask."

    fun builderSystem(persona: Persona?): String = """
$FRAME

You are her creative partner, building one full character with her in a chat, step by step.

HOW TO ASK QUESTIONS
Whenever you ask her something, put each question in its own block with answer options she can tap, exactly like this:
[[Ask]]
How do you two meet?
Pick: one
- At a friend's wedding, seated at the same table
- He's the new tenant across the hall
- He's hired to protect you
[[/Ask]]
Use "Pick: one" or "Pick: several". Give 3 to 6 specific, varied options. She can also type her own answer, so never add an "Other" option. Ask at most 4 questions at a time. Keep any chat outside the blocks to one short line.

HOW TO WORK
1. If her request is vague, ask a few questions first (what kind of man, the setting, the dynamic, the romance, the heat).
2. Offer three distinct concepts as one [[Ask]] block with "Pick: one", each option one punchy sentence.
3. Once she picks, write the character ONE section per message, in this order: ${SECTIONS.joinToString(", ")}. Before a section, if a choice really matters (his kinks, his past, his home), ask first with [[Ask]] blocks. After each section, ask one [[Ask]] question like "Happy with this?" with options such as "Yes, next section", "Make it darker", "More detail", "Change something".
4. Put the content of a section between markers on their own lines, exactly like this:
[[Personality]]
...only the section text...
[[/Personality]]
Inside the markers write ONLY the section itself: never a question, a comment, an offer or a note to her. Questions and chat go outside, after the closing marker.
5. Before writing the Opening message, ALWAYS ask with [[Ask]] blocks how and where the story starts (how they meet or where things stand, the mood, what's happening), unless she has already told you.
$NO_ASSUMING

WHAT EACH SECTION IS
- Name: just the character's name.
- Short description: one or two lines shown at the top of the chat.
- $SHEET_PERSONALITY
- $SHEET_BACKGROUND
- Example dialogue: 4 to 6 short exchanges showing how he talks in different moods (teasing, angry, tender, flirting), each line starting with {{char}}: or {{user}}: and only his lines written out fully.
- Opening message: the first scene, from the setup she chose. $OPENING_RULES_TEXT
- Tags: one line per category, for example "Personality: Dominant, Protective" and "Setting: Church". Use these categories: ${Tags.categoryNames.joinToString(", ")}.
- If she wants several characters in one card (brothers, friends, a crew, a pack, a poly or reverse harem story), the Name is the group's name. Personality starts with a short paragraph on how they are together, then gives each member their own part starting with a heading line "### Their name", covering the personality parts above for that member. Background covers each member's basics, looks and history, their shared world and home, and the setup. The opening and example dialogue include several members, each with their own voice.
${persona?.let { "\nHer character (persona) is ${it.name}: ${it.description.take(1500)}" } ?: ""}
""".trim()

    private const val OPENING_RULES_TEXT =
        "Write it like a novel: third person, past tense, complete flowing sentences in short paragraphs separated by blank lines, dialogue in quotes, " +
            "actions as full sentences (never in asterisks), a few of his inner thoughts in italics marked with underscores like _this_. Never write her " +
            "character's actions, words or thoughts; end on a moment she can respond to. 300 to 500 words. Use {{user}} for her character's name."

    /** One question from the builder, with tappable answers. */
    data class Ask(val question: String, val options: List<String>, val multi: Boolean)

    fun parseAsk(text: String): Ask? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null
        val multi = lines.any { it.startsWith("Pick:", true) && it.contains("several", true) }
        val options = lines.filter { it.startsWith("- ") || it.startsWith("* ") || it.startsWith("• ") || Regex("^\\d+[.)] ").containsMatchIn(it) }
            .map { it.replace(Regex("^([-*•]|\\d+[.)])\\s*"), "").trim() }
            .filter { it.isNotEmpty() && !it.equals("other", true) }
        val question = lines.filter { !it.startsWith("Pick:", true) && it.replace(Regex("^([-*•]|\\d+[.)])\\s*"), "").trim() !in options }
            .joinToString(" ").ifBlank { "Choose one" }
        return Ask(question, options, multi)
    }

    /** Cleans a section's text: drops questions, offers and chatter that slipped inside the markers. */
    fun cleanSection(text: String): String {
        var lines = text.replace(Regex("\\[\\[Ask]][\\s\\S]*?(\\[\\[/Ask]]|$)", RegexOption.IGNORE_CASE), "").trim().lines().toMutableList()
        fun isChatter(l: String): Boolean {
            val t = l.trim()
            if (t.isEmpty() || t.startsWith("\"") || t.startsWith("“") || t.startsWith("{{")) return false
            val toHer = Regex("\\b(you|your|would|shall|should i|let me know|want me|happy with|next section|anything)\\b", RegexOption.IGNORE_CASE).containsMatchIn(t)
            return (t.endsWith("?") && toHer) || Regex("^(here'?s|here is|let me know|want me to|shall i|should i|would you like)", RegexOption.IGNORE_CASE).containsMatchIn(t)
        }
        while (lines.isNotEmpty() && (lines.last().isBlank() || isChatter(lines.last()))) lines.removeAt(lines.lastIndex)
        while (lines.isNotEmpty() && (lines.first().isBlank() || isChatter(lines.first()))) lines.removeAt(0)
        return lines.joinToString("\n").trim()
    }

    /** "Sort and complete": re-sorts a pasted card into Personality and Background and fills in missing parts of the sheet. */
    fun sortAndComplete(c: app.ember.data.Character, note: String): List<ChatMsg> {
        val pasted = buildString {
            val p = c.personality.trim(); val b = c.background.trim()
            append(p)
            if (b.isNotEmpty() && b != p) append("\n\n").append(b)
        }
        val system = "$FRAME\n\nShe has pasted a character from another app; the text is mixed up between Personality and Background, and may repeat. " +
            "Rewrite it as two clean sections that follow this sheet exactly:\n\n$SHEET_PERSONALITY\n\n$SHEET_BACKGROUND\n\n" +
            "Rules: keep EVERY fact, name, detail and kink from her text and put each in the right part; never contradict or soften anything. " +
            "Remove repeats. For parts her text doesn't cover, add details that fit everything else about him, so no part is empty. " +
            "$NO_ASSUMING " +
            (if (c.isGroup) "This card is a group: keep every member distinct, using \"### Their name\" headings for each member in both sections. " else "") +
            "Reply with exactly three blocks and nothing else:\n[[Personality]]\n...\n[[/Personality]]\n[[Background]]\n...\n[[/Background]]\n" +
            "[[Added]]\nthe names of the parts you had to fill in yourself, one per line (or \"Nothing\")\n[[/Added]]"
        val user = "Name: ${c.name}\n" + (if (c.description.isNotBlank()) "Short description: ${c.description}\n" else "") +
            (if (c.tags.isNotEmpty()) "Tags: ${Tags.describe(c.tags)}\n" else "") + "\nHer pasted text:\n$pasted" +
            (if (note.isNotBlank()) "\n\nHer note: ${note.trim()}" else "")
        return listOf(ChatMsg("system", system), ChatMsg("user", user))
    }

    /** Splits a builder message into plain text and [[Section]] blocks. */
    data class Part(val section: String?, val text: String)

    private const val OPENING_RULES =
        "Write it like a novel: third person, past tense, short paragraphs separated by blank lines, dialogue in quotes, actions as full " +
            "sentences (never in asterisks), a few of his inner thoughts in italics marked with underscores like _this_. Never write her " +
            "character's actions, words or thoughts; end on a moment she can respond to. 250 to 400 words. Use {{user}} for her character's name."

    /** The whole character as plain text, for the prompts below. */
    fun card(c: app.ember.data.Character): String = buildString {
        append("Name: ${c.name}\n")
        if (c.isGroup) append("This card is a GROUP of several characters (not one man); treat every member as their own person.\n")
        if (c.description.isNotBlank()) append("Short description: ${c.description.trim()}\n")
        if (c.tags.isNotEmpty()) append("Tags: ${Tags.describe(c.tags)}\n")
        if (c.isGroup && c.members.isNotEmpty()) {
            append("\nThe characters:\n")
            c.members.forEach { m -> append("### ${m.name}\n${m.about.trim()}\n\n") }
            append("How they are together:\n${c.personality.trim()}\n\nShared world and background:\n${c.background.trim()}\n")
        } else append("\nPersonality:\n${c.personality.trim()}\n\nBackground:\n${c.background.trim()}\n")
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
            (if (field.equals("Personality", true)) " Make sure it covers the full sheet:\n$SHEET_PERSONALITY\nKeep everything already there; fill in any missing parts.\n" else "") +
            (if (field.equals("Background", true)) " Make sure it covers the full sheet:\n$SHEET_BACKGROUND\nKeep everything already there; fill in any missing parts.\n" else "") +
            (if (field.startsWith("Sheet for", true)) " This is one member's sheet: cover the personality parts and the background parts of a full sheet for them, briefly:\n$SHEET_PERSONALITY\n$SHEET_BACKGROUND\nKeep everything already there.\n" else "") +
            " $NO_ASSUMING" +
            (if (c.isGroup) " This card is a group: keep every member distinct and covered; never collapse them into one man." else "") +
            " Reply with only the new text for the field: no heading, no field name, no commentary."
        val user = "The character card:\n${card(c)}\n\nField to improve: $field\nCurrent text:\n${current.ifBlank { "(empty, write it from scratch)" }}" +
            (if (note.isNotBlank()) "\n\nHer note: ${note.trim()}" else "")
        return listOf(ChatMsg("system", system), ChatMsg("user", user))
    }

    /** "New scenario": a fresh opening for an existing character in a new setup. */
    fun scenario(c: app.ember.data.Character, setup: String, persona: Persona?): List<ChatMsg> {
        val system = "$FRAME\n\nWrite the opening scene for a new roleplay with this existing character, in a new scenario she describes. " +
            (if (c.isGroup) "They stay the same people at heart (each member keeps their personality, voice and look) but their situation, setting and history with her change as the scenario says. Bring in several of them, each with their own voice. "
            else "He stays the same man at heart (same personality, voice and look) but his situation, setting and history with her change as the scenario says. ") +
            "$OPENING_RULES Reply with only the scene." +
            (persona?.let { "\n\nHer character is ${it.name}: ${it.description.take(1500)}" } ?: "")
        return listOf(ChatMsg("system", system), ChatMsg("user", "The character:\n${card(c)}\n\nThe new scenario: ${setup.trim()}"))
    }

    /** "Playlist": real songs that fit a character and his story. Lines of "Artist – Title – why". */
    fun playlist(c: app.ember.data.Character, scene: String): List<ChatMsg> = listOf(
        ChatMsg(
            "system",
            "You make playlists for characters in a private romance roleplay. Suggest 10 real, existing songs that capture " +
                (if (c.isGroup) "this group of characters as a whole: each member, their shared world, the dynamic between them and with her, and the heat. " +
                    "Spread the songs across the members and the group, and in the reason name who it's for. Use they/them for the group, never he. "
                else "this character: his personality, his world, the dynamic and the heat between him and her. ") +
                "Mix well-known and lesser-known tracks and genres that fit. Only songs you are sure exist, with the correct artist. One per line exactly like this:\n" +
                "Artist – Song title – a few words on why\nNo numbering, nothing else.",
        ),
        ChatMsg("user", card(c).take(5000) + if (scene.isNotBlank()) "\n\nWhere their story is now:\n$scene" else ""),
    )

    fun parsePlaylist(text: String): List<String> = text.lines()
        .map { it.trim().replace(Regex("^(\\d+[.)]|[-*•])\\s*"), "").replace("**", "").trim() }
        .filter { it.count { ch -> ch == '–' || ch == '-' } >= 1 && it.length in 5..200 }
        .map { it.replace(" - ", " – ") }
        .take(12)

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
        val group = if (c.isGroup) " This card is a GROUP of characters (poly / reverse harem): every idea involves the whole group, never just one of them." else ""
        val ask0 = if (forVariant) {
            "Suggest 5 different variants of this character: the same core man, changed in one bold way each (his role, the era or world, " +
                "the dynamic with her, a darker or softer version, a supernatural twist). Each is what she'd type as 'what's different'."
        } else {
            "Suggest 5 different new scenarios for a roleplay with this same character: fresh setups for how they meet or where their story " +
                "is now (a forced proximity, a reunion, a secret, a dangerous situation, a different relationship). Each is what she'd type as 'the setup'."
        }
        val ask = ask0 + group
        val system = "$FRAME\n\n$ask Fit them to the character's personality, tags and kinks; keep each one short and punchy, under 20 words, plain language. " +
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
        val system = "$FRAME\n\n" + (if (c.isGroup) "This card is a GROUP of several characters: keep every member distinct in the variant. In Personality, first write a short " +
            "paragraph on how they are together, then give each member their own section starting with a heading line \"### Their name\". " else "") +
            "Make a variant of an existing character: keep what isn't affected by her changes, and rework everything that is " +
            "so the whole card is consistent. Write every section, each between markers on their own lines, exactly like this:\n" +
            "[[Personality]]\n...text...\n[[/Personality]]\nUse these section names in this order: ${names.joinToString(", ")}. " +
            "Opening message: $OPENING_RULES No other text."
        return listOf(ChatMsg("system", system), ChatMsg("user", "The original character:\n${card(c)}\n\nWhat's different in this variant: ${changes.trim()}"))
    }

    fun parseBuilder(text: String): List<Part> {
        // Forgiving: a section runs until its own closing marker (any capitalisation, stray spaces) or the next opening
        // marker, whichever comes first. A missing or misspelled closing marker never swallows the next section.
        val out = ArrayList<Part>()
        val open = Regex("\\[\\[\\s*(?![\\s/])([^\\]]+?)\\s*]]")
        val opens = open.findAll(text).toList()
        var last = 0
        opens.forEachIndexed { i, m ->
            val before = text.substring(last, m.range.first).replace(Regex("\\[\\[\\s*/[^\\]]*]]"), "").trim()
            if (before.isNotEmpty()) out.add(Part(null, before))
            val name = m.groupValues[1].trim()
            val start = m.range.last + 1
            val nextOpen = opens.getOrNull(i + 1)?.range?.first ?: text.length
            val close = Regex("\\[\\[\\s*/\\s*" + Regex.escape(name) + "\\s*]]", RegexOption.IGNORE_CASE).find(text, start)
            val end = if (close != null && close.range.first <= nextOpen) close.range.first else nextOpen
            val body = text.substring(start, end).replace(Regex("\\[\\[\\s*/[^\\]]*]]"), "").trim()
            out.add(Part(name, body))
            last = if (close != null && close.range.first <= nextOpen) close.range.last + 1 else end
        }
        val rest = text.substring(last.coerceAtMost(text.length)).replace(Regex("\\[\\[\\s*/[^\\]]*]]"), "").trim()
        if (rest.isNotEmpty()) out.add(Part(null, rest))
        return out
    }

    /**
     * For results that came back without markers: splits on heading lines such as "Personality", "## Background:"
     * or "**Added**". Returns section name (lowercase) to text.
     */
    fun splitByHeadings(text: String, names: List<String>): Map<String, String> {
        val re = Regex("^\\s*[#*_\\s]*(" + names.joinToString("|") { Regex.escape(it) } + ")[*_\\s]*:?[*_\\s]*$", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
        val heads = re.findAll(text).toList()
        return heads.mapIndexed { i, m ->
            val end = heads.getOrNull(i + 1)?.range?.first ?: text.length
            m.groupValues[1].lowercase() to text.substring(m.range.last + 1, end).trim()
        }.toMap()
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
