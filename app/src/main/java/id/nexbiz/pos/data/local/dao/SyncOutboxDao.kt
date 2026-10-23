package id.nexbiz.pos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import id.nexbiz.pos.data.local.entity.AuditLogEntity
import id.nexbiz.pos.data.local.entity.SyncMetadataEntity
import id.nexbiz.pos.data.local.entity.SyncOutboxEntity
import id.nexbiz.pos.data.local.model.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncOutboxDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutbox(entry: SyncOutboxEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutboxBatch(entries: List<SyncOutboxEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncMetadata(metadata: SyncMetadataEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(auditLog: AuditLogEntity)

    @Query(
        """
        SELECT * FROM sync_outbox
        WHERE business_id = :businessId
          AND sync_status IN ('PENDING', 'FAILED')
          AND retry_count < max_retries
          AND next_attempt_at <= :nowEpochMs
        ORDER BY created_at ASC
        LIMIT :batchSize
        """
    )
    suspend fun getReadyOutboxBatch(
        businessId: String,
        nowEpochMs: Long,
        batchSize: Int = 50
    ): List<SyncOutboxEntity>

    @Query(
        """
        SELECT * FROM sync_outbox
        WHERE business_id = :businessId
        ORDER BY created_at DESC
        LIMIT :limit
        """
    )
    fun observeOutboxEntries(businessId: String, limit: Int = 100): Flow<List<SyncOutboxEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM sync_outbox
        WHERE business_id = :businessId
          AND sync_status IN ('PENDING', 'SYNCING', 'FAILED', 'CONFLICT')
        """
    )
    fun observeUnsyncedCount(businessId: String): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM sync_outbox
        WHERE business_id = :businessId
          AND sync_status = :status
        """
    )
    fun observeCountByStatus(businessId: String, status: SyncStatus): Flow<Int>

    @Query(
        """
        UPDATE sync_outbox
        SET sync_status = :newStatus,
            updated_at = :updatedAt
        WHERE business_id = :businessId
          AND id IN (:outboxIds)
        """
    )
    suspend fun markBatchStatus(
        businessId: String,
        outboxIds: List<String>,
        newStatus: SyncStatus,
        updatedAt: Long
    )

    @Query(
        """
        UPDATE sync_outbox
        SET sync_status = 'SYNCED',
            last_error_code = NULL,
            last_error_message = NULL,
            updated_at = :updatedAt
        WHERE business_id = :businessId
          AND id = :outboxId
        """
    )
    suspend fun markSynced(
        businessId: String,
        outboxId: String,
        updatedAt: Long
    )

    @Query(
        """
        UPDATE sync_outbox
        SET sync_status = :failureStatus,
            retry_count = retry_count + 1,
            next_attempt_at = :nextAttemptAt,
            last_error_code = :errorCode,
            last_error_message = :errorMessage,
            updated_at = :updatedAt
        WHERE business_id = :businessId
          AND id = :outboxId
        """
    )
    suspend fun markFailedOrConflict(
        businessId: String,
        outboxId: String,
        failureStatus: SyncStatus,
        nextAttemptAt: Long,
        errorCode: String?,
        errorMessage: String?,
        updatedAt: Long
    )

    @Query(
        """
        DELETE FROM sync_outbox
        WHERE business_id = :businessId
          AND sync_status = 'SYNCED'
          AND updated_at < :olderThanEpochMs
        """
    )
    suspend fun pruneSyncedOutbox(businessId: String, olderThanEpochMs: Long): Int

    @Query(
        """
        SELECT * FROM sync_metadata
        WHERE business_id = :businessId AND resource_name = :resourceName
        LIMIT 1
        """
    )
    suspend fun getSyncMetadata(businessId: String, resourceName: String): SyncMetadataEntity?

    @Query(
        """
        SELECT * FROM audit_logs
        WHERE business_id = :businessId
        ORDER BY created_at DESC
        LIMIT :limit
        """
    )
    fun observeRecentAuditLogs(businessId: String, limit: Int = 100): Flow<List<AuditLogEntity>>
}
