package app.ember.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.widget.Toast
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.ember.ai.Transcriber
import app.ember.data.Store
import kotlinx.coroutines.launch
import java.io.File

/** Adds dictated text to the end of what's already in a field, with a space between. */
fun appendDictation(current: String, spoken: String): String {
    val s = spoken.trim()
    if (s.isEmpty()) return current
    val text = s.replaceFirstChar { it.uppercase() }
    return when {
        current.isBlank() -> text
        current.endsWith(" ") || current.endsWith("\n") -> current + text
        else -> "$current $text"
    }
}

/**
 * Microphone button. Tap to start, tap again to stop.
 * With a voice API key in Settings (e.g. Groq), it records and sends the audio to that model for accurate
 * transcription. Without one, it uses the phone's own speech recognition.
 */
@Composable
fun MicButton(onText: (String) -> Unit, tint: Color = EmberColors.Muted) {
    if (Store.voiceReady) RecordingMicButton(onText, tint) else SystemMicButton(onText, tint)
}

@Composable
private fun RecordingMicButton(onText: (String) -> Unit, tint: Color) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val onTextNow by rememberUpdatedState(onText)
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var file by remember { mutableStateOf<File?>(null) }
    var working by remember { mutableStateOf(false) }

    fun start() {
        val f = File(context.cacheDir, "voice-${System.currentTimeMillis()}.m4a")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(16000)
            r.setAudioEncodingBitRate(64000)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            recorder = r; file = f
        } catch (e: Exception) {
            r.release()
            Toast.makeText(context, "Couldn't start recording.", Toast.LENGTH_SHORT).show()
        }
    }

    fun finish() {
        val r = recorder ?: return
        val f = file
        recorder = null
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        if (!ok || f == null) { f?.delete(); return }
        working = true
        scope.launch {
            try {
                val text = Transcriber.transcribe(f)
                if (text.isNotBlank()) onTextNow(text)
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "Transcription failed.", Toast.LENGTH_LONG).show()
            } finally {
                f.delete()
                working = false
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { recorder?.let { runCatching { it.stop() }; it.release() }; recorder = null }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) start()
    }

    IconButton(onClick = {
        when {
            working -> {}
            recorder != null -> finish()
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> start()
            else -> permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }) {
        when {
            working -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = EmberColors.RedSoft)
            recorder != null -> Box(Modifier.size(32.dp).background(EmberColors.Red, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Stop, "Stop and transcribe", tint = Color.White, modifier = Modifier.size(18.dp))
            }
            else -> Icon(Icons.Filled.Mic, "Dictate", tint = tint)
        }
    }
}

/** Fallback: Android's built-in speech recognition, listening until you tap stop. */
@Composable
private fun SystemMicButton(onText: (String) -> Unit, tint: Color) {
    val context = LocalContext.current
    val onTextNow by rememberUpdatedState(onText)
    var listening by remember { mutableStateOf(false) }
    var unavailable by remember { mutableStateOf(false) }
    val recognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null
    }

    val intent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
        }
    }

    fun begin() {
        val r = recognizer ?: run { unavailable = true; return }
        listening = true
        r.startListening(intent)
    }

    DisposableEffect(recognizer) {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrBlank()) onTextNow(text)
                if (listening) recognizer.startListening(intent)
            }
            override fun onError(error: Int) {
                val keepGoing = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                if (listening && keepGoing) recognizer.startListening(intent) else listening = false
            }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        onDispose {
            listening = false
            recognizer?.cancel()
            recognizer?.destroy()
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) begin()
    }

    IconButton(onClick = {
        when {
            listening -> { listening = false; recognizer?.stopListening() }
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> begin()
            else -> permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }) {
        if (listening) {
            Box(Modifier.size(32.dp).background(EmberColors.Red, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Stop, "Stop dictating", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        } else {
            Icon(
                if (unavailable) Icons.Outlined.Mic else Icons.Filled.Mic,
                if (unavailable) "Voice input isn't available on this phone" else "Dictate",
                tint = tint,
            )
        }
    }
}
