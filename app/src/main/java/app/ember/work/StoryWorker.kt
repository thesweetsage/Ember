package app.ember.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.ember.ai.StoryWriter
import app.ember.ai.UsageTag
import app.ember.data.Store
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Checks every hour whether a daily story is due (its time has come and today's chapter isn't written yet),
 * and writes it, even when Ember is closed. Costs nothing when nothing is due.
 */
class StoryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Store.init(applicationContext)
        Generation.init(applicationContext)
        val now = Calendar.getInstance()
        val due = Store.stories.filter { st ->
            st.active && Store.character(st.characterId) != null && now.get(Calendar.HOUR_OF_DAY) >= st.hour &&
                st.chapters.lastOrNull()?.let { !sameDay(it.createdAt, now) } != false
        }
        for (st in due) {
            val name = Store.character(st.characterId)?.name ?: "Your story"
            runCatching { setForeground(foreground("Writing today's chapter with $name…")) }
            val problem = withContext(UsageTag(st.characterId)) { runCatching { StoryWriter.writeNext(st.id) }.getOrElse { it.message } }
            if (problem == null) Store.story(st.id)?.chapters?.lastOrNull()?.let { ch ->
                Generation.notifyStory(st.id, "New chapter with $name: ${ch.title}", ch.text.take(300))
            }
        }
        return Result.success()
    }

    private fun foreground(label: String): ForegroundInfo {
        val n = Generation.workingNotification(applicationContext, label)
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(2, n)
    }

    private fun sameDay(t: Long, now: Calendar): Boolean {
        val c = Calendar.getInstance().apply { timeInMillis = t }
        return c.get(Calendar.YEAR) == now.get(Calendar.YEAR) && c.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
    }

    companion object {
        /** Makes sure the hourly check is scheduled (safe to call on every app start). */
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<StoryWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("daily-stories", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
