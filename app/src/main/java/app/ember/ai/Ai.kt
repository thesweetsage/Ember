package app.ember.ai

import app.ember.data.Store
import java.io.IOException

/** Shared helpers for features outside a chat (Ideas, the character builder). */
object Ai {
    fun main() = Endpoint(Store.settings.baseUrl, Store.apiKey(), Store.settings.model)
    private fun backup() = Endpoint(Store.settings.backupBaseUrl, Store.backupKey(), Store.settings.backupModel)

    private fun check() {
        if (!Store.hasApiKey) throw IOException("Add your API key in Settings first.")
    }

    /** Streams a reply, retrying with the backup model if the main one refuses. */
    suspend fun stream(prompt: List<ChatMsg>, maxTokens: Int, temperature: Double, onText: (String) -> Unit): String {
        check()
        suspend fun runOn(ep: Endpoint): String {
            val buf = StringBuilder()
            LlmClient.stream(ep, prompt, maxTokens, temperature).collect { ev ->
                if (ev is StreamEvent.Text) { buf.append(ev.text); onText(buf.toString()) }
            }
            return buf.toString()
        }
        val first = try {
            runOn(main())
        } catch (e: ContentBlockedException) {
            if (!Store.backupReady) throw e
            null
        }
        if (first != null && first.isNotBlank() && !Refusal.looksLike(first)) return first
        if (!Store.backupReady) return first.orEmpty()
        onText("")
        return runOn(backup())
    }

    suspend fun complete(prompt: List<ChatMsg>, maxTokens: Int, temperature: Double): String {
        check()
        val first = try {
            LlmClient.complete(main(), prompt, maxTokens, temperature).text
        } catch (e: ContentBlockedException) {
            if (!Store.backupReady) throw e
            null
        }
        if (first != null && first.isNotBlank() && !Refusal.looksLike(first)) return first
        if (!Store.backupReady) return first.orEmpty()
        return LlmClient.complete(backup(), prompt, maxTokens, temperature).text
    }
}
