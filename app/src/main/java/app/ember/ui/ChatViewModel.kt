package app.ember.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ember.ai.ChatMsg
import app.ember.ai.ContentBlockedException
import app.ember.ai.Endpoint
import app.ember.ai.LlmClient
import app.ember.ai.PromptBuilder
import app.ember.ai.PromptContext
import app.ember.ai.Refusal
import app.ember.ai.StreamEvent
import app.ember.ai.UsageTag
import app.ember.data.isGroup
import app.ember.data.Character
import app.ember.data.Chat
import app.ember.data.Message
import app.ember.data.Pin
import app.ember.data.Store
import app.ember.data.Summary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** How many of the newest messages always stay out of the summaries. */
private const val KEEP_RECENT = 10
/** In Light context mode, only this many newest messages stay word for word. */
private const val LIGHT_KEEP_RECENT = 6
/** The scene tracker refreshes every this many messages. */
private const val SCENE_EVERY = 6

class ChatViewModel : ViewModel() {
    var characterId by mutableStateOf("")
        private set
    private var localBusy by mutableStateOf(false)
    /** True while a reply for this character is being written, even one started before you left the screen. */
    val busy: Boolean get() = localBusy || app.ember.work.Generation.isRunning(characterId)
    var streamingId by mutableStateOf<String?>(null)
        private set
    /** True while a "thinking" model reasons before writing. */
    var thinking by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    var info by mutableStateOf<String?>(null)
    private var job: Job? = null
    private var summarising = false

    // ----- Write for me -----
    var wfmOpen by mutableStateOf(false)
    var wfmNotes by mutableStateOf("")
    var wfmChange by mutableStateOf("")
    var wfmDrafts by mutableStateOf(listOf<String>())
        private set
    /** Which model wrote each draft, so you can compare them as you swipe. */
    var wfmDraftModels by mutableStateOf(listOf<String>())
        private set
    var wfmSel by mutableStateOf(0)
    /** Draft length in words; 0 means Auto, -1 means not set yet. */
    var wfmWords by mutableStateOf(-1)
    var wfmBusy by mutableStateOf(false)
        private set
    var wfmError by mutableStateOf<String?>(null)
    private var wfmJob: Job? = null

    val character: Character? get() = Store.character(characterId)
    val chat: Chat? get() = Store.activeChat(characterId)

    fun bind(id: String) {
        if (characterId == id) return
        characterId = id
        val c = Store.character(id) ?: return
        if (Store.activeChat(id) == null) Store.newChat(c)
        if (wfmWords < 0) wfmWords = Store.settings.writeForMeWords
    }

    private fun context(chat: Chat): PromptContext? {
        val c = character ?: return null
        return PromptContext(
            character = c,
            persona = Store.personaFor(chat, c),
            chat = chat,
            ground = Store.groundRules,
            rules = Store.rules,
            preset = null,
            wfmRules = Store.wfmRules,
        )
    }

    private fun mainEndpoint() = Endpoint(Store.settings.baseUrl, Store.apiKey(), Store.settings.model)
    private fun backupEndpoint() = Endpoint(Store.settings.backupBaseUrl, Store.backupKey(), Store.settings.backupModel)

    /** The model this chat writes with first, and the one it falls back to (if any). */
    private fun endpoints(chat: Chat): Pair<Endpoint, Endpoint?>? {
        val wantBackup = chat.useBackup && Store.backupReady
        if (!wantBackup && !Store.hasApiKey) return null
        val primary = if (wantBackup) backupEndpoint() else mainEndpoint()
        val fallback = if (!wantBackup && chat.autoSwitch && Store.backupReady) backupEndpoint() else null
        return primary to fallback
    }

    private fun temperature(chat: Chat) = chat.temperature ?: Store.settings.temperature

    fun newChat() {
        stop()
        val c = character ?: return
        Store.newChat(c)
        error = null; info = null
        resetWriteForMe()
    }

    fun updateChat(f: (Chat) -> Chat) {
        val ch = chat ?: return
        Store.updateChat(ch.id) { f(it) }
    }

    /** 0 means Auto: the model picks the length to fit the scene. */
    /** The spicy button: write this chat with the backup model (e.g. DeepSeek) until switched off. */
    fun toggleSpicy() {
        if (!Store.backupReady) { info = "Set up a backup model in Settings to use the spicy button."; return }
        val on = !(chat?.useBackup ?: false)
        updateChat { it.copy(useBackup = on) }
        info = if (on) "Spicy on: ${Store.settings.backupModel} writes this chat." else "Spicy off: back to ${Store.settings.model}."
    }

