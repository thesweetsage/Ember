package app.ember.ai

import app.ember.data.Store
import app.ember.data.StoryChapter
import app.ember.data.isGroup

/** Writes the chapters of daily stories, and keeps a running summary so long stories stay consistent and cheap. */
object StoryWriter {

    private const val RULES =
        "You write a serialised romance novel for one reader, a private adult story. All characters are adults; mature, dark and explicit " +
            "themes are fully allowed when the story leads there. Write like a published novel: third person, past tense, complete flowing " +
            "sentences in paragraphs separated by blank lines, dialogue in double quotes, inner thoughts in italics marked with underscores like " +
            "_this_. Build it on prose and inner life: what the characters do, notice, feel and don't say. Dialogue sounds like real people: " +
            "contractions, everyday words, short turns, no speeches, catchphrases, aphorisms or clever one-liners. No dramatic one-word or " +
            "fragment sentences on their own. Unlike a roleplay, you write BOTH sides: the heroine (the reader's persona) acts, speaks and " +
            "thinks exactly as her persona describes her, never out of character. Each chapter is a complete episode with its own shape " +
            "(a beginning, rising tension, a turn, and an ending that makes the reader want the next one), and moves the relationship on."

    /** Writes the next chapter. Returns a problem to show, or null when the chapter was saved. */
    suspend fun writeNext(storyId: String): String? {
        val st = Store.story(storyId) ?: return "That story is gone."
        val c = Store.character(st.characterId) ?: return "The character for this story was deleted."
        val persona = Store.persona(st.personaId ?: c.personaId ?: Store.settings.defaultPersonaId)
        val her = persona?.name?.ifBlank { null } ?: "the heroine"
        val n = st.chapters.size + 1
        val fill = { t: String -> t.replace("{{char}}", c.name).replace("{{user}}", her).replace("{{persona}}", her).replace("{{character}}", c.name) }

        val system = buildString {
            append(RULES).append("\n\n")
            if (c.isGroup) append("The love interests are a group of several distinct men; give each their own voice and presence.\n\n")
            Store.groundRules.firstOrNull { it.title.contains("adult", true) }?.let { append(fill(it.text)).append("\n\n") }
            append("Reply with exactly three blocks and nothing else:\n[[Title]]\na short chapter title\n[[/Title]]\n[[Chapter]]\nthe chapter\n[[/Chapter]]\n")
            append("[[Next]]\nthree short ideas for where the next chapter could go, one per line\n[[/Next]]")
        }
        val user = buildString {
            append("THE LOVE INTEREST\n").append(fill(CreativePrompts.card(c))).append("\n\n")
            append("THE HEROINE (the reader): ").append(her)
            persona?.description?.takeIf { it.isNotBlank() }?.let { append("\n").append(fill(it.take(2500))) }
            append("\n\n")
            if (n == 1) {
                val opening = c.opening.trim()
                if (opening.isNotEmpty()) append("The story begins with this scene. Open Chapter 1 here, rewritten as novel prose, and carry on from it:\n").append(fill(opening)).append("\n\n")
                else append("This is Chapter 1: begin their story.\n\n")
            } else {
                if (st.summary.isNotBlank()) append("THE STORY SO FAR\n").append(st.summary).append("\n\n")
                st.chapters.lastOrNull()?.let { last ->
                    append("THE END OF THE LAST CHAPTER (\"${last.title}\")\n").append(last.text.takeLast(3500)).append("\n\n")
                }
            }
            append("Write Chapter $n, about ${st.words} words.")
            if (st.nextDirection.isNotBlank()) append(" The reader wants this chapter to go here: ${st.nextDirection.trim()}")
            else if (n > 1) append(" Choose the most compelling next step yourself.")
        }

        val raw = Ai.complete(listOf(ChatMsg("system", system), ChatMsg("user", user)), null, 0.95)
        val parts = CreativePrompts.parseBuilder(raw).filter { it.section != null }.associate { it.section!!.lowercase() to it.text.trim() }
        var text = parts["chapter"].orEmpty()
        if (text.isBlank()) {
            // No markers came back: keep the prose anyway.
            text = raw.replace(Regex("\\[\\[/?[^\\]]*]]"), "").trim()
        }
        if (text.split(Regex("\\s+")).size < 200 || Refusal.looksLike(text) || Refusal.looksLikeLeak(text)) {
            return "The model didn't write a proper chapter. Try again, or switch models."
        }
        val title = parts["title"]?.lines()?.firstOrNull()?.trim()?.trim('"', '*', '#', ' ')?.ifBlank { null } ?: "Chapter $n"
        val next = parts["next"].orEmpty().lines().map { it.trim().trimStart('-', '*', '•', ' ').replace(Regex("^\\d+[.)]\\s*"), "") }
            .filter { it.length > 4 }.take(3)
        val previous = st.chapters.lastOrNull()

        Store.updateStory(storyId) {
            it.copy(chapters = it.chapters + StoryChapter(title = title, text = text), suggestions = next, nextDirection = "")
        }
        // Fold the previous chapter into the running summary (cheap, on the backup model when there is one).
        if (previous != null) runCatching {
            val prompt = listOf(
                ChatMsg("system", "You keep the running summary of a private adult romance novel. Merge the new chapter into the summary: " +
                    "who did what, how the relationship changed, promises, secrets, injuries, places, names and details that must stay consistent. " +
                    "Plain, factual, under 500 words. Reply with only the updated summary."),
                ChatMsg("user", "Summary so far:\n${st.summary.ifBlank { "(none yet)" }}\n\nNew chapter \"${previous.title}\":\n${previous.text}"),
            )
            val sum = Ai.complete(prompt, null, 0.3, preferBackup = Store.backupReady).trim()
            if (sum.isNotBlank()) Store.updateStory(storyId) { it.copy(summary = sum) }
        }
        return null
    }
}
