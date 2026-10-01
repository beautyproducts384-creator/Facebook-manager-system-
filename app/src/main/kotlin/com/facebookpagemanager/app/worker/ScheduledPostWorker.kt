package com.facebookpagemanager.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.facebookpagemanager.app.FpmApplication
import com.facebookpagemanager.app.data.model.RepoResult
import com.facebookpagemanager.app.di.AppContainer
import java.util.concurrent.TimeUnit

/**
 * Fires when a scheduled post becomes due: publishes it through the active
 * repository (Graph API in real mode, local publish in demo mode) and raises
 * a notification on success or failure.
 */
class ScheduledPostWorker(
    appContext: Context,
    params: WorkerParameters,
    private val container: AppContainer,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val scheduledId = inputData.getString(KEY_ID) ?: return Result.failure()
        val dao = container.db.scheduledPostDao()
        val entity = dao.getById(scheduledId) ?: return Result.failure()
        if (entity.status != "scheduled") return Result.success() // cancelled/edited meanwhile

        val repo = container.currentRepository()
        val domain = entity.let {
            com.facebookpagemanager.app.data.model.ScheduledPost(
                it.id, it.pageId, it.message, it.link, it.mediaUri,
                it.postType, it.scheduledFor, it.status, it.remoteId, it.error
            )
        }
        return when (val r = repo.publishScheduledNow(domain)) {
            is RepoResult.Ok -> {
                container.notificationHelper.showPublished(
                    "Scheduled post published",
                    (domain.message ?: "Your scheduled post").take(100)
                )
                Result.success()
            }
            is RepoResult.Err -> {
                container.notificationHelper.showPublishFailed(
                    "${r.message} ${r.requirement ?: ""}".trim().take(200)
                )
                Result.failure()
            }
        }
    }

    companion object {
        const val KEY_ID = "scheduled_post_id"
    }
}

class FpmWorkerFactory(private val container: AppContainer) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): androidx.work.ListenableWorker? {
        if (workerClassName == ScheduledPostWorker::class.java.name) {
            return ScheduledPostWorker(appContext, workerParameters, container)
        }
        return null
    }
}

/** Schedules/cancels the WorkManager job for a scheduled post. */
object PostScheduler {
    private fun uniqueName(scheduledId: String) = "scheduled_post_$scheduledId"

    fun schedule(context: Context, scheduledId: String, atEpochSec: Long) {
        val delaySec = (atEpochSec - System.currentTimeMillis() / 1000).coerceAtLeast(0)
        val req = OneTimeWorkRequestBuilder<ScheduledPostWorker>()
            .setInitialDelay(delaySec, TimeUnit.SECONDS)
            .setInputData(workDataOf(ScheduledPostWorker.KEY_ID to scheduledId))
            .addTag("scheduled_post")
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(uniqueName(scheduledId), ExistingWorkPolicy.REPLACE, req)
    }

    fun cancel(context: Context, scheduledId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueName(scheduledId))
    }
}

/** Convenience accessor for non-Compose call sites. */
fun Context.appContainer(): AppContainer =
    (applicationContext as FpmApplication).container