    fun setLength(words: Int) = updateChat { it.copy(replyWords = if (words <= 0) 0 else words.coerceIn(30, 2000)) }

    /** Sends your message (if any) and gets the character's reply. Empty send lets the character carry on. */
    fun send(text: String) {
        if (busy) return
        val ch = chat ?: return
        val t = text.trim()
        val current = if (t.isNotEmpty()) {
            ch.copy(messages = ch.messages + Message(fromUser = true, versions = listOf(t))).also { Store.putChat(it) }
        } else ch
        val reply = Message(fromUser = false, versions = listOf(""))
        Store.putChat(current.copy(messages = current.messages + reply), persist = false)
        runReply(reply.id, 0, current.messages, base = "", extra = null)
    }

    /**
     * "Move it along": the character writes a new reply that pushes the story forward the way you picked
     * (a time skip, a new place, a twist…). [chapter] marks it as a new chapter for book export.
     */
    fun nudge(instruction: String, chapter: Boolean) {
        if (busy || instruction.isBlank()) return
        val ch = chat ?: return
        val reply = Message(fromUser = false, versions = listOf(""), chapter = chapter)
        Store.putChat(ch.copy(messages = ch.messages + reply), persist = false)
        runReply(
            reply.id, 0, ch.messages, base = "",
            extra = "Story direction from the user for this reply: ${instruction.trim()} " +
                "This reply moves the story on, so it doesn't need to answer the last message line by line. " +
                "Still write only ${character?.name ?: "the character"}'s part${if (character?.isGroup == true) "s (the characters of this card)" else ""} and end where the user can respond.",
        )
    }

    /** Writes a new version of the last reply; older versions stay available to swipe back to. */
    fun regenerate() {
        if (busy) return
        val ch = chat ?: return
        val last = ch.messages.lastOrNull() ?: return
        if (last.fromUser) { send(""); return }
        val idx = last.versions.size
        Store.updateMessage(ch.id, last.id, persist = false) { it.copy(versions = it.versions + "", sel = idx) }
        runReply(last.id, idx, ch.messages.dropLast(1), base = "", extra = null)
    }

    /**
     * Writes a new version of the last reply guided by your note: either revising the current
     * version, or starting it over from scratch with the note in mind. Old versions stay swipeable.
     */
    fun rewriteLast(note: String, fromScratch: Boolean) {
        if (busy || note.isBlank()) return
        val ch = chat ?: return
        val last = ch.messages.lastOrNull() ?: return
        if (last.fromUser) return
        val extra = if (fromScratch) {
            "Write this reply from scratch, keeping this direction from the user in mind: ${note.trim()}"
        } else {
            "Here is the current version of this reply:\n${last.text}\n\n" +
                "Rewrite it with these changes from the user: ${note.trim()}\n" +
                "Keep everything else as close to the current version as you can, unless the changes say otherwise."
        }
        val idx = last.versions.size
        Store.updateMessage(ch.id, last.id, persist = false) { it.copy(versions = it.versions + "", sel = idx) }
        runReply(last.id, idx, ch.messages.dropLast(1), base = "", extra = extra)
    }

    /** Asks the character to keep writing the last reply from where it stopped. */
    fun continueLast() {
        if (busy) return
        val ch = chat ?: return
        val last = ch.messages.lastOrNull() ?: return
        if (last.fromUser) return
        val base = last.text.let { if (it.isEmpty() || it.last().isWhitespace()) it else "$it " }
        val name = character?.name ?: "the character"
        runReply(
            last.id, last.sel, ch.messages, base = base,
            extra = "Continue $name's previous reply exactly where it stopped. Do not repeat anything already written; write only the continuation.",
        )
    }

    fun stop() {
        job?.cancel()
        wfmJob?.cancel()
    }

    fun setVersion(messageId: String, sel: Int) {
        val id = chat?.id ?: return
        Store.updateMessage(id, messageId, persist = true) { it.copy(sel = sel.coerceIn(0, it.versions.size - 1)) }
    }

    fun editMessage(messageId: String, text: String) {
        val id = chat?.id ?: return
        Store.updateMessage(id, messageId, persist = true) { m ->
            m.copy(versions = m.versions.toMutableList().also { it[m.sel] = text })
        }
    }

