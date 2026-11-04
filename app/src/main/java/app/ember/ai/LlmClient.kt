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

/**
 * Talks to any OpenAI-compatible chat API (DeepSeek uses this format).
 * Replies stream in word by word.
 */
object LlmClient {

    private fun open(baseUrl: String, key: String, stream: Boolean): HttpURLConnection {
        val url = URL(baseUrl.trim().trimEnd('/') + "/chat/completions")
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 180_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${key.trim()}")
            if (stream) setRequestProperty("Accept", "text/event-stream")
        }
    }

    private fun body(model: String, messages: List<ChatMsg>, maxTokens: Int, temperature: Double, stream: Boolean) =
        JSONObject()
            .put("model", model.trim())
            .put("stream", stream)
            .put("max_tokens", maxTokens)
            .put("temperature", temperature)
            .put("messages", JSONArray().apply {
                messages.forEach { put(JSONObject().put("role", it.role).put("content", it.content)) }
            })
            .toString()

    private fun errorText(conn: HttpURLConnection): String {
        val code = conn.responseCode
        val raw = runCatching { conn.errorStream?.bufferedReader()?.readText() }.getOrNull().orEmpty()
        val msg = runCatching { JSONObject(raw).getJSONObject("error").optString("message") }.getOrNull()
        val readable = when (code) {
            401 -> "The API key was rejected. Check it in Settings."
            402 -> "Your provider account is out of credit."
            429 -> "Too many requests or rate limited. Wait a moment and try again."
            else -> "The provider returned an error ($code)."
        }
        return if (!msg.isNullOrBlank()) "$readable\n$msg" else readable
    }

    fun stream(
        baseUrl: String, key: String, model: String,
        messages: List<ChatMsg>, maxTokens: Int, temperature: Double,
    ): Flow<String> = flow {
        val conn = open(baseUrl, key, stream = true)
        try {
            conn.outputStream.use { it.write(body(model, messages, maxTokens, temperature, true).toByteArray()) }
            if (conn.responseCode !in 200..299) throw IOException(errorText(conn))
            conn.inputStream.bufferedReader().use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    val delta = runCatching {
                        JSONObject(data).getJSONArray("choices").getJSONObject(0).optJSONObject("delta")
                    }.getOrNull() ?: continue
                    val content = delta.opt("content")
                    if (content is String && content.isNotEmpty()) emit(content)
                }
            }
        } finally {
            conn.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    /** Small non-streaming request used by "Test connection". Returns null on success, or an error. */
    suspend fun test(baseUrl: String, key: String, model: String): String? = withContext(Dispatchers.IO) {
        try {
            val conn = open(baseUrl, key, stream = false)
            try {
                conn.outputStream.use {
                    it.write(body(model, listOf(ChatMsg("user", "Reply with the word OK.")), 5, 0.0, false).toByteArray())
                }
                if (conn.responseCode in 200..299) null else errorText(conn)
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            "Could not reach the provider: ${e.message}"
        }
    }
}
