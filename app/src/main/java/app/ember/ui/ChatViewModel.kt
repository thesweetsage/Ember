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

class ChatViewModel : ViewModel() {
    var characterId by mutableStateOf("")
        private set
    var busy by mutableStateOf(false)
        private set
    var streamingId by mutableStateOf<String?>(null)
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
        ch.copy(messages = ch.messages.take(size), summaries = ch.summaries.filter { it.to <= size })
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
        val maxTokens = PromptBuilder.maxTokens(ch.replyWords)
        val temp = temperature(ch)

        busy = true; error = null; info = null
        streamingId = messageId
        job = viewModelScope.launch {
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
                    streamInto(cid, primary, prompt, maxTokens, temp, ::show)
                    Refusal.looksLike(written)
                } catch (e: ContentBlockedException) {
                    if (fallback == null) throw e
                    true
                }
                if (blocked && fallback != null) {
                    show("")
                    streamInto(cid, fallback, prompt, maxTokens, temp, ::show)
                    info = "The main model refused, so the backup model wrote this reply."
                } else if (blocked) {
                    info = "This looks like a refusal. Add a backup model in Settings to retry these automatically."
                }
                if (written.isBlank()) error = "The model sent back an empty reply. Try again."
            } catch (e: CancellationException) {
                // Stopped by you: keep whatever was written so far.
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong."
            } finally {
                cleanup(cid, messageId, idx, base, written)
                Store.persistChat(cid)
                busy = false
                streamingId = null
            }
            maybeSummarise(cid)
        }
    }

    /** Streams one request, passing the text so far to [onText], and adds the token usage to the chat. */
    private suspend fun streamInto(
        chatId: String, ep: Endpoint, prompt: List<ChatMsg>, maxTokens: Int, temp: Double, onText: (String) -> Unit,
    ) {
        val buf = StringBuilder()
        LlmClient.stream(ep, prompt, maxTokens, temp).collect { ev ->
            when (ev) {
                is StreamEvent.Text -> { buf.append(ev.text); onText(buf.toString()) }
                is StreamEvent.Usage -> addUsage(chatId, ev.input, ev.output)
            }
        }
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

    // ----- memory -----

    /** Every [Settings.summariseEvery] messages, folds the oldest unsummarised block into a summary. */
    private fun maybeSummarise(chatId: String) {
        if (summarising) return
        val every = Store.settings.summariseEvery
        if (every <= 0) return
        val ch = Store.chat(chatId) ?: return
        val start = ch.coveredUntil
        val end = start + every
        if (ch.messages.size - KEEP_RECENT < end) return
        val ctx = context(ch) ?: return
        val eps = endpoints(ch) ?: return
        summarising = true
        viewModelScope.launch {
            try {
                val block = ch.messages.subList(start, end)
                val prompt = PromptBuilder.forSummary(ctx, block)
                val result = try {
                    LlmClient.complete(eps.first, prompt, 600, 0.4)
                        .takeIf { !Refusal.looksLike(it.text) } ?: eps.second?.let { LlmClient.complete(it, prompt, 600, 0.4) }
                } catch (e: ContentBlockedException) {
                    eps.second?.let { LlmClient.complete(it, prompt, 600, 0.4) }
                }
                if (result != null && result.text.isNotBlank() && !Refusal.looksLike(result.text)) {
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
        viewModelScope.launch {
            try {
                val prompt = PromptBuilder.forSummary(ctx, ch.messages.subList(start, end))
                val r = LlmClient.complete(eps.first, prompt, 600, 0.4)
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
        wfmNotes = ""; wfmChange = ""; wfmDrafts = emptyList(); wfmSel = 0; wfmError = null
    }

    /** Writes a new draft from your notes, or a rewrite of the current draft when [rewrite] is true. */
    fun draft(rewrite: Boolean) {
        if (wfmBusy || busy) return
        val ch = chat ?: return
        val ctx = context(ch) ?: return
        val eps = endpoints(ch) ?: run { wfmError = "Add your API key in Settings first."; return }
        val current = wfmDrafts.getOrNull(wfmSel)
        val prompt = PromptBuilder.forUser(
            ctx, ch.messages, wfmWords, wfmNotes,
            draft = if (rewrite) current else null,
            change = if (rewrite) wfmChange else null,
        )
        val idx = wfmDrafts.size
        wfmDrafts = wfmDrafts + ""
        wfmSel = idx
        wfmBusy = true; wfmError = null
        wfmJob = viewModelScope.launch {
            var written = ""
            fun show(t: String) {
                written = t.trimStart()
                wfmDrafts = wfmDrafts.toMutableList().also { if (idx < it.size) it[idx] = written }
            }
            try {
                val (primary, fallback) = eps
                val blocked = try {
                    streamInto(ch.id, primary, prompt, PromptBuilder.maxTokens(wfmWords), temperature(ch), ::show)
                    Refusal.looksLike(written)
                } catch (e: ContentBlockedException) {
                    if (fallback == null) throw e
                    true
                }
                if (blocked && fallback != null) {
                    show("")
                    streamInto(ch.id, fallback, prompt, PromptBuilder.maxTokens(wfmWords), temperature(ch), ::show)
                }
                if (rewrite) wfmChange = ""
            } catch (e: CancellationException) {
                // stopped
            } catch (e: Exception) {
                wfmError = e.message ?: "Something went wrong."
            } finally {
                if (written.isBlank()) {
                    wfmDrafts = wfmDrafts.toMutableList().also { if (idx < it.size) it.removeAt(idx) }
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
