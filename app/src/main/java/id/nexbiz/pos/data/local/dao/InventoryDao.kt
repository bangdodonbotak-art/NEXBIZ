package id.nexbiz.pos.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import id.nexbiz.pos.data.local.entity.InventoryBalanceEntity
import id.nexbiz.pos.data.local.entity.InventoryMovementEntity
import id.nexbiz.pos.data.local.entity.PurchaseEntity
import id.nexbiz.pos.data.local.entity.PurchaseItemEntity
import id.nexbiz.pos.data.local.entity.SupplierEntity
import id.nexbiz.pos.data.local.entity.SyncOutboxEntity
import id.nexbiz.pos.data.local.model.MovementType
import id.nexbiz.pos.data.local.model.SyncStatus
import java.util.UUID
import kotlinx.coroutines.flow.Flow

data class LowStockVariantAlertTuple(
    @ColumnInfo(name = "variant_id") val variantId: String,
    @ColumnInfo(name = "product_name") val productName: String,
    @ColumnInfo(name = "variant_name") val variantName: String,
    @ColumnInfo(name = "sku") val sku: String,
    @ColumnInfo(name = "local_estimated_stock") val localEstimatedStock: Long,
    @ColumnInfo(name = "server_confirmed_stock") val serverConfirmedStock: Long,
    @ColumnInfo(name = "min_stock_threshold") val minStockThreshold: Long
)

