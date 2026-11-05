package app.ember.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class ChatMsg(val role: String, val content: String)

/** Where a request goes: provider address, key and model name. */
data class Endpoint(val baseUrl: String, val key: String, val model: String)

sealed interface StreamEvent {
    data class Text(val text: String) : StreamEvent
    data class Usage(val input: Long, val output: Long) : StreamEvent
    /** The model is reasoning silently before it writes. */
    data object Thinking : StreamEvent
}

/** The provider blocked the request on content grounds (e.g. DeepSeek's "Content Exists Risk"). */
class ContentBlockedException(message: String) : IOException(message)

data class Completion(val text: String, val input: Long, val output: Long)

/** Talks to any OpenAI-compatible chat API (DeepSeek, OpenRouter, xAI and others use this format). */
object LlmClient {

    private fun open(ep: Endpoint, stream: Boolean): HttpURLConnection {
        val url = URL(ep.baseUrl.trim().trimEnd('/') + "/chat/completions")
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 180_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${ep.key.trim()}")
            if (stream) setRequestProperty("Accept", "text/event-stream")
        }
    }

    private fun body(ep: Endpoint, messages: List<ChatMsg>, maxTokens: Int?, temperature: Double, stream: Boolean, lightThinking: Boolean = false): String {
        val model = ep.model
        val o = JSONObject()
            .put("model", model.trim())
            .put("stream", stream)
            .put("temperature", temperature)
            .put("messages", JSONArray().apply {
                messages.forEach { put(JSONObject().put("role", it.role).put("content", it.content)) }
            })
        // Reply length is steered by the prompt and the app's own word stop, never by a token cap,
        // because thinking models spend the cap on thinking first and can run out before writing.
        if (maxTokens != null) o.put("max_tokens", maxTokens)
        else if (ep.baseUrl.contains("openrouter", ignoreCase = true)) {
            // Some OpenRouter providers apply a small default cap, so give plenty of room explicitly,
            // and keep the thinking part bounded so there's always room left for the reply itself.
            o.put("max_tokens", 16_000)
            o.put("reasoning", if (lightThinking) JSONObject().put("effort", "low") else JSONObject().put("max_tokens", 6_000))
        }
        if (stream) o.put("stream_options", JSONObject().put("include_usage", true))
        return o.toString()
    }

    private fun fail(conn: HttpURLConnection): Nothing {
        val code = conn.responseCode
        val raw = runCatching { conn.errorStream?.bufferedReader()?.readText() }.getOrNull().orEmpty()
        val msg = runCatching { JSONObject(raw).getJSONObject("error").optString("message") }.getOrNull().orEmpty()
        val lower = msg.lowercase()
        if (code == 400 && (lower.contains("risk") || lower.contains("content") && lower.contains("policy") || lower.contains("safety"))) {
            throw ContentBlockedException("The provider blocked this request.\n$msg")
        }
        val readable = when (code) {
            401 -> "The API key was rejected. Check it in Settings."
            402 -> "Your provider account is out of credit."
            404 -> "The provider address or model name looks wrong. Check them in Settings."
            429 -> "Too many requests or rate limited. Wait a moment and try again."
            else -> "The provider returned an error ($code)."
        }
        throw IOException(if (msg.isNotBlank()) "$readable\n$msg" else readable)
    }

    private fun usageOf(o: JSONObject): StreamEvent.Usage? {
        val u = o.optJSONObject("usage") ?: return null
        return StreamEvent.Usage(u.optLong("prompt_tokens", 0), u.optLong("completion_tokens", 0))
    }

    fun stream(ep: Endpoint, messages: List<ChatMsg>, maxTokens: Int?, temperature: Double): Flow<StreamEvent> = flow {
      // If the model thinks so long it never writes, try once more with light thinking before giving up.
      for (attempt in 0..1) {
        val conn = open(ep, stream = true)
        var retry = false
        try {
            conn.outputStream.use { it.write(body(ep, messages, maxTokens, temperature, true, lightThinking = attempt > 0).toByteArray()) }
            if (conn.responseCode !in 200..299) fail(conn)
            var wrote = false
            var finish: String? = null
            conn.inputStream.bufferedReader().use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    val o = runCatching { JSONObject(data) }.getOrNull() ?: continue
                    // Errors can arrive in the middle of a stream (OpenRouter does this).
                    o.optJSONObject("error")?.let { err ->
                        val msg = err.optString("message").ifBlank { "The provider stopped with an error." }
                        val low = msg.lowercase()
                        if (low.contains("moderation") || low.contains("flagged") || low.contains("content") && low.contains("polic")) {
                            throw ContentBlockedException("The model's provider blocked this reply.\n$msg")
                        }
                        throw IOException(msg)
                    }
                    usageOf(o)?.let { emit(it) }
                    val choices = o.optJSONArray("choices") ?: continue
                    if (choices.length() == 0) continue
                    val choice = choices.optJSONObject(0) ?: continue
                    choice.optString("finish_reason").takeIf { it.isNotBlank() && it != "null" }?.let { finish = it }
                    val delta = choice.optJSONObject("delta") ?: continue
                    val content = delta.opt("content")
                    if (content is String && content.isNotEmpty()) { wrote = true; emit(StreamEvent.Text(content)) }
                    val reasoning = delta.opt("reasoning") ?: delta.opt("reasoning_content")
                    if (!wrote && reasoning is String && reasoning.isNotEmpty()) emit(StreamEvent.Thinking)
                }
            }
            if (!wrote) {
                when (finish) {
                    "content_filter" -> throw ContentBlockedException("The model's provider filtered this reply.")
                    "length" -> if (attempt == 0) retry = true else throw IOException(
                        "The model thought for too long and never wrote the reply, even on a second try. " +
                            "Tap regenerate, or switch to a model that doesn't think first."
                    )
                }
            }
        } finally {
            conn.disconnect()
        }
        if (!retry) break
      }
    }.flowOn(Dispatchers.IO)

    /** One-shot request (used for summaries). */
    suspend fun complete(ep: Endpoint, messages: List<ChatMsg>, maxTokens: Int?, temperature: Double): Completion =
        withContext(Dispatchers.IO) {
            val conn = open(ep, stream = false)
            try {
                conn.outputStream.use { it.write(body(ep, messages, maxTokens, temperature, false).toByteArray()) }
                if (conn.responseCode !in 200..299) fail(conn)
                val o = JSONObject(conn.inputStream.bufferedReader().readText())
                val text = o.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content", "")
                val u = usageOf(o)
                Completion(text.trim(), u?.input ?: 0, u?.output ?: 0)
            } finally {
                conn.disconnect()
            }
        }

    /** Small request used by "Test connection". Returns null on success, or what went wrong. */
    suspend fun test(ep: Endpoint): String? = try {
        complete(ep, listOf(ChatMsg("user", "Reply with the word OK.")), 5, 0.0)
        null
    } catch (e: Exception) {
        e.message ?: "Could not reach the provider."
    }
}

/** Spots replies where the model stepped out of the story to refuse. */
object Refusal {
    private val phrases = listOf(
        "i can't continue", "i cannot continue", "i can't help with", "i cannot help with",
        "i can't assist", "i cannot assist", "i'm not able to", "i am not able to", "i'm unable to",
        "i am unable to", "i can't write", "i cannot write", "i can't create", "i cannot create",
        "i can't provide", "i cannot provide", "i won't be able to", "i will not write", "i won't write",
        "as an ai", "i apologize, but", "i'm sorry, but i can't", "i'm sorry, but i cannot",
        "i must decline", "against my guidelines", "content policy", "i can't engage", "i cannot engage",
    )

    fun looksLike(text: String): Boolean {
        val head = text.take(400).lowercase().replace('’', '\'')
        // Story replies almost always contain dialogue; refusals almost never do.
        val hasDialogue = head.contains('"') || head.contains('“')
        return !hasDialogue && phrases.any { head.contains(it) }
    }
}