    fun deleteMessage(messageId: String) = updateChat { ch ->
        val i = ch.messages.indexOfFirst { it.id == messageId }
        if (i < 0) return@updateChat ch
        val summaries = ch.summaries.mapNotNull { s ->
            when {
                i >= s.to -> s
                i < s.from -> s.copy(from = s.from - 1, to = s.to - 1)
                s.to - s.from <= 1 -> null
                else -> s.copy(to = s.to - 1)
            }
        }
        ch.copy(messages = ch.messages.filter { it.id != messageId }, summaries = summaries)
    }

    /** Deletes every message after this one. */
    fun rewindTo(messageId: String) = updateChat { ch ->
        val i = ch.messages.indexOfFirst { it.id == messageId }
        if (i < 0) return@updateChat ch
        val size = i + 1
        ch.copy(messages = ch.messages.take(size), summaries = ch.summaries.filter { it.to <= size }, sceneAt = minOf(ch.sceneAt, size),
            timeline = ch.timeline.filter { m -> ch.messages.take(size).any { it.id == m.messageId } })
    }

    fun pin(text: String) {
        if (text.isBlank()) return
        updateChat { it.copy(pins = it.pins + Pin(text = text.trim())) }
        info = "Pinned to memory."
    }

    // ----- the character's replies -----

    private fun runReply(messageId: String, idx: Int, history: List<Message>, base: String, extra: String?) {
        val ch = chat ?: return
        val cid = ch.id
        val ctx = context(ch) ?: return
        val eps = endpoints(ch)
        if (eps == null) {
            error = "Add your API key in Settings first."
            cleanup(cid, messageId, idx, base, "")
            return
        }
        val prompt = PromptBuilder.forCharacter(ctx, history, ch.replyWords, extra)
        Store.lastPrompt = PromptBuilder.describe(prompt)
        // A length typed in a note ("make it 700 words") raises the runaway stop so the reply isn't cut off.
        val asked = requestedWords(extra)
        val limit = wordLimit(asked ?: ch.replyWords, 500)
        val maxTokens = PromptBuilder.maxTokens(ch.replyWords)
        val temp = temperature(ch)

        localBusy = true; error = null; info = null
        streamingId = messageId
        val name = character?.name?.ifBlank { null } ?: "The reply"
        // Runs app-wide (not tied to this screen), so leaving the chat or the app never cuts the reply off.
        job = app.ember.work.Generation.launch(
            characterId, UsageTag(characterId), label = "Writing $name's reply…",
            result = { Store.chat(cid)?.messages?.firstOrNull { it.id == messageId }?.text?.takeIf { it.isNotBlank() } },
        ) {
            var written = ""
            fun show(text: String) {
                written = text
                val full = base + if (base.isEmpty()) text.trimStart() else text
                Store.updateMessage(cid, messageId, persist = false) { m ->
                    if (idx < m.versions.size) m.copy(versions = m.versions.toMutableList().also { it[idx] = full }) else m
                }
            }
            try {
                val (primary, fallback) = eps
                val blocked = try {
                    streamInto(cid, primary, prompt, maxTokens, temp, limit, ::show)
                    written.isBlank() || Refusal.looksLike(written) || Refusal.looksLikeLeak(written)
                } catch (e: ContentBlockedException) {
                    if (fallback == null) throw e
                    true
                }
                var used = primary
                val leaked = written.isNotBlank() && Refusal.looksLikeLeak(written)
                if (blocked && fallback != null) {
                    show("")
                    streamInto(cid, fallback, prompt, maxTokens, temp, limit, ::show)
                    used = fallback
                    info = if (leaked) "${primary.model.substringAfter('/')} wrote its own notes instead of a reply, so the backup model wrote this one."
                    else "The main model refused, so the backup model wrote this reply."
                } else if (blocked && leaked) {
                    // No backup: one more try with the same model.
                    show("")
                    streamInto(cid, primary, prompt, maxTokens, temp, limit, ::show)
                    if (Refusal.looksLikeLeak(written)) { show(""); error = "${primary.model.substringAfter('/')} keeps writing its notes instead of a reply. Try another model." }
                } else if (blocked) {
                    info = "This looks like a refusal. Add a backup model in Settings to retry these automatically."
                }
                // Well short (under 80%) of the length you chose: ask once more at the right length.
                val got = wordCount(written)
                if (base.isEmpty() && ch.replyWords > 0 && got in 1 until ch.replyWords * 8 / 10) {
                    val again = PromptBuilder.forCharacter(
                        ctx, history, ch.replyWords,
                        (extra?.let { "$it\n" } ?: "") + "A first attempt came out at only $got words, far too short. " +
                            "Write this reply at about ${ch.replyWords} words: the same events, with deeper body language, noticing and inner thoughts.",
                    )
                    show("")
                    streamInto(cid, used, again, maxTokens, temp, limit, ::show)
                }
                if (written.isBlank()) error = "The model sent back an empty reply. Try again."
            } catch (e: CancellationException) {
                // Stopped by you: keep whatever was written so far.
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong."
            } finally {
                cleanup(cid, messageId, idx, base, written)
                Store.persistChat(cid)
                localBusy = false
                streamingId = null
                thinking = false
            }
            maybeSummarise(cid)
            maybeUpdateScene(cid)
        }
    }

