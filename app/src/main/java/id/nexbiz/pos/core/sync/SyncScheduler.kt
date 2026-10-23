package id.nexbiz.pos.core.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow

object SyncScheduler {

    private const val INITIAL_BACKOFF_SECONDS = 15L
    private const val PERIODIC_SYNC_INTERVAL_MINUTES = 15L
    private const val PERIODIC_FLEX_INTERVAL_MINUTES = 5L

    private fun buildConnectedConstraints(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

    /**
     * Enqueues an immediate OneTimeWorkRequest when a local POS sale, stock adjustment,
     * or accounting entry is committed to the Room outbox.
     */
    fun triggerImmediateOutboxSync(context: Context, businessId: String) {
        val request = OneTimeWorkRequestBuilder<OutboxSyncWorker>()
            .setConstraints(buildConnectedConstraints())
            .setInputData(workDataOf(OutboxSyncWorker.KEY_BUSINESS_ID to businessId))
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                INITIAL_BACKOFF_SECONDS,
                TimeUnit.SECONDS
            )
            .addTag(OutboxSyncWorker.SYNC_WORK_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "${OutboxSyncWorker.UNIQUE_IMMEDIATE_WORK_PREFIX}$businessId",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }

    /**
     * Schedules a periodic background delta pull & outbox flush every 15 minutes
     * scoped to the active tenant `businessId`.
     */
    fun schedulePeriodicTenantSync(context: Context, businessId: String) {
        val periodicRequest = PeriodicWorkRequestBuilder<OutboxSyncWorker>(
            PERIODIC_SYNC_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
            PERIODIC_FLEX_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        )
            .setConstraints(buildConnectedConstraints())
            .setInputData(workDataOf(OutboxSyncWorker.KEY_BUSINESS_ID to businessId))
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                INITIAL_BACKOFF_SECONDS,
                TimeUnit.SECONDS
            )
            .addTag(OutboxSyncWorker.SYNC_WORK_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "${OutboxSyncWorker.UNIQUE_PERIODIC_WORK_PREFIX}$businessId",
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        )
    }

    fun observeSyncWorkInfos(context: Context): Flow<List<WorkInfo>> =
        WorkManager.getInstance(context)
            .getWorkInfosByTagFlow(OutboxSyncWorker.SYNC_WORK_TAG)
}
