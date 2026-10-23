package id.nexbiz.pos.data.repository

import id.nexbiz.pos.data.local.entity.AuditLogEntity
import id.nexbiz.pos.data.local.entity.SyncOutboxEntity
import id.nexbiz.pos.data.remote.dto.OutboxConflictDto
import kotlinx.coroutines.flow.Flow

enum class SyncExecutionOutcome {
    IDLE,
    SUCCESS,
    RETRYABLE_ERROR,
    PERMANENT_CONFLICT
}

data class SyncCycleReport(
    val businessId: String,
    val outcome: SyncExecutionOutcome,
    val pushedCount: Int = 0,
    val acknowledgedCount: Int = 0,
    val conflictCount: Int = 0,
    val failedCount: Int = 0,
    val pulledDeltasCount: Int = 0,
    val reconciledStockCount: Int = 0,
    val errorMessage: String? = null,
    val completedAtEpochMs: Long = System.currentTimeMillis()
)

interface SyncRepository {
    fun observeUnsyncedOutboxCount(businessId: String): Flow<Int>

    fun observeOutboxQueue(businessId: String, limit: Int = 100): Flow<List<SyncOutboxEntity>>

    fun observeAuditLogs(businessId: String, limit: Int = 100): Flow<List<AuditLogEntity>>

    suspend fun pushPendingOutbox(businessId: String): SyncCycleReport

    suspend fun pullServerDeltas(businessId: String): SyncCycleReport

    suspend fun executeFullSyncCycle(businessId: String): SyncCycleReport

    suspend fun resolveMultiDeviceStockConflict(
        businessId: String,
        outboxEntry: SyncOutboxEntity,
        conflict: OutboxConflictDto,
        nowEpochMs: Long
    )
}
