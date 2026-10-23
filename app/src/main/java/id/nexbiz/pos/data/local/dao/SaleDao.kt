package id.nexbiz.pos.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import id.nexbiz.pos.data.local.entity.AccountEntity
import id.nexbiz.pos.data.local.entity.AuditLogEntity
import id.nexbiz.pos.data.local.entity.CashMovementEntity
import id.nexbiz.pos.data.local.entity.ExpenseEntity
import id.nexbiz.pos.data.local.entity.InventoryBalanceEntity
import id.nexbiz.pos.data.local.entity.InventoryMovementEntity
import id.nexbiz.pos.data.local.entity.JournalEntryEntity
import id.nexbiz.pos.data.local.entity.JournalLineEntity
import id.nexbiz.pos.data.local.entity.PaymentEntity
import id.nexbiz.pos.data.local.entity.SaleEntity
import id.nexbiz.pos.data.local.entity.SaleItemEntity
import id.nexbiz.pos.data.local.entity.SyncOutboxEntity
import id.nexbiz.pos.data.local.model.MovementType
import id.nexbiz.pos.data.local.model.OutboxOperation
import id.nexbiz.pos.data.local.model.PaymentMethod
import id.nexbiz.pos.data.local.model.SyncStatus
import id.nexbiz.pos.data.local.model.UserRole
import java.util.UUID
import kotlinx.coroutines.flow.Flow

data class SaleWithItemsAndPayments(
    @Embedded val sale: SaleEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "sale_id"
    )
    val items: List<SaleItemEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "sale_id"
    )
    val payments: List<PaymentEntity>
)

