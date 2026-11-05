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

    /** True when Ideas and the builder should go to the backup model (DeepSeek) first. */
    val creativeOnBackup get() = Store.settings.creativeUseBackup && Store.backupReady

    /**
     * Streams a reply. With [preferBackup] the backup model writes first and the main one is the fallback;
     * otherwise the main model writes and the backup takes over if it refuses.
     */
    suspend fun stream(prompt: List<ChatMsg>, maxTokens: Int?, temperature: Double, preferBackup: Boolean = false, onText: (String) -> Unit): String {
        check()
        val (firstEp, secondEp) = if (preferBackup && Store.backupReady) backup() to main() else main() to backup()
        suspend fun runOn(ep: Endpoint): String {
            val buf = StringBuilder()
            LlmClient.stream(ep, prompt, maxTokens, temperature).collect { ev ->
                if (ev is StreamEvent.Text) { buf.append(ev.text); onText(buf.toString()) }
            }
            return buf.toString()
        }
        val first = try {
            runOn(firstEp)
        } catch (e: ContentBlockedException) {
            if (!Store.backupReady) throw e
            null
        }
        if (first != null && first.isNotBlank() && !Refusal.looksLike(first)) return first
        if (!Store.backupReady) return first.orEmpty()
        onText("")
        return runOn(secondEp)
    }

    suspend fun complete(prompt: List<ChatMsg>, maxTokens: Int?, temperature: Double, preferBackup: Boolean = false): String {
        check()
        val (firstEp, secondEp) = if (preferBackup && Store.backupReady) backup() to main() else main() to backup()
        val first = try {
            LlmClient.complete(firstEp, prompt, maxTokens, temperature).text
        } catch (e: ContentBlockedException) {
            if (!Store.backupReady) throw e
            null
        }
        if (first != null && first.isNotBlank() && !Refusal.looksLike(first)) return first
        if (!Store.backupReady) return first.orEmpty()
        return LlmClient.complete(secondEp, prompt, maxTokens, temperature).text
    }
}
