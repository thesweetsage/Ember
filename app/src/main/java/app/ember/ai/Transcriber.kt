package app.ember.ai

import app.ember.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Sends a recording to an OpenAI-compatible /audio/transcriptions endpoint (Groq, OpenAI…). */
object Transcriber {
    suspend fun transcribe(audio: File): String = withContext(Dispatchers.IO) {
        val s = Store.settings
        val boundary = "----ember" + System.currentTimeMillis()
        val url = URL(s.voiceBaseUrl.trim().trimEnd('/') + "/audio/transcriptions")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 120_000
            setRequestProperty("Authorization", "Bearer ${Store.voiceKey()}")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        try {
            conn.outputStream.use { out ->
                fun field(name: String, value: String) {
                    out.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray())
                }
                field("model", s.voiceModel.trim())
                field("response_format", "json")
                if (s.voiceLanguage.isNotBlank()) field("language", s.voiceLanguage.trim())
                out.write(
                    ("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"voice.m4a\"\r\n" +
                        "Content-Type: audio/mp4\r\n\r\n").toByteArray()
                )
                audio.inputStream().use { it.copyTo(out) }
                out.write("\r\n--$boundary--\r\n".toByteArray())
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                val raw = runCatching { conn.errorStream?.bufferedReader()?.readText() }.getOrNull().orEmpty()
                val msg = runCatching { JSONObject(raw).getJSONObject("error").optString("message") }.getOrNull().orEmpty()
                throw IOException(
                    when (code) {
                        401 -> "The voice API key was rejected."
                        else -> "Transcription failed ($code)."
                    } + if (msg.isNotBlank()) " $msg" else ""
                )
            }
            JSONObject(conn.inputStream.bufferedReader().readText()).optString("text", "").trim()
        } finally {
            conn.disconnect()
        }
    }
}