@Dao
interface InventoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBalance(balance: InventoryBalanceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBalances(balances: List<InventoryBalanceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovement(movement: InventoryMovementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovements(movements: List<InventoryMovementEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSuppliers(suppliers: List<SupplierEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchase(purchase: PurchaseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchaseItems(items: List<PurchaseItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutboxForInventory(outbox: SyncOutboxEntity)

    @Query(
        """
        SELECT * FROM inventory_balances 
        WHERE business_id = :businessId AND variant_id = :variantId 
        LIMIT 1
        """
    )
    suspend fun getBalanceForVariant(businessId: String, variantId: String): InventoryBalanceEntity?

    @Query(
        """
        SELECT * FROM inventory_balances 
        WHERE business_id = :businessId 
        ORDER BY updated_at DESC
        """
    )
    fun observeBalances(businessId: String): Flow<List<InventoryBalanceEntity>>

    @Query(
        """
        SELECT * FROM inventory_movements 
        WHERE business_id = :businessId AND variant_id = :variantId 
        ORDER BY created_at DESC 
        LIMIT :limit
        """
    )
    fun observeMovementsForVariant(
        businessId: String,
        variantId: String,
        limit: Int = 100
    ): Flow<List<InventoryMovementEntity>>

    @Query(
        """
        SELECT * FROM inventory_movements 
        WHERE business_id = :businessId 
        ORDER BY created_at DESC 
        LIMIT :limit
        """
    )
    fun observeRecentMovements(businessId: String, limit: Int = 100): Flow<List<InventoryMovementEntity>>

    @Query(
        """
        SELECT 
            pv.id AS variant_id,
            p.name AS product_name,
            pv.variant_name AS variant_name,
            pv.sku AS sku,
            COALESCE(ib.local_estimated_stock, 0) AS local_estimated_stock,
            COALESCE(ib.server_confirmed_stock, 0) AS server_confirmed_stock,
            pv.min_stock_threshold AS min_stock_threshold
        FROM product_variants pv
        INNER JOIN products p ON p.id = pv.product_id AND p.business_id = pv.business_id
        LEFT JOIN inventory_balances ib ON ib.variant_id = pv.id AND ib.business_id = pv.business_id
        WHERE pv.business_id = :businessId
          AND pv.is_active = 1
          AND p.track_inventory = 1
          AND COALESCE(ib.local_estimated_stock, 0) <= pv.min_stock_threshold
        ORDER BY COALESCE(ib.local_estimated_stock, 0) ASC
        """
    )
    fun observeLowStockAlerts(businessId: String): Flow<List<LowStockVariantAlertTuple>>

    @Query(
        """
        SELECT COALESCE(SUM(quantity_delta), 0) 
        FROM inventory_movements 
        WHERE business_id = :businessId 
          AND variant_id = :variantId 
          AND sync_status != 'SYNCED'
        """
    )
    suspend fun sumUnconfirmedLocalDelta(businessId: String, variantId: String): Long

    @Query(
        """
        UPDATE inventory_movements
        SET sync_status = :syncStatus
        WHERE business_id = :businessId AND id = :movementId
        """
    )
    suspend fun updateMovementSyncStatus(
        businessId: String,
        movementId: String,
        syncStatus: SyncStatus
    )

    @Query(
        """
        SELECT * FROM inventory_movements
        WHERE business_id = :businessId AND id = :movementId
        LIMIT 1
        """
    )
    suspend fun getMovementById(businessId: String, movementId: String): InventoryMovementEntity?

    @Transaction
    suspend fun adjustLocalStock(
        businessId: String,
        variantId: String,
        quantityDelta: Long,
        movementType: MovementType,
        unitCostRp: Long,
        userId: String,
        referenceId: String?,
        referenceType: String?,
        notes: String?,
        nowEpochMs: Long,
        outboxEntry: SyncOutboxEntity? = null
    ): InventoryMovementEntity {
        val existingBalance = getBalanceForVariant(businessId, variantId)
        val stockBefore = existingBalance?.localEstimatedStock ?: 0L
        val stockAfter = stockBefore + quantityDelta
        val updatedBalance = if (existingBalance != null) {
            existingBalance.copy(
                localEstimatedStock = stockAfter,
                valuationCostRp = stockAfter.coerceAtLeast(0L) * unitCostRp,
                syncStatus = SyncStatus.PENDING,
                version = existingBalance.version + 1L,
                updatedAt = nowEpochMs
            )
        } else {
            InventoryBalanceEntity(
                id = UUID.randomUUID().toString(),
                businessId = businessId,
                variantId = variantId,
                localEstimatedStock = stockAfter,
                serverConfirmedStock = 0L,
                reservedStock = 0L,
                valuationCostRp = stockAfter.coerceAtLeast(0L) * unitCostRp,
                syncStatus = SyncStatus.PENDING,
                lastReconciledAt = null,
                version = 1L,
                updatedAt = nowEpochMs
            )
        }
        upsertBalance(updatedBalance)

        val movement = InventoryMovementEntity(
            id = UUID.randomUUID().toString(),
            businessId = businessId,
            variantId = variantId,
            movementType = movementType,
            quantityDelta = quantityDelta,
            stockBefore = stockBefore,
            stockAfter = stockAfter,
            unitCostRp = unitCostRp,
            referenceId = referenceId,
            referenceType = referenceType,
            notes = notes,
            createdByUserId = userId,
            syncStatus = SyncStatus.PENDING,
            createdAt = nowEpochMs
        )
        insertMovement(movement)

        if (outboxEntry != null) {
            insertOutboxForInventory(outboxEntry)
        }
        return movement
    }

    @Transaction
    suspend fun reconcileServerConfirmedStock(
        businessId: String,
        variantId: String,
        serverConfirmedStock: Long,
        unitCostRp: Long,
        reconciledAtEpochMs: Long
    ) {
        val unconfirmedDelta = sumUnconfirmedLocalDelta(businessId, variantId)
        val recalculatedEstimated = serverConfirmedStock + unconfirmedDelta
        val existing = getBalanceForVariant(businessId, variantId)
        if (existing != null) {
            upsertBalance(
                existing.copy(
                    serverConfirmedStock = serverConfirmedStock,
                    localEstimatedStock = recalculatedEstimated,
                    valuationCostRp = recalculatedEstimated.coerceAtLeast(0L) * unitCostRp,
                    syncStatus = if (unconfirmedDelta == 0L) SyncStatus.SYNCED else SyncStatus.PENDING,
                    lastReconciledAt = reconciledAtEpochMs,
                    version = existing.version + 1L,
                    updatedAt = reconciledAtEpochMs
                )
            )
        }
    }
}
