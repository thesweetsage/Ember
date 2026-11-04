package app.ember.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ember.ai.LlmClient
import app.ember.ai.PromptBuilder
import app.ember.data.Character
import app.ember.data.Chat
import app.ember.data.Message
import app.ember.data.Store
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {
    var characterId by mutableStateOf("")
        private set
    var chatId by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var streamingId by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
    private var job: Job? = null

    val character: Character? get() = Store.character(characterId)
    val chat: Chat? get() = chatId?.let { Store.chat(it) }

    fun bind(id: String) {
        val current = chatId
        if (characterId == id && current != null && Store.chat(current) != null) return
        characterId = id
        val c = Store.character(id) ?: return
        chatId = (Store.latestChat(id) ?: Store.newChat(c)).id
    }

    fun newChat() {
        stop()
        val c = character ?: return
        chatId = Store.newChat(c).id
        error = null
    }

    fun setLength(words: Int) {
        val ch = chat ?: return
        Store.putChat(ch.copy(replyWords = words.coerceIn(30, 2000)))
    }

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
        runStream(reply.id, 0, current.messages, base = "", extra = null)
    }

    /** Writes a new version of the last reply; older versions stay available to swipe back to. */
    fun regenerate() {
        if (busy) return
        val ch = chat ?: return
        val last = ch.messages.lastOrNull() ?: return
        if (last.fromUser) { send(""); return }
        val idx = last.versions.size
        Store.updateMessage(ch.id, last.id, persist = false) { it.copy(versions = it.versions + "", sel = idx) }
        runStream(last.id, idx, ch.messages.dropLast(1), base = "", extra = null)
    }

    /** Asks the character to keep writing the last reply from where it stopped. */
    fun continueLast() {
        if (busy) return
        val ch = chat ?: return
        val last = ch.messages.lastOrNull() ?: return
        if (last.fromUser) return
        val base = last.text.let { if (it.isEmpty() || it.last().isWhitespace()) it else "$it " }
        val name = character?.name ?: "the character"
        runStream(
            last.id, last.sel, ch.messages, base = base,
            extra = "Continue $name's previous reply exactly where it stopped. Do not repeat anything already written; write only the continuation.",
        )
    }

    fun stop() { job?.cancel() }

    fun setVersion(messageId: String, sel: Int) {
        val id = chatId ?: return
        Store.updateMessage(id, messageId, persist = true) { it.copy(sel = sel.coerceIn(0, it.versions.size - 1)) }
    }

    fun editMessage(messageId: String, text: String) {
        val id = chatId ?: return
        Store.updateMessage(id, messageId, persist = true) { m ->
            m.copy(versions = m.versions.toMutableList().also { it[m.sel] = text })
        }
    }

    fun deleteMessage(messageId: String) {
        val ch = chat ?: return
        Store.putChat(ch.copy(messages = ch.messages.filter { it.id != messageId }))
    }

    /** Deletes every message after this one. */
    fun rewindTo(messageId: String) {
        val ch = chat ?: return
        val i = ch.messages.indexOfFirst { it.id == messageId }
        if (i < 0) return
        Store.putChat(ch.copy(messages = ch.messages.take(i + 1)))
    }

    private fun runStream(messageId: String, idx: Int, history: List<Message>, base: String, extra: String?) {
        val c = character ?: return
        val ch = chat ?: return
        val cid = ch.id
        if (!Store.hasApiKey) {
            error = "Add your API key in Settings first."
            cleanup(cid, messageId, idx, base, "")
            return
        }
        val settings = Store.settings
        val messages = PromptBuilder.build(c, settings, Store.groundRules, Store.rules, history, ch.replyWords, extra)
        val key = Store.apiKey()

        busy = true
        error = null
        streamingId = messageId
        job = viewModelScope.launch {
            val buf = StringBuilder()
            try {
                LlmClient.stream(
                    settings.baseUrl, key, settings.model, messages,
                    PromptBuilder.maxTokens(ch.replyWords), settings.temperature,
                ).collect { delta ->
                    buf.append(delta)
                    val text = base + if (base.isEmpty()) buf.toString().trimStart() else buf.toString()
                    Store.updateMessage(cid, messageId, persist = false) { m ->
                        if (idx < m.versions.size) m.copy(versions = m.versions.toMutableList().also { it[idx] = text })
                        else m
                    }
                }
                if (buf.isBlank()) error = "The model sent back an empty reply. Try again."
            } catch (e: CancellationException) {
                // Stopped by you: keep whatever was written so far.
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong."
            } finally {
                cleanup(cid, messageId, idx, base, buf.toString())
                Store.persistChat(cid)
                busy = false
                streamingId = null
            }
        }
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
}
