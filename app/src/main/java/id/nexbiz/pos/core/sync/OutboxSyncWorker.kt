package id.nexbiz.pos.core.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import id.nexbiz.pos.data.repository.SyncExecutionOutcome
import id.nexbiz.pos.di.DatabaseModule
import id.nexbiz.pos.di.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OutboxSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val businessId = inputData.getString(KEY_BUSINESS_ID)
            ?.takeIf { it.isNotBlank() }
            ?: DatabaseModule.DEFAULT_BUSINESS_ID

        if (runAttemptCount >= MAX_WORKER_ATTEMPTS) {
            return@withContext Result.failure(
                workDataOf(
                    KEY_BUSINESS_ID to businessId,
                    KEY_SYNC_ERROR to "Max WorkManager retry attempts ($MAX_WORKER_ATTEMPTS) exceeded"
                )
            )
        }

        val database = DatabaseModule.provideDatabase(applicationContext)
        DatabaseModule.ensureSeeded(database)
        val syncRepository = NetworkModule.provideSyncRepository(database)

        val report = syncRepository.executeFullSyncCycle(businessId)

        val outputData = workDataOf(
            KEY_BUSINESS_ID to report.businessId,
            KEY_OUTCOME to report.outcome.name,
            KEY_PUSHED_COUNT to report.pushedCount,
            KEY_ACK_COUNT to report.acknowledgedCount,
            KEY_CONFLICT_COUNT to report.conflictCount,
            KEY_PULLED_COUNT to report.pulledDeltasCount,
            KEY_RECONCILED_STOCK_COUNT to report.reconciledStockCount,
            KEY_COMPLETED_AT_MS to report.completedAtEpochMs,
            KEY_SYNC_ERROR to (report.errorMessage ?: "")
        )

        when (report.outcome) {
            SyncExecutionOutcome.IDLE,
            SyncExecutionOutcome.SUCCESS,
            SyncExecutionOutcome.PERMANENT_CONFLICT -> Result.success(outputData)

            SyncExecutionOutcome.RETRYABLE_ERROR -> Result.retry()
        }
    }

    companion object {
        const val KEY_BUSINESS_ID = "business_id"
        const val KEY_OUTCOME = "sync_outcome"
        const val KEY_PUSHED_COUNT = "pushed_count"
        const val KEY_ACK_COUNT = "acknowledged_count"
        const val KEY_CONFLICT_COUNT = "conflict_count"
        const val KEY_PULLED_COUNT = "pulled_deltas_count"
        const val KEY_RECONCILED_STOCK_COUNT = "reconciled_stock_count"
        const val KEY_COMPLETED_AT_MS = "completed_at_ms"
        const val KEY_SYNC_ERROR = "sync_error"

        const val UNIQUE_IMMEDIATE_WORK_PREFIX = "nexbiz_immediate_outbox_sync_"
        const val UNIQUE_PERIODIC_WORK_PREFIX = "nexbiz_periodic_delta_sync_"
        const val SYNC_WORK_TAG = "nexbiz_pos_sync"
        private const val MAX_WORKER_ATTEMPTS = 6
    }
}