@Dao
interface SaleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSale(sale: SaleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSaleItems(items: List<SaleItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayments(payments: List<PaymentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccounts(accounts: List<AccountEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccount(account: AccountEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJournalEntry(entry: JournalEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJournalLines(lines: List<JournalLineEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCashMovement(cashMovement: CashMovementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInventoryBalanceInternal(balance: InventoryBalanceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInventoryMovementsInternal(movements: List<InventoryMovementEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncOutboxInternal(outbox: SyncOutboxEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLogInternal(auditLog: AuditLogEntity)

    @Query(
        """
        SELECT * FROM inventory_balances 
        WHERE business_id = :businessId AND variant_id = :variantId 
        LIMIT 1
        """
    )
    suspend fun getBalanceForVariantInternal(
        businessId: String,
        variantId: String
    ): InventoryBalanceEntity?

    @Query(
        """
        SELECT * FROM accounts 
        WHERE business_id = :businessId AND code = :accountCode 
        LIMIT 1
        """
    )
    suspend fun getAccountByCode(businessId: String, accountCode: String): AccountEntity?

    @Query(
        """
        SELECT * FROM accounts 
        WHERE business_id = :businessId AND is_active = 1 
        ORDER BY code ASC
        """
    )
    fun observeAccounts(businessId: String): Flow<List<AccountEntity>>

    @Transaction
    @Query(
        """
        SELECT * FROM sales 
        WHERE business_id = :businessId 
        ORDER BY created_at DESC 
        LIMIT :limit
        """
    )
    fun observeRecentSalesWithDetails(
        businessId: String,
        limit: Int = 100
    ): Flow<List<SaleWithItemsAndPayments>>

    @Transaction
    @Query("SELECT * FROM sales WHERE business_id = :businessId AND id = :saleId LIMIT 1")
    suspend fun getSaleWithDetailsById(
        businessId: String,
        saleId: String
    ): SaleWithItemsAndPayments?

    @Query(
        """
        SELECT COALESCE(SUM(grand_total_rp), 0) 
        FROM sales 
        WHERE business_id = :businessId 
          AND is_voided = 0 
          AND created_at >= :startEpochMs 
          AND created_at <= :endEpochMs
        """
    )
    fun observeRevenueBetween(
        businessId: String,
        startEpochMs: Long,
        endEpochMs: Long
    ): Flow<Long>

    @Query(
        """
        SELECT COALESCE(SUM(total_cogs_rp), 0) 
        FROM sales 
        WHERE business_id = :businessId 
          AND is_voided = 0 
          AND created_at >= :startEpochMs 
          AND created_at <= :endEpochMs
        """
    )
    fun observeCogsBetween(
        businessId: String,
        startEpochMs: Long,
        endEpochMs: Long
    ): Flow<Long>

    @Query(
        """
        SELECT COUNT(*) 
        FROM sales 
        WHERE business_id = :businessId 
          AND is_voided = 0 
          AND created_at >= :startEpochMs 
          AND created_at <= :endEpochMs
        """
    )
    fun observeSaleCountBetween(
        businessId: String,
        startEpochMs: Long,
        endEpochMs: Long
    ): Flow<Int>

    @Query(
        """
        SELECT * FROM journal_entries 
        WHERE business_id = :businessId 
        ORDER BY entry_date DESC 
        LIMIT :limit
        """
    )
    fun observeRecentJournalEntries(
        businessId: String,
        limit: Int = 100
    ): Flow<List<JournalEntryEntity>>

    @Query(
        """
        SELECT * FROM journal_lines
        WHERE business_id = :businessId AND journal_entry_id = :journalEntryId
        ORDER BY debit_rp DESC, account_code ASC
        """
    )
    suspend fun getJournalLinesForEntry(
        businessId: String,
        journalEntryId: String
    ): List<JournalLineEntity>

    @Query(
        """
        SELECT * FROM expenses
        WHERE business_id = :businessId
        ORDER BY expense_date DESC
        LIMIT :limit
        """
    )
    fun observeRecentExpenses(
        businessId: String,
        limit: Int = 100
    ): Flow<List<ExpenseEntity>>

    @Query(
        """
        SELECT * FROM cash_movements
        WHERE business_id = :businessId
        ORDER BY created_at DESC
        LIMIT :limit
        """
    )
    fun observeRecentCashMovements(
        businessId: String,
        limit: Int = 100
    ): Flow<List<CashMovementEntity>>

    @Transaction
    suspend fun recordOfflineExpense(
        expense: ExpenseEntity,
        outboxPayloadJson: String
    ) {
        val now = expense.createdAt
        val businessId = expense.businessId
        insertExpense(expense)

        val expenseAccount = getAccountByCode(businessId, expense.expenseAccountCode)
        val paidFromAccount = getAccountByCode(businessId, expense.paidFromAccountCode)

        if (expenseAccount != null && paidFromAccount != null) {
            val journalId = UUID.randomUUID().toString()
            val totalAmountRp = expense.amountRp + expense.taxRp

            val lines = listOf(
                JournalLineEntity(
                    id = UUID.randomUUID().toString(),
                    businessId = businessId,
                    journalEntryId = journalId,
                    accountId = expenseAccount.id,
                    accountCode = expenseAccount.code,
                    debitRp = totalAmountRp,
                    creditRp = 0L,
                    memo = "Beban ${expense.categoryName} #${expense.expenseNumber}",
                    syncStatus = SyncStatus.PENDING,
                    createdAt = now
                ),
                JournalLineEntity(
                    id = UUID.randomUUID().toString(),
                    businessId = businessId,
                    journalEntryId = journalId,
                    accountId = paidFromAccount.id,
                    accountCode = paidFromAccount.code,
                    debitRp = 0L,
                    creditRp = totalAmountRp,
                    memo = "Pembayaran beban #${expense.expenseNumber}",
                    syncStatus = SyncStatus.PENDING,
                    createdAt = now
                )
            )

            upsertAccount(
                expenseAccount.copy(
                    currentBalanceRp = expenseAccount.currentBalanceRp + totalAmountRp,
                    updatedAt = now
                )
            )
            upsertAccount(
                paidFromAccount.copy(
                    currentBalanceRp = paidFromAccount.currentBalanceRp - totalAmountRp,
                    updatedAt = now
                )
            )

            val totalDebitRp = lines.sumOf { it.debitRp }
            val totalCreditRp = lines.sumOf { it.creditRp }

            insertJournalEntry(
                JournalEntryEntity(
                    id = journalId,
                    businessId = businessId,
                    entryNumber = "JE-${expense.expenseNumber}",
                    referenceType = "EXPENSE",
                    referenceId = expense.id,
                    description = "Jurnal Beban ${expense.categoryName} (${expense.vendorName ?: "Operasional"})",
                    totalDebitRp = totalDebitRp,
                    totalCreditRp = totalCreditRp,
                    isBalanced = totalDebitRp == totalCreditRp,
                    postedByUserId = expense.recordedByUserId,
                    entryDate = now,
                    syncStatus = SyncStatus.PENDING,
                    createdAt = now
                )
            )
            insertJournalLines(lines)
        }

        insertSyncOutboxInternal(
            SyncOutboxEntity(
                id = UUID.randomUUID().toString(),
                businessId = businessId,
                idempotencyKey = "expense-create-${expense.id}",
                operation = OutboxOperation.RECORD_EXPENSE,
                entityType = "ExpenseEntity",
                entityId = expense.id,
                payloadJson = outboxPayloadJson,
                syncStatus = SyncStatus.PENDING,
                retryCount = 0,
                maxRetries = 5,
                nextAttemptAt = now,
                lastErrorCode = null,
                lastErrorMessage = null,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    @Transaction
    suspend fun recordOfflineCashMovement(
        movement: CashMovementEntity,
        outboxPayloadJson: String
    ) {
        val now = movement.createdAt
        val businessId = movement.businessId
        insertCashMovement(movement)

        val cashAccount = getAccountByCode(businessId, movement.cashAccountCode)
        val equityAccount = getAccountByCode(businessId, "3101")

        if (cashAccount != null && equityAccount != null) {
            val journalId = UUID.randomUUID().toString()
            val lines = if (movement.isCashIn) {
                listOf(
                    JournalLineEntity(
                        id = UUID.randomUUID().toString(),
                        businessId = businessId,
                        journalEntryId = journalId,
                        accountId = cashAccount.id,
                        accountCode = cashAccount.code,
                        debitRp = movement.amountRp,
                        creditRp = 0L,
                        memo = "Kas Masuk: ${movement.reason}",
                        syncStatus = SyncStatus.PENDING,
                        createdAt = now
                    ),
                    JournalLineEntity(
                        id = UUID.randomUUID().toString(),
                        businessId = businessId,
                        journalEntryId = journalId,
                        accountId = equityAccount.id,
                        accountCode = equityAccount.code,
                        debitRp = 0L,
                        creditRp = movement.amountRp,
                        memo = "Setoran Modal / Kas Masuk Shift",
                        syncStatus = SyncStatus.PENDING,
                        createdAt = now
                    )
                )
            } else {
                listOf(
                    JournalLineEntity(
                        id = UUID.randomUUID().toString(),
                        businessId = businessId,
                        journalEntryId = journalId,
                        accountId = equityAccount.id,
                        accountCode = equityAccount.code,
                        debitRp = movement.amountRp,
                        creditRp = 0L,
                        memo = "Penarikan Kas / Setor Kasir: ${movement.reason}",
                        syncStatus = SyncStatus.PENDING,
                        createdAt = now
                    ),
                    JournalLineEntity(
                        id = UUID.randomUUID().toString(),
                        businessId = businessId,
                        journalEntryId = journalId,
                        accountId = cashAccount.id,
                        accountCode = cashAccount.code,
                        debitRp = 0L,
                        creditRp = movement.amountRp,
                        memo = "Kas Keluar: ${movement.reason}",
                        syncStatus = SyncStatus.PENDING,
                        createdAt = now
                    )
                )
            }

            val delta = if (movement.isCashIn) movement.amountRp else -movement.amountRp
            upsertAccount(
                cashAccount.copy(
                    currentBalanceRp = cashAccount.currentBalanceRp + delta,
                    updatedAt = now
                )
            )
            upsertAccount(
                equityAccount.copy(
                    currentBalanceRp = equityAccount.currentBalanceRp + delta,
                    updatedAt = now
                )
            )

            insertJournalEntry(
                JournalEntryEntity(
                    id = journalId,
                    businessId = businessId,
                    entryNumber = "JE-CASH-${now % 100000}",
                    referenceType = "CASH_MOVEMENT",
                    referenceId = movement.id,
                    description = if (movement.isCashIn) "Kas Masuk: ${movement.reason}" else "Kas Keluar: ${movement.reason}",
                    totalDebitRp = movement.amountRp,
                    totalCreditRp = movement.amountRp,
                    isBalanced = true,
                    postedByUserId = movement.performedByUserId,
                    entryDate = now,
                    syncStatus = SyncStatus.PENDING,
                    createdAt = now
                )
            )
            insertJournalLines(lines)
        }

        insertSyncOutboxInternal(
            SyncOutboxEntity(
                id = UUID.randomUUID().toString(),
                businessId = businessId,
                idempotencyKey = "cash-movement-${movement.id}",
                operation = OutboxOperation.RECORD_CASH_MOVEMENT,
                entityType = "CashMovementEntity",
                entityId = movement.id,
                payloadJson = outboxPayloadJson,
                syncStatus = SyncStatus.PENDING,
                retryCount = 0,
                maxRetries = 5,
                nextAttemptAt = now,
                lastErrorCode = null,
                lastErrorMessage = null,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    @Query(
        """
        SELECT * FROM accounts
        WHERE business_id = :businessId AND id = :accountId
        LIMIT 1
        """
    )
    suspend fun getAccountById(businessId: String, accountId: String): AccountEntity?

    @Query(
        """
        UPDATE sales
        SET sync_status = :syncStatus, version = :serverVersion, updated_at = :updatedAt
        WHERE business_id = :businessId AND id = :saleId
        """
    )
    suspend fun updateSaleSyncStatus(
        businessId: String,
        saleId: String,
        syncStatus: SyncStatus,
        serverVersion: Long,
        updatedAt: Long
    )

    @Query(
        """
        UPDATE sale_items
        SET sync_status = :syncStatus
        WHERE business_id = :businessId AND sale_id = :saleId
        """
    )
    suspend fun updateSaleItemsSyncStatus(
        businessId: String,
        saleId: String,
        syncStatus: SyncStatus
    )

    @Query(
        """
        UPDATE payments
        SET sync_status = :syncStatus
        WHERE business_id = :businessId AND sale_id = :saleId
        """
    )
    suspend fun updatePaymentsSyncStatus(
        businessId: String,
        saleId: String,
        syncStatus: SyncStatus
    )

    @Query(
        """
        UPDATE inventory_movements
        SET sync_status = :syncStatus
        WHERE business_id = :businessId AND reference_id = :referenceId
        """
    )
    suspend fun updateMovementsSyncStatusByReference(
        businessId: String,
        referenceId: String,
        syncStatus: SyncStatus
    )

    @Query(
        """
        UPDATE journal_entries
        SET sync_status = :syncStatus
        WHERE business_id = :businessId AND reference_id = :referenceId
        """
    )
    suspend fun updateJournalEntrySyncStatusByReference(
        businessId: String,
        referenceId: String,
        syncStatus: SyncStatus
    )

    @Transaction
    suspend fun markSaleGraphSynced(
        businessId: String,
        saleId: String,
        serverVersion: Long,
        syncedAtMs: Long
    ) {
        updateSaleSyncStatus(businessId, saleId, SyncStatus.SYNCED, serverVersion, syncedAtMs)
        updateSaleItemsSyncStatus(businessId, saleId, SyncStatus.SYNCED)
        updatePaymentsSyncStatus(businessId, saleId, SyncStatus.SYNCED)
        updateMovementsSyncStatusByReference(businessId, saleId, SyncStatus.SYNCED)
        updateJournalEntrySyncStatusByReference(businessId, saleId, SyncStatus.SYNCED)
    }

    /**
     * Atomic Offline-First POS Checkout Transaction:
     * 1. Persists SaleEntity, SaleItemEntity list, and PaymentEntity list (all Long IDR amounts).
     * 2. Deducts localEstimatedStock in InventoryBalanceEntity and appends InventoryMovementEntity
     *    (MovementType.SALE_DEDUCTION) for every line item.
     * 3. Posts a balanced Double-Entry JournalEntryEntity + JournalLineEntity list against the
     *    Indonesian Chart of Accounts (Kas/Bank, Pendapatan Penjualan, PPN Keluaran, Diskon, HPP, Persediaan)
     *    and updates AccountEntity balances.
     * 4. Enqueues a SyncOutboxEntity (OutboxOperation.CREATE_SALE_TRANSACTION, SyncStatus.PENDING)
     *    with an idempotency key and records an AuditLogEntity.
     */
    @Transaction
    suspend fun completeOfflineSale(
        sale: SaleEntity,
        items: List<SaleItemEntity>,
        payments: List<PaymentEntity>,
        outboxPayloadJson: String,
        actorRole: UserRole = UserRole.CASHIER,
        deviceId: String = "ANDROID-POS-TERMINAL"
    ) {
        val nowEpochMs = sale.createdAt
        val businessId = sale.businessId

        // 1. Insert Sale Header, Line Items, and Payments
        insertSale(sale)
        insertSaleItems(items)
        insertPayments(payments)

        // 2. Deduct Local Estimated Stock & Append Inventory Movements
        val movements = ArrayList<InventoryMovementEntity>(items.size)
        for (item in items) {
            val currentBalance = getBalanceForVariantInternal(businessId, item.variantId)
            val stockBefore = currentBalance?.localEstimatedStock ?: 0L
            val quantityDelta = -item.quantity
            val stockAfter = stockBefore + quantityDelta

            if (currentBalance != null) {
                upsertInventoryBalanceInternal(
                    currentBalance.copy(
                        localEstimatedStock = stockAfter,
                        valuationCostRp = stockAfter.coerceAtLeast(0L) * item.unitCostRp,
                        syncStatus = SyncStatus.PENDING,
                        version = currentBalance.version + 1L,
                        updatedAt = nowEpochMs
                    )
                )
            } else {
                upsertInventoryBalanceInternal(
                    InventoryBalanceEntity(
                        id = UUID.randomUUID().toString(),
                        businessId = businessId,
                        variantId = item.variantId,
                        localEstimatedStock = stockAfter,
                        serverConfirmedStock = 0L,
                        reservedStock = 0L,
                        valuationCostRp = 0L,
                        syncStatus = SyncStatus.PENDING,
                        lastReconciledAt = null,
                        version = 1L,
                        updatedAt = nowEpochMs
                    )
                )
            }

            movements.add(
                InventoryMovementEntity(
                    id = UUID.randomUUID().toString(),
                    businessId = businessId,
                    variantId = item.variantId,
                    movementType = MovementType.SALE_DEDUCTION,
                    quantityDelta = quantityDelta,
                    stockBefore = stockBefore,
                    stockAfter = stockAfter,
                    unitCostRp = item.unitCostRp,
                    referenceId = sale.id,
                    referenceType = "SALE",
                    notes = "Penjualan POS #${sale.receiptNumber}",
                    createdByUserId = sale.cashierUserId,
                    syncStatus = SyncStatus.PENDING,
                    createdAt = nowEpochMs
                )
            )
        }
        insertInventoryMovementsInternal(movements)

        // 3. Post Double-Entry Accounting Journal (Indonesian COA)
        val cashAccountCode = when (sale.paymentMethod) {
            PaymentMethod.CASH -> "1101" // Kas Tunai Kasir
            PaymentMethod.CREDIT_TEMPO -> "1103" // Piutang Usaha
            else -> "1102" // Bank BCA / QRIS & Non-Tunai
        }

        val assetAccount = getAccountByCode(businessId, cashAccountCode)
        val revenueAccount = getAccountByCode(businessId, "4101") // Pendapatan Penjualan POS
        val discountAccount = getAccountByCode(businessId, "4102") // Potongan / Diskon Penjualan
        val taxPayableAccount = getAccountByCode(businessId, "2102") // Utang PPN Keluaran
        val cogsAccount = getAccountByCode(businessId, "5101") // Harga Pokok Penjualan (HPP)
        val inventoryAccount = getAccountByCode(businessId, "1104") // Persediaan Barang Dagang

        if (assetAccount != null && revenueAccount != null) {
            val journalEntryId = UUID.randomUUID().toString()
            val lines = mutableListOf<JournalLineEntity>()

            // Debit Kas / Bank / Piutang (Grand Total Received)
            lines.add(
                JournalLineEntity(
                    id = UUID.randomUUID().toString(),
                    businessId = businessId,
                    journalEntryId = journalEntryId,
                    accountId = assetAccount.id,
                    accountCode = assetAccount.code,
                    debitRp = sale.grandTotalRp,
                    creditRp = 0L,
                    memo = "Penerimaan ${sale.paymentMethod.name} #${sale.receiptNumber}",
                    syncStatus = SyncStatus.PENDING,
                    createdAt = nowEpochMs
                )
            )
            upsertAccount(
                assetAccount.copy(
                    currentBalanceRp = assetAccount.currentBalanceRp + sale.grandTotalRp,
                    updatedAt = nowEpochMs
                )
            )

            // Debit Potongan Penjualan (if discountTotalRp > 0)
            if (sale.discountTotalRp > 0L && discountAccount != null) {
                lines.add(
                    JournalLineEntity(
                        id = UUID.randomUUID().toString(),
                        businessId = businessId,
                        journalEntryId = journalEntryId,
                        accountId = discountAccount.id,
                        accountCode = discountAccount.code,
                        debitRp = sale.discountTotalRp,
                        creditRp = 0L,
                        memo = "Diskon penjualan #${sale.receiptNumber}",
                        syncStatus = SyncStatus.PENDING,
                        createdAt = nowEpochMs
                    )
                )
                upsertAccount(
                    discountAccount.copy(
                        currentBalanceRp = discountAccount.currentBalanceRp + sale.discountTotalRp,
                        updatedAt = nowEpochMs
                    )
                )
            }

            // Credit Pendapatan Penjualan POS (Gross Subtotal)
            lines.add(
                JournalLineEntity(
                    id = UUID.randomUUID().toString(),
                    businessId = businessId,
                    journalEntryId = journalEntryId,
                    accountId = revenueAccount.id,
                    accountCode = revenueAccount.code,
                    debitRp = 0L,
                    creditRp = sale.subtotalRp,
                    memo = "Pendapatan penjualan #${sale.receiptNumber}",
                    syncStatus = SyncStatus.PENDING,
                    createdAt = nowEpochMs
                )
            )
            upsertAccount(
                revenueAccount.copy(
                    currentBalanceRp = revenueAccount.currentBalanceRp + sale.subtotalRp,
                    updatedAt = nowEpochMs
                )
            )

            // Credit Utang PPN Keluaran (if taxTotalRp > 0)
            if (sale.taxTotalRp > 0L && taxPayableAccount != null) {
                lines.add(
                    JournalLineEntity(
                        id = UUID.randomUUID().toString(),
                        businessId = businessId,
                        journalEntryId = journalEntryId,
                        accountId = taxPayableAccount.id,
                        accountCode = taxPayableAccount.code,
                        debitRp = 0L,
                        creditRp = sale.taxTotalRp,
                        memo = "PPN Keluaran #${sale.receiptNumber}",
                        syncStatus = SyncStatus.PENDING,
                        createdAt = nowEpochMs
                    )
                )
                upsertAccount(
                    taxPayableAccount.copy(
                        currentBalanceRp = taxPayableAccount.currentBalanceRp + sale.taxTotalRp,
                        updatedAt = nowEpochMs
                    )
                )
            }

            // Debit HPP & Credit Persediaan Barang Dagang (Perpetual Inventory Valuation)
            if (sale.totalCogsRp > 0L && cogsAccount != null && inventoryAccount != null) {
                lines.add(
                    JournalLineEntity(
                        id = UUID.randomUUID().toString(),
                        businessId = businessId,
                        journalEntryId = journalEntryId,
                        accountId = cogsAccount.id,
                        accountCode = cogsAccount.code,
                        debitRp = sale.totalCogsRp,
                        creditRp = 0L,
                        memo = "HPP penjualan #${sale.receiptNumber}",
                        syncStatus = SyncStatus.PENDING,
                        createdAt = nowEpochMs
                    )
                )
                upsertAccount(
                    cogsAccount.copy(
                        currentBalanceRp = cogsAccount.currentBalanceRp + sale.totalCogsRp,
                        updatedAt = nowEpochMs
                    )
                )

                lines.add(
                    JournalLineEntity(
                        id = UUID.randomUUID().toString(),
                        businessId = businessId,
                        journalEntryId = journalEntryId,
                        accountId = inventoryAccount.id,
                        accountCode = inventoryAccount.code,
                        debitRp = 0L,
                        creditRp = sale.totalCogsRp,
                        memo = "Penurunan persediaan #${sale.receiptNumber}",
                        syncStatus = SyncStatus.PENDING,
                        createdAt = nowEpochMs
                    )
                )
                upsertAccount(
                    inventoryAccount.copy(
                        currentBalanceRp = inventoryAccount.currentBalanceRp - sale.totalCogsRp,
                        updatedAt = nowEpochMs
                    )
                )
            }

            val totalDebitRp = lines.sumOf { it.debitRp }
            val totalCreditRp = lines.sumOf { it.creditRp }

            insertJournalEntry(
                JournalEntryEntity(
                    id = journalEntryId,
                    businessId = businessId,
                    entryNumber = "JE-${sale.receiptNumber}",
                    referenceType = "SALE",
                    referenceId = sale.id,
                    description = "Jurnal Penjualan POS #${sale.receiptNumber}",
                    totalDebitRp = totalDebitRp,
                    totalCreditRp = totalCreditRp,
                    isBalanced = totalDebitRp == totalCreditRp,
                    postedByUserId = sale.cashierUserId,
                    entryDate = nowEpochMs,
                    syncStatus = SyncStatus.PENDING,
                    createdAt = nowEpochMs
                )
            )
            insertJournalLines(lines)
        }

        // 4. Enqueue Outbox Operation & Record Audit Log
        insertSyncOutboxInternal(
            SyncOutboxEntity(
                id = UUID.randomUUID().toString(),
                businessId = businessId,
                idempotencyKey = "sale-create-${sale.id}",
                operation = OutboxOperation.CREATE_SALE_TRANSACTION,
                entityType = "SaleEntity",
                entityId = sale.id,
                payloadJson = outboxPayloadJson,
                syncStatus = SyncStatus.PENDING,
                retryCount = 0,
                maxRetries = 5,
                nextAttemptAt = nowEpochMs,
                lastErrorCode = null,
                lastErrorMessage = null,
                createdAt = nowEpochMs,
                updatedAt = nowEpochMs
            )
        )

        insertAuditLogInternal(
            AuditLogEntity(
                id = UUID.randomUUID().toString(),
                businessId = businessId,
                actorUserId = sale.cashierUserId,
                actorRole = actorRole,
                action = "COMPLETE_OFFLINE_SALE",
                entityType = "SaleEntity",
                entityId = sale.id,
                beforeStateJson = null,
                afterStateJson = outboxPayloadJson,
                deviceId = deviceId,
                syncStatus = SyncStatus.PENDING,
                createdAt = nowEpochMs
            )
        )
    }
}
