package app.ember.work

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.compose.runtime.mutableStateListOf
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.ember.MainActivity
import app.ember.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * Runs replies outside any screen, so leaving the chat or the app never cuts them off. While something is
 * being written, a small foreground service keeps Ember alive; when a reply lands while you're away, you get
 * a notification that opens the chat.
 */
object Generation {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var app: Context

    /** Characters with a reply being written right now (watched by the chat screen). */
    val running = mutableStateListOf<String>()

    /** True while one of Ember's screens is on screen. */
    @Volatile var appVisible = false

    fun init(context: Context) {
        app = context.applicationContext
        val channels = listOf(
            NotificationChannel(CH_WORKING, "Writing replies", NotificationManager.IMPORTANCE_LOW),
            NotificationChannel(CH_DONE, "New replies", NotificationManager.IMPORTANCE_DEFAULT),
        )
        app.getSystemService(NotificationManager::class.java).createNotificationChannels(channels)
    }

    fun isRunning(characterId: String) = characterId in running

    /**
     * Starts [block] in the app-wide scope. [label] shows in the "writing" notification. When it ends, a
     * "replied" notification is posted if you're not looking at the app and [result] gives a preview.
     */
    fun launch(
        characterId: String,
        context: CoroutineContext,
        label: String,
        result: () -> String? = { null },
        block: suspend CoroutineScope.() -> Unit,
    ): Job {
        running.add(characterId)
        updateService(label)
        return scope.launch(context) {
            try {
                block()
            } finally {
                running.remove(characterId)
                if (!appVisible) result()?.let { notifyDone(characterId, label, it) }
                updateService(label)
            }
        }
    }

    /** Background work that shouldn't count as "writing" (summaries, the scene tracker). */
    fun launchQuiet(context: CoroutineContext, block: suspend CoroutineScope.() -> Unit): Job = scope.launch(context, block = block)

    private fun updateService(label: String) {
        if (!::app.isInitialized) return
        val intent = Intent(app, GenerationService::class.java)
        if (running.isEmpty()) app.stopService(intent)
        else runCatching { ContextCompat.startForegroundService(app, intent.putExtra("label", label)) }
    }

    private fun notifyDone(characterId: String, label: String, preview: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            app, characterId.hashCode(),
            Intent(app, MainActivity::class.java).putExtra(MainActivity.EXTRA_CHAT, characterId)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(app, CH_DONE)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(label.removePrefix("Writing ").removeSuffix("…").let { "$it is ready" })
            .setContentText(preview.take(140))
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview.take(400)))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(app).notify(characterId.hashCode(), n) }
    }

    fun workingNotification(context: Context, label: String): Notification =
        NotificationCompat.Builder(context, CH_WORKING)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(label)
            .setContentText("You can leave Ember; it keeps writing.")
            .setOngoing(true)
            .setSilent(true)
            .build()

    private const val CH_WORKING = "writing"
    private const val CH_DONE = "replies"
}

/** Keeps Ember alive while a reply is being written. */
class GenerationService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = Generation.workingNotification(this, intent?.getStringExtra("label") ?: "Writing…")
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(1, n)
        if (Generation.running.isEmpty()) stopSelf()
        return START_NOT_STICKY
    }
}
