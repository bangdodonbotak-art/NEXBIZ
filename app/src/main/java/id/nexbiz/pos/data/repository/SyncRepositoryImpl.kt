package id.nexbiz.pos.data.repository

import androidx.room.withTransaction
import id.nexbiz.pos.data.local.dao.InventoryDao
import id.nexbiz.pos.data.local.dao.ProductDao
import id.nexbiz.pos.data.local.dao.SaleDao
import id.nexbiz.pos.data.local.dao.SyncOutboxDao
import id.nexbiz.pos.data.local.db.NexbizDatabase
import id.nexbiz.pos.data.local.entity.AccountEntity
import id.nexbiz.pos.data.local.entity.AuditLogEntity
import id.nexbiz.pos.data.local.entity.CategoryEntity
import id.nexbiz.pos.data.local.entity.InventoryMovementEntity
import id.nexbiz.pos.data.local.entity.ProductEntity
import id.nexbiz.pos.data.local.entity.ProductVariantEntity
import id.nexbiz.pos.data.local.entity.SyncMetadataEntity
import id.nexbiz.pos.data.local.entity.SyncOutboxEntity
import id.nexbiz.pos.data.local.model.MovementType
import id.nexbiz.pos.data.local.model.OutboxOperation
import id.nexbiz.pos.data.local.model.SyncStatus
import id.nexbiz.pos.data.local.model.UserRole
import id.nexbiz.pos.data.remote.api.NexbizSyncApi
import id.nexbiz.pos.data.remote.dto.OutboxConflictDto
import id.nexbiz.pos.data.remote.dto.OutboxMutationDto
import id.nexbiz.pos.data.remote.dto.OutboxPushBatchRequest
import id.nexbiz.pos.data.remote.dto.SaleOutboxPayloadDto
import id.nexbiz.pos.data.remote.dto.StockAdjustmentPayloadDto
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val database: NexbizDatabase,
    private val productDao: ProductDao,
    private val saleDao: SaleDao,
    private val inventoryDao: InventoryDao,
    private val syncOutboxDao: SyncOutboxDao,
    private val syncApi: NexbizSyncApi,
    private val deviceId: String = "NEXBIZ-ANDROID-POS-01"
) : SyncRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun observeUnsyncedOutboxCount(businessId: String): Flow<Int> =
        syncOutboxDao.observeUnsyncedCount(businessId)

    override fun observeOutboxQueue(businessId: String, limit: Int): Flow<List<SyncOutboxEntity>> =
        syncOutboxDao.observeOutboxEntries(businessId, limit)

    override fun observeAuditLogs(businessId: String, limit: Int): Flow<List<AuditLogEntity>> =
        syncOutboxDao.observeRecentAuditLogs(businessId, limit)

    override suspend fun executeFullSyncCycle(businessId: String): SyncCycleReport {
        val pushReport = pushPendingOutbox(businessId)
        if (pushReport.outcome == SyncExecutionOutcome.RETRYABLE_ERROR) {
            return pushReport
        }

        val pullReport = pullServerDeltas(businessId)
        val mergedOutcome = when {
            pullReport.outcome == SyncExecutionOutcome.RETRYABLE_ERROR ->
                SyncExecutionOutcome.RETRYABLE_ERROR
            pushReport.conflictCount > 0 -> SyncExecutionOutcome.PERMANENT_CONFLICT
            pushReport.pushedCount == 0 && pullReport.pulledDeltasCount == 0 ->
                SyncExecutionOutcome.IDLE
            else -> SyncExecutionOutcome.SUCCESS
        }

        return SyncCycleReport(
            businessId = businessId,
            outcome = mergedOutcome,
            pushedCount = pushReport.pushedCount,
            acknowledgedCount = pushReport.acknowledgedCount,
            conflictCount = pushReport.conflictCount,
            failedCount = pushReport.failedCount,
            pulledDeltasCount = pullReport.pulledDeltasCount,
            reconciledStockCount = pushReport.reconciledStockCount + pullReport.reconciledStockCount,
            errorMessage = pullReport.errorMessage ?: pushReport.errorMessage,
            completedAtEpochMs = System.currentTimeMillis()
        )
    }

    override suspend fun pushPendingOutbox(businessId: String): SyncCycleReport {
        val now = System.currentTimeMillis()
        val readyBatch = syncOutboxDao.getReadyOutboxBatch(
            businessId = businessId,
            nowEpochMs = now,
            batchSize = DEFAULT_OUTBOX_BATCH_SIZE
        )
        if (readyBatch.isEmpty()) {
            return SyncCycleReport(
                businessId = businessId,
                outcome = SyncExecutionOutcome.IDLE
            )
        }

        val outboxIds = readyBatch.map { it.id }
        val outboxById = readyBatch.associateBy { it.id }

        // Transition batch to SYNCING before network I/O
        syncOutboxDao.markBatchStatus(
            businessId = businessId,
            outboxIds = outboxIds,
            newStatus = SyncStatus.SYNCING,
            updatedAt = now
        )

        val request = OutboxPushBatchRequest(
            businessId = businessId,
            deviceId = deviceId,
            clientTimestampMs = now,
            mutations = readyBatch.map { entry ->
                OutboxMutationDto(
                    outboxId = entry.id,
                    businessId = entry.businessId,
                    idempotencyKey = entry.idempotencyKey,
                    operation = entry.operation,
                    entityType = entry.entityType,
                    entityId = entry.entityId,
                    payloadJson = entry.payloadJson,
                    createdAtMs = entry.createdAt
                )
            }
        )

        val batchIdempotencyKey = "batch-${businessId.take(8)}-${readyBatch.first().id.take(8)}-${readyBatch.size}"

        return try {
            val response = syncApi.pushOutboxBatch(
                businessId = businessId,
                batchIdempotencyKey = batchIdempotencyKey,
                deviceId = deviceId,
                request = request
            )

            if (response.isSuccessful) {
                val body = response.body()
                if (body == null) {
                    markBatchRetryableFailure(
                        businessId = businessId,
                        entries = readyBatch,
                        errorCode = "EMPTY_RESPONSE",
                        errorMessage = "Server returned empty outbox response body",
                        nowEpochMs = now
                    )
                    return SyncCycleReport(
                        businessId = businessId,
                        outcome = SyncExecutionOutcome.RETRYABLE_ERROR,
                        pushedCount = readyBatch.size,
                        failedCount = readyBatch.size,
                        errorMessage = "Empty response body from sync server"
                    )
                }

                database.withTransaction {
                    // 1. Mark acknowledged mutations as SYNCED & propagate to domain tables
                    for (ack in body.acknowledged) {
                        syncOutboxDao.markSynced(
                            businessId = businessId,
                            outboxId = ack.outboxId,
                            updatedAt = body.serverTimestampMs
                        )
                        val sourceOutbox = outboxById[ack.outboxId]
                        if (sourceOutbox != null) {
                            markDomainEntitySynced(
                                businessId = businessId,
                                outbox = sourceOutbox,
                                serverVersion = ack.serverVersion,
                                syncedAtMs = body.serverTimestampMs
                            )
                        }
                    }

                    // 2. Reconcile authoritative stock confirmations returned by server
                    for (stockConfirmation in body.stockConfirmations) {
                        inventoryDao.reconcileServerConfirmedStock(
                            businessId = businessId,
                            variantId = stockConfirmation.variantId,
                            serverConfirmedStock = stockConfirmation.serverConfirmedStock,
                            unitCostRp = stockConfirmation.unitCostRp,
                            reconciledAtEpochMs = stockConfirmation.reconciledAtMs
                        )
                    }

                    // 3. Resolve multi-device stock conflicts
                    for (conflict in body.conflicts) {
                        val conflictingOutbox = outboxById[conflict.outboxId]
                        if (conflictingOutbox != null) {
                            resolveMultiDeviceStockConflict(
                                businessId = businessId,
                                outboxEntry = conflictingOutbox,
                                conflict = conflict,
                                nowEpochMs = body.serverTimestampMs
                            )
                        }
                    }

                    // 4. Update last push metadata timestamp
                    val existingMeta = syncOutboxDao.getSyncMetadata(businessId, ALL_RESOURCES_KEY)
                    if (existingMeta != null) {
                        syncOutboxDao.upsertSyncMetadata(
                            existingMeta.copy(
                                lastPushEpochMs = body.serverTimestampMs,
                                syncStatus = SyncStatus.SYNCED,
                                updatedAt = body.serverTimestampMs
                            )
                        )
                    }
                }

                SyncCycleReport(
                    businessId = businessId,
                    outcome = if (body.conflicts.isNotEmpty()) {
                        SyncExecutionOutcome.PERMANENT_CONFLICT
                    } else {
                        SyncExecutionOutcome.SUCCESS
                    },
                    pushedCount = readyBatch.size,
                    acknowledgedCount = body.acknowledged.size,
                    conflictCount = body.conflicts.size,
                    reconciledStockCount = body.stockConfirmations.size,
                    completedAtEpochMs = body.serverTimestampMs
                )
            } else if (response.code() == 409) {
                // Batch-level HTTP 409 Conflict: resolve stock state for each item in batch
                database.withTransaction {
                    for (entry in readyBatch) {
                        val syntheticConflict = OutboxConflictDto(
                            outboxId = entry.id,
                            idempotencyKey = entry.idempotencyKey,
                            entityType = entry.entityType,
                            entityId = entry.entityId,
                            conflictCode = "HTTP_409_VERSION_CONFLICT",
                            conflictMessage = "Multi-device version conflict detected by server",
                            serverVersion = 2L
                        )
                        resolveMultiDeviceStockConflict(
                            businessId = businessId,
                            outboxEntry = entry,
                            conflict = syntheticConflict,
                            nowEpochMs = now
                        )
                    }
                }
                SyncCycleReport(
                    businessId = businessId,
                    outcome = SyncExecutionOutcome.PERMANENT_CONFLICT,
                    pushedCount = readyBatch.size,
                    conflictCount = readyBatch.size,
                    errorMessage = "HTTP 409 Multi-device conflict resolved via stock rebase"
                )
            } else {
                val code = "HTTP_${response.code()}"
                val msg = response.message().ifBlank { "Outbox push failed with HTTP ${response.code()}" }
                markBatchRetryableFailure(
                    businessId = businessId,
                    entries = readyBatch,
                    errorCode = code,
                    errorMessage = msg,
                    nowEpochMs = now
                )
                SyncCycleReport(
                    businessId = businessId,
                    outcome = SyncExecutionOutcome.RETRYABLE_ERROR,
                    pushedCount = readyBatch.size,
                    failedCount = readyBatch.size,
                    errorMessage = "$code: $msg"
                )
            }
        } catch (ioError: IOException) {
            markBatchRetryableFailure(
                businessId = businessId,
                entries = readyBatch,
                errorCode = "NETWORK_IO_ERROR",
                errorMessage = ioError.message ?: "Offline or unreachable sync endpoint",
                nowEpochMs = now
            )
            SyncCycleReport(
                businessId = businessId,
                outcome = SyncExecutionOutcome.RETRYABLE_ERROR,
                pushedCount = readyBatch.size,
                failedCount = readyBatch.size,
                errorMessage = ioError.message ?: "Network unreachable"
            )
        }
    }

    override suspend fun pullServerDeltas(businessId: String): SyncCycleReport {
        val now = System.currentTimeMillis()
        val metadata = syncOutboxDao.getSyncMetadata(businessId, ALL_RESOURCES_KEY)
        val cursor = metadata?.lastPullCursor
        val sinceEpochMs = metadata?.lastPullEpochMs ?: 0L

        return try {
            val response = syncApi.pullServerDeltas(
                businessId = businessId,
                cursor = cursor,
                sinceEpochMs = sinceEpochMs,
                limit = DEFAULT_DELTA_PULL_LIMIT
            )

            if (!response.isSuccessful) {
                return SyncCycleReport(
                    businessId = businessId,
                    outcome = SyncExecutionOutcome.RETRYABLE_ERROR,
                    errorMessage = "Delta pull HTTP ${response.code()}"
                )
            }

            val body = response.body() ?: return SyncCycleReport(
                businessId = businessId,
                outcome = SyncExecutionOutcome.IDLE
            )

            var appliedDeltas = 0
            var reconciledStocks = 0

            database.withTransaction {
                // 1. Categories (Last-Write-Wins if local is not PENDING)
                for (dto in body.categories) {
                    val local = productDao.getCategoryById(businessId, dto.id)
                    if (local == null || (local.syncStatus == SyncStatus.SYNCED && dto.version >= local.version)) {
                        productDao.upsertCategory(
                            CategoryEntity(
                                id = dto.id,
                                businessId = businessId,
                                name = dto.name,
                                description = dto.description,
                                colorHex = dto.colorHex,
                                sortOrder = dto.sortOrder,
                                isActive = dto.isActive,
                                syncStatus = SyncStatus.SYNCED,
                                version = dto.version,
                                createdAt = local?.createdAt ?: dto.updatedAtMs,
                                updatedAt = dto.updatedAtMs
                            )
                        )
                        appliedDeltas++
                    }
                }

                // 2. Products
                for (dto in body.products) {
                    val local = productDao.getProductById(businessId, dto.id)
                    if (local == null || (local.syncStatus == SyncStatus.SYNCED && dto.version >= local.version)) {
                        productDao.upsertProduct(
                            ProductEntity(
                                id = dto.id,
                                businessId = businessId,
                                categoryId = dto.categoryId,
                                sku = dto.sku,
                                name = dto.name,
                                description = dto.description,
                                unitName = dto.unitName,
                                basePriceRp = dto.basePriceRp,
                                baseCostRp = dto.baseCostRp,
                                isTaxable = dto.isTaxable,
                                trackInventory = dto.trackInventory,
                                isActive = dto.isActive,
                                syncStatus = SyncStatus.SYNCED,
                                version = dto.version,
                                createdAt = local?.createdAt ?: dto.updatedAtMs,
                                updatedAt = dto.updatedAtMs
                            )
                        )
                        appliedDeltas++
                    }
                }

                // 3. Product Variants
                for (dto in body.variants) {
                    val local = productDao.getVariantById(businessId, dto.id)
                    if (local == null || (local.syncStatus == SyncStatus.SYNCED && dto.version >= local.version)) {
                        productDao.upsertVariant(
                            ProductVariantEntity(
                                id = dto.id,
                                businessId = businessId,
                                productId = dto.productId,
                                variantName = dto.variantName,
                                sku = dto.sku,
                                barcode = dto.barcode,
                                sellingPriceRp = dto.sellingPriceRp,
                                averageCostRp = dto.averageCostRp,
                                minStockThreshold = dto.minStockThreshold,
                                isActive = dto.isActive,
                                syncStatus = SyncStatus.SYNCED,
                                version = dto.version,
                                createdAt = local?.createdAt ?: dto.updatedAtMs,
                                updatedAt = dto.updatedAtMs
                            )
                        )
                        appliedDeltas++
                    }
                }

                // 4. Inventory Balances (Rebase localEstimatedStock = serverConfirmedStock + unconfirmedLocalDelta)
                for (dto in body.inventoryBalances) {
                    inventoryDao.reconcileServerConfirmedStock(
                        businessId = businessId,
                        variantId = dto.variantId,
                        serverConfirmedStock = dto.serverConfirmedStock,
                        unitCostRp = dto.unitCostRp,
                        reconciledAtEpochMs = body.serverTimestampMs
                    )
                    reconciledStocks++
                }

                // 5. Chart of Accounts
                for (dto in body.accounts) {
                    val local = saleDao.getAccountById(businessId, dto.id)
                    if (local == null || (local.syncStatus == SyncStatus.SYNCED && dto.version >= local.version)) {
                        saleDao.upsertAccount(
                            AccountEntity(
                                id = dto.id,
                                businessId = businessId,
                                code = dto.code,
                                name = dto.name,
                                accountType = dto.accountType,
                                normalDebit = dto.normalDebit,
                                currentBalanceRp = dto.currentBalanceRp,
                                isSystemAccount = dto.isSystemAccount,
                                isActive = dto.isActive,
                                syncStatus = SyncStatus.SYNCED,
                                version = dto.version,
                                createdAt = local?.createdAt ?: dto.updatedAtMs,
                                updatedAt = dto.updatedAtMs
                            )
                        )
                        appliedDeltas++
                    }
                }

                // 6. Advance SyncMetadata Cursor
                syncOutboxDao.upsertSyncMetadata(
                    SyncMetadataEntity(
                        id = metadata?.id ?: "meta-0001-4000-8000-000000000001",
                        businessId = businessId,
                        resourceName = ALL_RESOURCES_KEY,
                        lastPullCursor = body.nextCursor,
                        lastPullEpochMs = body.serverTimestampMs,
                        lastPushEpochMs = metadata?.lastPushEpochMs ?: now,
                        syncStatus = SyncStatus.SYNCED,
                        updatedAt = body.serverTimestampMs
                    )
                )
            }

            SyncCycleReport(
                businessId = businessId,
                outcome = SyncExecutionOutcome.SUCCESS,
                pulledDeltasCount = appliedDeltas,
                reconciledStockCount = reconciledStocks,
                completedAtEpochMs = body.serverTimestampMs
            )
        } catch (ioError: IOException) {
            SyncCycleReport(
                businessId = businessId,
                outcome = SyncExecutionOutcome.RETRYABLE_ERROR,
                errorMessage = ioError.message ?: "Delta pull network error"
            )
        }
    }

    /**
     * Multi-Device Stock Conflict Resolution Algorithm:
     *
     * Case A — Append-Only Sale Transaction (`CREATE_SALE_TRANSACTION`):
     * Sales are financial facts that already occurred at the physical cashier terminal.
     * Even if Device B concurrently sold the same SKU and changed `serverConfirmedStock`:
     *   1. Mark the sale movements synced or rebase against the server's authoritative stock.
     *   2. Recompute `localEstimatedStock = serverConfirmedStock + sumUnconfirmedLocalDelta`
     *      (even if `localEstimatedStock` temporarily dips below 0, indicating negative physical variance
     *      to be reconciled during physical Stock Opname).
     *   3. Record an AuditLogEntity detailing the multi-device stock rebase.
     *
     * Case B — Inventory Stock Opname / Adjustment (`RECORD_INVENTORY_MOVEMENT`):
     * If Device A submitted an absolute stock count based on stale `baseVersion` while Device B
     * recorded a sale on the server:
     *   1. Rebase `InventoryBalanceEntity` to the authoritative `serverConfirmedStock` + remaining
     *      unconfirmed deltas.
     *   2. Transition the conflicting outbox entry to `SyncStatus.CONFLICT` and mark the local
     *      movement as `SyncStatus.CONFLICT` so the inventory clerk is alerted, while writing a
     *      compensating adjustment movement to preserve ledger accuracy.
     */
    override suspend fun resolveMultiDeviceStockConflict(
        businessId: String,
        outboxEntry: SyncOutboxEntity,
        conflict: OutboxConflictDto,
        nowEpochMs: Long
    ) {
        when (outboxEntry.operation) {
            OutboxOperation.CREATE_SALE_TRANSACTION -> {
                val salePayload = runCatching {
                    json.decodeFromString<SaleOutboxPayloadDto>(outboxEntry.payloadJson)
                }.getOrNull()

                if (salePayload != null) {
                    saleDao.markSaleGraphSynced(
                        businessId = businessId,
                        saleId = salePayload.saleId,
                        serverVersion = conflict.serverVersion,
                        syncedAtMs = nowEpochMs
                    )
                    for (item in salePayload.items) {
                        val serverStock = conflict.serverConfirmedStock
                            ?: inventoryDao.getBalanceForVariant(businessId, item.variantId)?.serverConfirmedStock
                            ?: 0L
                        inventoryDao.reconcileServerConfirmedStock(
                            businessId = businessId,
                            variantId = item.variantId,
                            serverConfirmedStock = serverStock,
                            unitCostRp = item.unitCostRp,
                            reconciledAtEpochMs = nowEpochMs
                        )
                    }
                }
                syncOutboxDao.markSynced(
                    businessId = businessId,
                    outboxId = outboxEntry.id,
                    updatedAt = nowEpochMs
                )
            }

            OutboxOperation.RECORD_INVENTORY_MOVEMENT -> {
                val movementPayload = runCatching {
                    json.decodeFromString<StockAdjustmentPayloadDto>(outboxEntry.payloadJson)
                }.getOrNull()

                val variantId = conflict.variantId
                    ?: movementPayload?.variantId
                    ?: inventoryDao.getMovementById(businessId, outboxEntry.entityId)?.variantId

                if (variantId != null) {
                    inventoryDao.updateMovementSyncStatus(
                        businessId = businessId,
                        movementId = outboxEntry.entityId,
                        syncStatus = SyncStatus.CONFLICT
                    )

                    val existingBalance = inventoryDao.getBalanceForVariant(businessId, variantId)
                    val authoritativeServerStock = conflict.serverConfirmedStock
                        ?: existingBalance?.serverConfirmedStock
                        ?: 0L
                    val authoritativeCostRp = conflict.serverUnitCostRp
                        ?: movementPayload?.unitCostRp
                        ?: existingBalance?.valuationCostRp
                        ?: 0L

                    // Calculate compensating delta if local movement had shifted estimated stock
                    val staleDelta = movementPayload?.quantityDelta ?: 0L
                    if (staleDelta != 0L && existingBalance != null) {
                        val stockBefore = existingBalance.localEstimatedStock
                        val compensatingDelta = -staleDelta
                        val stockAfter = stockBefore + compensatingDelta

                        inventoryDao.insertMovement(
                            InventoryMovementEntity(
                                id = UUID.randomUUID().toString(),
                                businessId = businessId,
                                variantId = variantId,
                                movementType = MovementType.STOCK_OPNAME_ADJUSTMENT,
                                quantityDelta = compensatingDelta,
                                stockBefore = stockBefore,
                                stockAfter = stockAfter,
                                unitCostRp = authoritativeCostRp,
                                referenceId = outboxEntry.id,
                                referenceType = "CONFLICT_REBASE",
                                notes = "Rebase konflik stok multi-perangkat (${conflict.conflictCode})",
                                createdByUserId = "SYSTEM-SYNC-RESOLVER",
                                syncStatus = SyncStatus.SYNCED,
                                createdAt = nowEpochMs
                            )
                        )
                    }

                    inventoryDao.reconcileServerConfirmedStock(
                        businessId = businessId,
                        variantId = variantId,
                        serverConfirmedStock = authoritativeServerStock,
                        unitCostRp = authoritativeCostRp,
                        reconciledAtEpochMs = nowEpochMs
                    )
                }

                syncOutboxDao.markFailedOrConflict(
                    businessId = businessId,
                    outboxId = outboxEntry.id,
                    failureStatus = SyncStatus.CONFLICT,
                    nextAttemptAt = Long.MAX_VALUE,
                    errorCode = conflict.conflictCode,
                    errorMessage = conflict.conflictMessage,
                    updatedAt = nowEpochMs
                )
            }

            else -> {
                syncOutboxDao.markFailedOrConflict(
                    businessId = businessId,
                    outboxId = outboxEntry.id,
                    failureStatus = SyncStatus.CONFLICT,
                    nextAttemptAt = Long.MAX_VALUE,
                    errorCode = conflict.conflictCode,
                    errorMessage = conflict.conflictMessage,
                    updatedAt = nowEpochMs
                )
            }
        }

        // Always append an immutable AuditLogEntity for multi-device conflict resolution
        syncOutboxDao.insertAuditLog(
            AuditLogEntity(
                id = UUID.randomUUID().toString(),
                businessId = businessId,
                actorUserId = "SYSTEM-SYNC-ENGINE",
                actorRole = UserRole.ADMIN,
                action = "MULTI_DEVICE_STOCK_CONFLICT_RESOLVED",
                entityType = outboxEntry.entityType,
                entityId = outboxEntry.entityId,
                beforeStateJson = outboxEntry.payloadJson,
                afterStateJson = """{"conflict_code":"${conflict.conflictCode}","server_version":${conflict.serverVersion},"server_confirmed_stock":${conflict.serverConfirmedStock ?: 0},"strategy":"${conflict.resolutionStrategy}"}""",
                deviceId = deviceId,
                syncStatus = SyncStatus.SYNCED,
                createdAt = nowEpochMs
            )
        )
    }

    private suspend fun markDomainEntitySynced(
        businessId: String,
        outbox: SyncOutboxEntity,
        serverVersion: Long,
        syncedAtMs: Long
    ) {
        when (outbox.operation) {
            OutboxOperation.CREATE_SALE_TRANSACTION -> {
                saleDao.markSaleGraphSynced(
                    businessId = businessId,
                    saleId = outbox.entityId,
                    serverVersion = serverVersion,
                    syncedAtMs = syncedAtMs
                )
            }
            OutboxOperation.RECORD_INVENTORY_MOVEMENT -> {
                inventoryDao.updateMovementSyncStatus(
                    businessId = businessId,
                    movementId = outbox.entityId,
                    syncStatus = SyncStatus.SYNCED
                )
            }
            OutboxOperation.UPSERT_VARIANT -> {
                productDao.updateVariantSyncStatus(
                    businessId = businessId,
                    variantId = outbox.entityId,
                    syncStatus = SyncStatus.SYNCED,
                    updatedAt = syncedAtMs
                )
            }
            else -> Unit
        }
    }

    private suspend fun markBatchRetryableFailure(
        businessId: String,
        entries: List<SyncOutboxEntity>,
        errorCode: String,
        errorMessage: String,
        nowEpochMs: Long
    ) {
        database.withTransaction {
            for (entry in entries) {
                val nextRetry = entry.retryCount + 1
                val backoffDelayMs = computeExponentialBackoffWithJitterMs(nextRetry)
                syncOutboxDao.markFailedOrConflict(
                    businessId = businessId,
                    outboxId = entry.id,
                    failureStatus = SyncStatus.FAILED,
                    nextAttemptAt = nowEpochMs + backoffDelayMs,
                    errorCode = errorCode,
                    errorMessage = errorMessage,
                    updatedAt = nowEpochMs
                )
            }
        }
    }

    companion object {
        private const val ALL_RESOURCES_KEY = "ALL_POS_RESOURCES"
        private const val DEFAULT_OUTBOX_BATCH_SIZE = 50
        private const val DEFAULT_DELTA_PULL_LIMIT = 200
        private const val BASE_BACKOFF_DELAY_MS = 15_000L // 15 seconds
        private const val MAX_BACKOFF_DELAY_MS = 1_800_000L // 30 minutes

        fun computeExponentialBackoffWithJitterMs(retryAttempt: Int): Long {
            val cappedAttempt = retryAttempt.coerceIn(1, 10)
            val exponential = BASE_BACKOFF_DELAY_MS * (1L shl (cappedAttempt - 1))
            val bounded = min(MAX_BACKOFF_DELAY_MS, exponential)
            val jitter = Random.nextLong(from = 500L, until = 3_500L)
            return bounded + jitter
        }
    }
}