    /** Streams one request, passing the text so far to [onText], and adds the token usage to the chat. */
    /** Safety stop: Auto uses [autoLimit] words; a fixed length allows 60% extra. */
    private fun wordLimit(words: Int, autoLimit: Int) = if (words <= 0) autoLimit else (words * 1.6).toInt() + 40

    /** "700 words", "make it 700", "~700w" in a note: the length asked for, if any. */
    private fun requestedWords(text: String?): Int? = text?.let {
        Regex("(\\d{2,4})\\s*(?:words?|w\\b)", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { n -> n in 30..3000 }
    }

    private class RunawayStop : Exception()

    /**
     * Streams one request, passing the text so far to [onText], and adds the token usage to the chat.
     * If the reply runs far past [wordLimit] (a model that won't stop), it's cut off there.
     * A reply that was cut off is trimmed back to its last complete sentence.
     */
    private suspend fun streamInto(
        chatId: String, ep: Endpoint, prompt: List<ChatMsg>, maxTokens: Int?, temp: Double,
        wordLimit: Int, onText: (String) -> Unit,
    ) {
        val buf = StringBuilder()
        var cut = false
        try {
            LlmClient.stream(ep, prompt, maxTokens, temp).collect { ev ->
                when (ev) {
                    is StreamEvent.Text -> {
                        buf.append(ev.text)
                        // The hidden <beat> note (the model reading the moment) shows as "Thinking…", never as text.
                        val shown = withoutBeat(buf.toString())
                        thinking = shown == null
                        if (shown != null) {
                            onText(shown)
                            if (shown.count { it == ' ' || it == '\n' } > wordLimit) throw RunawayStop()
                        }
                    }
                    is StreamEvent.Usage -> addUsage(chatId, ev.input, ev.output)
                    is StreamEvent.Thinking -> thinking = true
                }
            }
        } catch (e: RunawayStop) {
            cut = true
        }
        // If a model never closed its note, keep what it wrote rather than lose the reply.
        var text = (withoutBeat(buf.toString()) ?: buf.toString().replace(Regex("</?beat>", RegexOption.IGNORE_CASE), "")).trimEnd()
        // Only a reply we cut off ourselves is trimmed. A finished reply is kept whole, even if its last line
        // has no full stop (a text message, a trailing-off line).
        if (text.isNotEmpty() && cut && !endsCleanly(text)) text = trimToLastSentence(text)
        val laidOut = paragraphize(text)
        onText(laidOut)
    }

    /**
     * The reply without its <beat>…</beat> planning note. Returns null while the note is still being
     * written (nothing to show yet). Text without a note is returned as it is.
     */
    private fun withoutBeat(raw: String): String? {
        val t = raw.trimStart()
        if (t.isEmpty()) return ""
        // Still could be the start of "<beat>".
        if ("<beat>".startsWith(t.take(6).lowercase()) && t.length < 6) return null
        // Planning notes some models write as text: <beat>, <think>, <thinking>, <reasoning>, <analysis>.
        val tag = Regex("^<(beat|think|thinking|reasoning|analysis)>", RegexOption.IGNORE_CASE).find(t)?.groupValues?.get(1)
        if (tag != null) {
            val close = "</$tag>"
            val end = t.indexOf(close, ignoreCase = true)
            return if (end < 0) null else withoutBeat(t.substring(end + close.length))
        }
        if (t.length < 12 && "<thinking>".startsWith(t.lowercase())) return null
        // A note placed somewhere else is removed too.
        return t.replace(Regex("<(beat|think|thinking|reasoning|analysis)>[\\s\\S]*?</\\1>\\s*", RegexOption.IGNORE_CASE), "")
    }

    /**
     * If a reply arrives as one solid block, splits it into paragraphs: a new one at each line of
     * dialogue, and after every three sentences of narration. Replies that already have paragraphs are left alone.
     */
    private fun paragraphize(t: String): String {
        if (t.contains('\n') || t.split(' ').size < 70) return t
        val sentences = Regex("(?<=[.!?…][\"”’_*)]?)\\s+(?=[\"“_*A-Z])").split(t).map { it.trim() }.filter { it.isNotEmpty() }
        if (sentences.size < 4) return t
        val paras = ArrayList<StringBuilder>()
        var count = 0
        var talk = false
        for (sent in sentences) {
            val speaks = sent.contains('"') || sent.contains('“')
            val cur = paras.lastOrNull()
            // A speaker's lines stay together; narration groups up to three sentences.
            val join = cur != null && (if (speaks) talk && count < 3 else count < 3 && !(talk && count >= 2))
            if (join) { cur!!.append(' ').append(sent); count++; talk = talk || speaks }
            else { paras.add(StringBuilder(sent)); count = 1; talk = speaks }
        }
        return paras.joinToString("\n\n")
    }

    private fun endsCleanly(t: String): Boolean = t.last() in ".!?…\"”’_*)"

    /** Drops an unfinished sentence at the end. */
    private fun trimToLastSentence(t: String): String {
        val re = Regex("[.!?…][\"”’_*)]*(?=\\s|$)")
        val last = re.findAll(t).lastOrNull() ?: return t
        val trimmed = t.substring(0, last.range.last + 1).trimEnd()
        return if (trimmed.length > t.length / 3) trimmed else t
    }

    private fun addUsage(chatId: String, input: Long, output: Long) {
        val c = Store.chat(chatId) ?: return
        Store.putChat(c.copy(tokensIn = c.tokensIn + input, tokensOut = c.tokensOut + output), persist = false, touch = false)
    }

    /** Removes an empty reply or version left behind by a failed request. */
    private fun cleanup(chatId: String, messageId: String, idx: Int, base: String, written: String) {
        if (base.isNotEmpty() || written.isNotBlank()) return
        val ch = Store.chat(chatId) ?: return
        val m = ch.messages.firstOrNull { it.id == messageId } ?: return
        if (m.versions.size <= 1) {
            Store.putChat(ch.copy(messages = ch.messages.filter { it.id != messageId }))
        } else {
            val versions = m.versions.toMutableList().also { if (idx < it.size) it.removeAt(idx) }
            Store.updateMessage(chatId, messageId, persist = true) {
                it.copy(versions = versions, sel = (idx - 1).coerceIn(0, versions.size - 1))
            }
        }
    }

    // ----- what next? -----

    /** Three directions for the next reply: (title, description). */
    var whatNext by mutableStateOf(listOf<Pair<String, String>>())
        private set
    var whatNextBusy by mutableStateOf(false)
        private set
    var whatNextError by mutableStateOf<String?>(null)
        private set

    fun suggestWhatNext(more: Boolean) {
        if (whatNextBusy) return
        val ch = chat ?: return
        val ctx = context(ch) ?: return
        val ep = if (Store.backupReady && Store.settings.creativeUseBackup) backupEndpoint()
            else if (Store.hasApiKey) mainEndpoint() else { whatNextError = "Add your API key in Settings first."; return }
        val avoid = if (more) whatNext.map { "${it.first} | ${it.second}" } else emptyList()
        if (!more) whatNext = emptyList()
        whatNextBusy = true; whatNextError = null
        app.ember.work.Generation.launchQuiet(UsageTag(characterId)) {
            try {
                val r = LlmClient.complete(ep, PromptBuilder.forWhatNext(ctx, ch.messages.takeLast(8), avoid), null, 1.0)
                val got = r.text.lines().map { it.trim().replace(Regex("^(\\d+[.)]|[-*•])\\s*"), "").replace("**", "") }
                    .filter { "|" in it }
                    .map { it.substringBefore("|").trim() to it.substringAfter("|").trim() }
                    .filter { it.first.isNotBlank() && it.second.isNotBlank() }.take(3)
                if (got.isEmpty()) whatNextError = "No ideas came back. Try again."
                whatNext = if (more) whatNext + got else got
            } catch (e: Exception) {
                whatNextError = e.message ?: "Couldn't get ideas."
            } finally {
                whatNextBusy = false
            }
        }
    }

    fun clearWhatNext() { whatNext = emptyList(); whatNextError = null }

    // ----- side by side -----

    /** Choosing the second model for "Side by side". */
    var pickingCompare by mutableStateOf(false)
    var compareModel by mutableStateOf<String?>(null)
        private set
    var compareText by mutableStateOf("")
        private set
    var compareBusy by mutableStateOf(false)
        private set
    var compareError by mutableStateOf<String?>(null)
        private set
    private var compareJob: Job? = null

    /** Writes the last reply again with [model] (on your main provider), next to the current one, without changing the chat. */
    fun compare(model: String) {
        if (busy || compareBusy) return
        val ch = chat ?: return
        val last = ch.messages.lastOrNull() ?: return
        if (last.fromUser || !Store.hasApiKey) return
        val ctx = context(ch) ?: return
        val prompt = PromptBuilder.forCharacter(ctx, ch.messages.dropLast(1), ch.replyWords, null)
        val ep = Endpoint(Store.settings.baseUrl, Store.apiKey(), model)
        compareModel = model; compareText = ""; compareError = null; compareBusy = true
        compareJob = viewModelScope.launch(UsageTag(characterId)) {
            try {
                streamInto(ch.id, ep, prompt, null, temperature(ch), wordLimit(ch.replyWords, 500)) { compareText = it.trimStart() }
                if (compareText.isBlank()) compareError = "$model sent back an empty reply."
            } catch (e: CancellationException) {
                // closed
            } catch (e: Exception) {
                compareError = e.message ?: "Something went wrong."
            } finally {
                compareBusy = false; thinking = false
                Store.persistChat(ch.id)
            }
        }
    }

    /** Keeps the compared reply as a new swipe on the last message (and shows it), or just closes. */
    fun closeCompare(keepNew: Boolean) {
        compareJob?.cancel()
        val ch = chat
        val last = ch?.messages?.lastOrNull()
        if (keepNew && ch != null && last != null && !last.fromUser && compareText.isNotBlank()) {
            val text = compareText
            Store.updateMessage(ch.id, last.id, persist = true) { it.copy(versions = it.versions + text, sel = it.versions.size) }
        }
        compareModel = null; compareText = ""; compareError = null; compareBusy = false
    }

    // ----- scene tracker -----

    private var sceneBusy = false
    var sceneUpdating by mutableStateOf(false)
        private set

    /**
     * Keeps the scene card current. To stay cheap it only runs every [SCENE_EVERY] messages, reads just
     * the last few messages, and uses the backup model (DeepSeek) when there is one.
     */
    private fun maybeUpdateScene(chatId: String, force: Boolean = false) {
        if (sceneBusy) return
        val ch = Store.chat(chatId) ?: return
        if (!ch.sceneOn && !force) return
        val due = ch.messages.size - ch.sceneAt >= SCENE_EVERY || (ch.scene.isBlank() && ch.messages.size >= 2)
        if (!due && !force) return
        val ctx = context(ch) ?: return
        val ep = if (Store.backupReady) backupEndpoint() else if (Store.hasApiKey) mainEndpoint() else return
        val prompt = PromptBuilder.forScene(ctx, ch.scene, ch.messages.takeLast(6), ch.meters, ch.timeline.map { it.text })
        sceneBusy = true; sceneUpdating = true
        app.ember.work.Generation.launchQuiet(UsageTag(characterId)) {
            try {
                val r = LlmClient.complete(ep, prompt, null, 0.3)
                val lines = r.text.trim().lines().map { it.trim().trimStart('-', '*', ' ').replace("**", "") }.filter { it.isNotEmpty() }
                fun valueOf(key: String) = lines.firstOrNull { it.startsWith("$key:", ignoreCase = true) }?.substringAfter(':')?.trim()
                val sceneText = lines.filter { l -> listOf("Place", "Time", "Wearing", "Mood").any { l.startsWith("$it:", ignoreCase = true) } }
                    .joinToString("\n")
                    .ifBlank { lines.filter { l -> app.ember.data.METERS.none { l.startsWith("$it:", true) } && !l.startsWith("Milestone:", true) }.take(4).joinToString("\n") }
                val meters = app.ember.data.METERS.mapNotNull { k -> valueOf(k)?.let { v -> Regex("\\d+").find(v)?.value?.toIntOrNull()?.coerceIn(0, 100)?.let { k to it } } }.toMap()
                val milestone = valueOf("Milestone")?.trim('.', '"', ' ')?.takeIf { it.isNotBlank() && !it.equals("none", true) && it.length <= 60 }
                if (sceneText.isNotBlank() && !Refusal.looksLike(sceneText)) {
                    Store.updateChat(chatId) { c ->
                        val anchor = c.messages.lastOrNull { !it.fromUser } ?: c.messages.lastOrNull()
                        val newTimeline = if (milestone != null && anchor != null && c.timeline.none { it.text.equals(milestone, true) })
                            c.timeline + app.ember.data.Milestone(text = milestone, messageId = anchor.id, at = c.messages.size)
                        else c.timeline
                        c.copy(scene = sceneText, sceneAt = c.messages.size, meters = if (meters.size == 4) meters else c.meters, timeline = newTimeline)
                    }
                }
            } catch (e: Exception) {
                if (force) error = e.message
            } finally {
                sceneBusy = false; sceneUpdating = false
            }
        }
    }

    fun updateSceneNow() { chat?.id?.let { maybeUpdateScene(it, force = true) } }

    // ----- memory -----

    /** Every [Settings.summariseEvery] messages, folds the oldest unsummarised block into a summary. */
    private fun maybeSummarise(chatId: String) {
        if (summarising) return
        // Light context keeps only the last 6 messages word for word and summarises every 10.
        val light = Store.settings.contextChars <= 0
        val every = if (light) 10 else Store.settings.summariseEvery
        val keep = if (light) LIGHT_KEEP_RECENT else KEEP_RECENT
        if (every <= 0) return
        summarising = true
        app.ember.work.Generation.launchQuiet(UsageTag(characterId)) {
            try {
                // Catch up a few blocks at a time if the chat is far behind (e.g. after switching to Light).
                repeat(3) {
                    val ch = Store.chat(chatId) ?: return@launchQuiet
                    val start = ch.coveredUntil
                    val end = start + every
                    if (ch.messages.size - keep < end) return@launchQuiet
                    val ctx = context(ch) ?: return@launchQuiet
                    val eps = endpoints(ch) ?: return@launchQuiet
                    val prompt = PromptBuilder.forSummary(ctx, ch.messages.subList(start, end))
                    val result = try {
                        LlmClient.complete(eps.first, prompt, null, 0.4)
                            .takeIf { it.text.isNotBlank() && !Refusal.looksLike(it.text) }
                            ?: eps.second?.let { LlmClient.complete(it, prompt, null, 0.4) }
                    } catch (e: ContentBlockedException) {
                        eps.second?.let { LlmClient.complete(it, prompt, null, 0.4) }
                    }
                    if (result == null || result.text.isBlank() || Refusal.looksLike(result.text)) return@launchQuiet
                    Store.updateChat(chatId) { c ->
                        if (c.coveredUntil != start) c
                        else c.copy(
                            summaries = c.summaries + Summary(from = start, to = end, text = result.text),
                            tokensIn = c.tokensIn + result.input, tokensOut = c.tokensOut + result.output,
                        )
                    }
                }
            } catch (e: Exception) {
                // Summaries are best effort; the next reply tries again.
            } finally {
                summarising = false
            }
        }
    }

    /** Writes a summary for the next block right away (from the Memory screen). */
    fun summariseNow() {
        val ch = chat ?: return
        if (ch.messages.size - ch.coveredUntil < 2) { info = "Nothing new to summarise yet."; return }
        val every = Store.settings.summariseEvery.coerceAtLeast(2)
        val end = minOf(ch.coveredUntil + every, ch.messages.size)
        val start = ch.coveredUntil
        val ctx = context(ch) ?: return
        val eps = endpoints(ch) ?: run { error = "Add your API key in Settings first."; return }
        summarising = true
        info = "Writing a summary…"
        app.ember.work.Generation.launchQuiet(UsageTag(characterId)) {
            try {
                val prompt = PromptBuilder.forSummary(ctx, ch.messages.subList(start, end))
                val r = LlmClient.complete(eps.first, prompt, null, 0.4)
                Store.updateChat(ch.id) { c ->
                    c.copy(summaries = c.summaries + Summary(from = start, to = end, text = r.text),
                        tokensIn = c.tokensIn + r.input, tokensOut = c.tokensOut + r.output)
                }
                info = "Summary added."
            } catch (e: Exception) {
                error = e.message
                info = null
            } finally {
                summarising = false
            }
        }
    }

    // ----- Write for me -----

    fun openWriteForMe() {
        wfmOpen = true
        if (wfmWords < 0) wfmWords = Store.settings.writeForMeWords
    }

    fun resetWriteForMe() {
        wfmJob?.cancel()
        wfmNotes = ""; wfmChange = ""; wfmDrafts = emptyList(); wfmDraftModels = emptyList(); wfmSel = 0; wfmError = null
    }

    /** Writes a new draft from your notes, or a rewrite of the current draft when [rewrite] is true. */
    fun draft(rewrite: Boolean) {
        if (wfmBusy || busy) return
        val ch = chat ?: return
        val ctx = context(ch) ?: return
        val chatEps = endpoints(ch) ?: run { wfmError = "Add your API key in Settings first."; return }
        // "Model for Write for me": the backup writes your drafts, with the main model as its fallback.
        val s = Store.settings
        val eps = if (s.wfmModel.isNotBlank() && Store.hasApiKey && s.baseUrl.contains("openrouter", ignoreCase = true)) {
            Endpoint(s.baseUrl, Store.apiKey(), s.wfmModel) to (if (Store.backupReady) backupEndpoint() else null)
        } else if (s.wfmUseBackup && Store.backupReady) {
            backupEndpoint() to (if (Store.hasApiKey) mainEndpoint() else null)
        } else chatEps
        val current = wfmDrafts.getOrNull(wfmSel)
        // A length typed in the notes or the change box ("make it 700 words") counts as the length you chose.
        val words = (if (rewrite) requestedWords(wfmChange) else null) ?: requestedWords(wfmNotes) ?: wfmWords
        val wfmLimit = wordLimit(words, 1200)
        val prompt = PromptBuilder.forUser(
            ctx, ch.messages, words, wfmNotes,
            draft = if (rewrite) current else null,
            change = if (rewrite) wfmChange else null,
        )
        Store.lastPrompt = PromptBuilder.describe(prompt)
        val idx = wfmDrafts.size
        wfmDrafts = wfmDrafts + ""
        wfmDraftModels = wfmDraftModels.take(idx) + eps.first.model
        wfmSel = idx
        wfmBusy = true; wfmError = null
        wfmJob = app.ember.work.Generation.launch(
            characterId, UsageTag(characterId), label = "Writing your draft…",
            result = { wfmDrafts.getOrNull(idx)?.takeIf { it.isNotBlank() } },
        ) {
            var written = ""
            fun show(t: String) {
                written = t.trimStart()
                wfmDrafts = wfmDrafts.toMutableList().also { if (idx < it.size) it[idx] = written }
            }
            try {
                val (primary, fallback) = eps
                val blocked = try {
                    streamInto(ch.id, primary, prompt, PromptBuilder.maxTokens(words), temperature(ch), wfmLimit, ::show)
                    written.isBlank() || Refusal.looksLike(written) || Refusal.looksLikeLeak(written)
                } catch (e: ContentBlockedException) {
                    if (fallback == null) throw e
                    true
                }
                var used = primary
                if (blocked && fallback != null) {
                    show("")
                    streamInto(ch.id, fallback, prompt, PromptBuilder.maxTokens(words), temperature(ch), wfmLimit, ::show)
                    used = fallback
                    wfmDraftModels = wfmDraftModels.toMutableList().also { if (idx < it.size) it[idx] = fallback.model }
                }
                // Well short (under 80%) of the length you chose: rewrite once at the right length, same beats.
                val got = wordCount(written)
                if (words > 0 && got in 1 until words * 8 / 10) {
                    val again = PromptBuilder.forUser(
                        ctx, ch.messages, words, wfmNotes, draft = written,
                        change = "it is only $got words; make it about $words words by deepening her body language, what she notices and her thoughts, keeping every beat and adding no new actions or lines",
                    )
                    show("")
                    streamInto(ch.id, used, again, PromptBuilder.maxTokens(words), temperature(ch), wfmLimit, ::show)
                }
                if (rewrite) wfmChange = ""
            } catch (e: CancellationException) {
                // stopped
            } catch (e: Exception) {
                wfmError = e.message ?: "Something went wrong."
            } finally {
                if (written.isBlank()) {
                    wfmDrafts = wfmDrafts.toMutableList().also { if (idx < it.size) it.removeAt(idx) }
                    wfmDraftModels = wfmDraftModels.toMutableList().also { if (idx < it.size) it.removeAt(idx) }
                    wfmSel = (wfmDrafts.size - 1).coerceAtLeast(0)
                }
                Store.persistChat(ch.id)
                wfmBusy = false
            }
        }
    }

    fun editDraft(text: String) {
        wfmDrafts = wfmDrafts.toMutableList().also { if (wfmSel < it.size) it[wfmSel] = text }
    }

    /** Sends the chosen draft as your message and gets the character's reply. */
    fun sendDraft() {
        val text = wfmDrafts.getOrNull(wfmSel)?.trim().orEmpty()
        if (text.isEmpty() || busy) return
        wfmOpen = false
        resetWriteForMe()
        send(text)
    }
}
