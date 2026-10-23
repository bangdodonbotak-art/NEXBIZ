package id.nexbiz.pos.ui.screens.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import id.nexbiz.pos.core.analytics.BreakEvenAnalysisResult
import id.nexbiz.pos.core.analytics.BreakEvenCalculator
import id.nexbiz.pos.core.sync.SyncScheduler
import id.nexbiz.pos.data.local.dao.InventoryDao
import id.nexbiz.pos.data.local.dao.LowStockVariantAlertTuple
import id.nexbiz.pos.data.local.dao.ProductDao
import id.nexbiz.pos.data.local.dao.SaleDao
import id.nexbiz.pos.data.local.dao.SaleWithItemsAndPayments
import id.nexbiz.pos.data.local.entity.AccountEntity
import id.nexbiz.pos.data.local.entity.BusinessEntity
import id.nexbiz.pos.data.local.entity.CashMovementEntity
import id.nexbiz.pos.data.local.entity.ExpenseEntity
import id.nexbiz.pos.data.local.entity.InventoryBalanceEntity
import id.nexbiz.pos.data.local.entity.JournalEntryEntity
import id.nexbiz.pos.data.local.model.PaymentMethod
import id.nexbiz.pos.data.local.model.SyncStatus
import id.nexbiz.pos.data.repository.SyncRepository
import id.nexbiz.pos.di.DatabaseModule
import id.nexbiz.pos.di.NetworkModule
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class ProfitAndLossReport(
    val grossSalesRevenueRp: Long = 0L,
    val salesDiscountsRp: Long = 0L,
    val netSalesRevenueRp: Long = 0L,
    val costOfGoodsSoldRp: Long = 0L,
    val grossProfitRp: Long = 0L,
    val grossMarginBps: Long = 0L,
    val storeOperationalExpenseRp: Long = 0L,
    val salaryPayrollExpenseRp: Long = 0L,
    val utilitiesInternetExpenseRp: Long = 0L,
    val totalOperatingExpensesRp: Long = 0L,
    val netOperatingProfitRp: Long = 0L,
    val netMarginBps: Long = 0L,
    val outputVatPayableRp: Long = 0L
)

data class CashFlowReport(
    val cashSalesInflowRp: Long = 0L,
    val nonCashQrisBankInflowRp: Long = 0L,
    val totalOperatingCashInflowRp: Long = 0L,
    val cashExpensesOutflowRp: Long = 0L,
    val bankExpensesOutflowRp: Long = 0L,
    val netOperatingCashFlowRp: Long = 0L,
    val manualCashInRp: Long = 0L,
    val manualCashOutRp: Long = 0L,
    val netFinancingCashFlowRp: Long = 0L,
    val endingCashDrawerBalanceRp: Long = 0L,
    val endingBankSettlementBalanceRp: Long = 0L,
    val totalLiquidCashAndBankRp: Long = 0L
)

data class DashboardUiState(
    val business: BusinessEntity? = null,
    val totalTransactionsCount: Int = 0,
    val totalUnitsSold: Long = 0L,
    val inventoryValuationRp: Long = 0L,
    val totalSkusTracked: Int = 0,
    val fixedMonthlyTargetCostRp: Long = 3_500_000L,
    val breakEvenAnalysis: BreakEvenAnalysisResult = BreakEvenCalculator.calculate(
        grossRevenueRp = 0L,
        discountTotalRp = 0L,
        totalCogsRp = 0L,
        fixedOperatingCostsRp = 3_500_000L,
        currentUnitsSold = 0L
    ),
    val profitAndLoss: ProfitAndLossReport = ProfitAndLossReport(),
    val cashFlow: CashFlowReport = CashFlowReport(),
    val lowStockAlerts: List<LowStockVariantAlertTuple> = emptyList(),
    val recentExpenses: List<ExpenseEntity> = emptyList(),
    val recentCashMovements: List<CashMovementEntity> = emptyList(),
    val recentJournals: List<JournalEntryEntity> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    val unsyncedOutboxCount: Int = 0,
    val isAllJournalsBalanced: Boolean = true,
    val statusBannerMessage: String? = null
)

class DashboardViewModel(
    private val appContext: Context,
    private val productDao: ProductDao,
    private val saleDao: SaleDao,
    private val inventoryDao: InventoryDao,
    private val syncRepository: SyncRepository,
    private val businessId: String = DatabaseModule.DEFAULT_BUSINESS_ID,
    private val activeUserId: String = DatabaseModule.DEFAULT_OWNER_USER_ID
) : ViewModel() {

    private val fixedCostBaselineRpFlow = MutableStateFlow(3_500_000L)
    private val statusBannerMessageFlow = MutableStateFlow<String?>(null)

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                productDao.observeBusiness(businessId),
                saleDao.observeRecentSalesWithDetails(businessId, 200),
                saleDao.observeAccounts(businessId),
                saleDao.observeRecentExpenses(businessId, 100),
                saleDao.observeRecentCashMovements(businessId, 100)
            ) { business, sales, accounts, expenses, cashMovements ->
                FinancialSourceBundle(
                    business = business,
                    sales = sales,
                    accounts = accounts,
                    expenses = expenses,
                    cashMovements = cashMovements
                )
            }.combine(
                combine(
                    inventoryDao.observeBalances(businessId),
                    inventoryDao.observeLowStockAlerts(businessId),
                    saleDao.observeRecentJournalEntries(businessId, 100),
                    syncRepository.observeUnsyncedOutboxCount(businessId),
                    fixedCostBaselineRpFlow
                ) { balances, lowStock, journals, unsyncedCount, fixedBaselineRp ->
                    OperationalSourceBundle(
                        balances = balances,
                        lowStock = lowStock,
                        journals = journals,
                        unsyncedCount = unsyncedCount,
                        fixedBaselineRp = fixedBaselineRp
                    )
                }
            ) { fin, ops ->
                val activeSales = fin.sales.filterNot { it.sale.isVoided }
                val totalUnitsSold = activeSales.sumOf { bundle ->
                    bundle.items.sumOf { it.quantity }
                }

                // Compute P&L from sales & COA accounts/expenses (pure Long IDR)
                val grossSalesRp = activeSales.sumOf { it.sale.subtotalRp }
                val discountsRp = activeSales.sumOf { it.sale.discountTotalRp }
                val netSalesRp = (grossSalesRp - discountsRp).coerceAtLeast(0L)
                val cogsRp = activeSales.sumOf { it.sale.totalCogsRp }
                val vatCollectedRp = activeSales.sumOf { it.sale.taxTotalRp }
                val grossProfitRp = netSalesRp - cogsRp

                val storeExpRp = fin.expenses
                    .filter { it.expenseAccountCode == "6101" }
                    .sumOf { it.amountRp + it.taxRp }
                val salaryExpRp = fin.expenses
                    .filter { it.expenseAccountCode == "6102" }
                    .sumOf { it.amountRp + it.taxRp }
                val utilityExpRp = fin.expenses
                    .filter { it.expenseAccountCode == "6103" }
                    .sumOf { it.amountRp + it.taxRp }
                val totalRecordedExpensesRp = storeExpRp + salaryExpRp + utilityExpRp

                val netOperatingProfitRp = grossProfitRp - totalRecordedExpensesRp
                val grossMarginBps = if (netSalesRp > 0L) {
                    (grossProfitRp * 10_000L) / netSalesRp
                } else {
                    0L
                }
                val netMarginBps = if (netSalesRp > 0L) {
                    (netOperatingProfitRp * 10_000L) / netSalesRp
                } else {
                    0L
                }

                val pnlReport = ProfitAndLossReport(
                    grossSalesRevenueRp = grossSalesRp,
                    salesDiscountsRp = discountsRp,
                    netSalesRevenueRp = netSalesRp,
                    costOfGoodsSoldRp = cogsRp,
                    grossProfitRp = grossProfitRp,
                    grossMarginBps = grossMarginBps,
                    storeOperationalExpenseRp = storeExpRp,
                    salaryPayrollExpenseRp = salaryExpRp,
                    utilitiesInternetExpenseRp = utilityExpRp,
                    totalOperatingExpensesRp = totalRecordedExpensesRp,
                    netOperatingProfitRp = netOperatingProfitRp,
                    netMarginBps = netMarginBps,
                    outputVatPayableRp = vatCollectedRp
                )

                // Compute Direct Cash Flow Statement (pure Long IDR)
                var cashInflowSalesRp = 0L
                var nonCashInflowSalesRp = 0L
                for (saleBundle in activeSales) {
                    for (payment in saleBundle.payments) {
                        if (payment.paymentMethod == PaymentMethod.CASH) {
                            cashInflowSalesRp += payment.amountRp
                        } else if (payment.paymentMethod != PaymentMethod.CREDIT_TEMPO) {
                            nonCashInflowSalesRp += payment.amountRp
                        }
                    }
                }

                val cashExpenseOutflowRp = fin.expenses
                    .filter { it.paidFromAccountCode == "1101" }
                    .sumOf { it.amountRp + it.taxRp }
                val bankExpenseOutflowRp = fin.expenses
                    .filter { it.paidFromAccountCode == "1102" }
                    .sumOf { it.amountRp + it.taxRp }

                val totalInflowRp = cashInflowSalesRp + nonCashInflowSalesRp
                val netOperatingCashRp = totalInflowRp - (cashExpenseOutflowRp + bankExpenseOutflowRp)

                val manualCashInRp = fin.cashMovements
                    .filter { it.isCashIn }
                    .sumOf { it.amountRp }
                val manualCashOutRp = fin.cashMovements
                    .filterNot { it.isCashIn }
                    .sumOf { it.amountRp }

                val accountMap = fin.accounts.associateBy { it.code }
                val endingCashRp = accountMap["1101"]?.currentBalanceRp ?: 5_000_000L
                val endingBankRp = accountMap["1102"]?.currentBalanceRp ?: 45_000_000L

                val cashFlowReport = CashFlowReport(
                    cashSalesInflowRp = cashInflowSalesRp,
                    nonCashQrisBankInflowRp = nonCashInflowSalesRp,
                    totalOperatingCashInflowRp = totalInflowRp,
                    cashExpensesOutflowRp = cashExpenseOutflowRp,
                    bankExpensesOutflowRp = bankExpenseOutflowRp,
                    netOperatingCashFlowRp = netOperatingCashRp,
                    manualCashInRp = manualCashInRp,
                    manualCashOutRp = manualCashOutRp,
                    netFinancingCashFlowRp = manualCashInRp - manualCashOutRp,
                    endingCashDrawerBalanceRp = endingCashRp,
                    endingBankSettlementBalanceRp = endingBankRp,
                    totalLiquidCashAndBankRp = endingCashRp + endingBankRp
                )

                // Effective Fixed Cost for Break-Even Analysis = max(targetFixedCost, recordedFixedExpenses)
                val effectiveFixedCostsRp = maxOf(ops.fixedBaselineRp, totalRecordedExpensesRp)
                val bepResult = BreakEvenCalculator.calculate(
                    grossRevenueRp = grossSalesRp,
                    discountTotalRp = discountsRp,
                    totalCogsRp = cogsRp,
                    fixedOperatingCostsRp = effectiveFixedCostsRp,
                    currentUnitsSold = totalUnitsSold
                )

                val inventoryValuationRp = ops.balances.sumOf { it.valuationCostRp }
                val allBalanced = ops.journals.all {
                    it.isBalanced && it.totalDebitRp == it.totalCreditRp
                }

                DashboardUiState(
                    business = fin.business,
                    totalTransactionsCount = activeSales.size,
                    totalUnitsSold = totalUnitsSold,
                    inventoryValuationRp = inventoryValuationRp,
                    totalSkusTracked = ops.balances.size,
                    fixedMonthlyTargetCostRp = effectiveFixedCostsRp,
                    breakEvenAnalysis = bepResult,
                    profitAndLoss = pnlReport,
                    cashFlow = cashFlowReport,
                    lowStockAlerts = ops.lowStock,
                    recentExpenses = fin.expenses,
                    recentCashMovements = fin.cashMovements,
                    recentJournals = ops.journals,
                    accounts = fin.accounts,
                    unsyncedOutboxCount = ops.unsyncedCount,
                    isAllJournalsBalanced = allBalanced,
                    statusBannerMessage = statusBannerMessageFlow.value
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun updateFixedCostTargetRp(targetRp: Long) {
        fixedCostBaselineRpFlow.value = targetRp.coerceAtLeast(0L)
    }

    fun recordOperationalExpense(
        categoryName: String,
        expenseAccountCode: String,
        paidFromAccountCode: String,
        amountRp: Long,
        vendorName: String,
        notes: String
    ) {
        if (amountRp <= 0L) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val expenseId = UUID.randomUUID().toString()
            val expenseNumber = "EXP-${now % 100_000L}"
            val paymentMethod = if (paidFromAccountCode == "1101") {
                PaymentMethod.CASH
            } else {
                PaymentMethod.BANK_TRANSFER
            }

            val expense = ExpenseEntity(
                id = expenseId,
                businessId = businessId,
                expenseNumber = expenseNumber,
                categoryName = categoryName.ifBlank { "Beban Operasional" },
                expenseAccountCode = expenseAccountCode,
                paidFromAccountCode = paidFromAccountCode,
                amountRp = amountRp,
                taxRp = 0L,
                paymentMethod = paymentMethod,
                vendorName = vendorName.ifBlank { null },
                notes = notes.ifBlank { null },
                recordedByUserId = activeUserId,
                expenseDate = now,
                syncStatus = SyncStatus.PENDING,
                version = 1L,
                createdAt = now,
                updatedAt = now
            )

            val payloadJson = """{"expense_id":"$expenseId","business_id":"$businessId","expense_number":"$expenseNumber","account_code":"$expenseAccountCode","amount_rp":$amountRp,"created_at_ms":$now}"""
            saleDao.recordOfflineExpense(expense, payloadJson)
            statusBannerMessageFlow.value =
                "Beban #$expenseNumber (${categoryName}) dicatat & dijurnal seimbang"
            SyncScheduler.triggerImmediateOutboxSync(appContext, businessId)
        }
    }

    fun recordCashDrawerMovement(
        isCashIn: Boolean,
        amountRp: Long,
        reason: String
    ) {
        if (amountRp <= 0L) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val movementId = UUID.randomUUID().toString()
            val movement = CashMovementEntity(
                id = movementId,
                businessId = businessId,
                shiftId = "SHIFT-JKT-01",
                cashAccountCode = "1101",
                isCashIn = isCashIn,
                amountRp = amountRp,
                reason = reason.ifBlank {
                    if (isCashIn) "Tambahan Modal Kasir" else "Setor Kas ke Brankas"
                },
                referenceId = null,
                performedByUserId = activeUserId,
                syncStatus = SyncStatus.PENDING,
                createdAt = now
            )
            val payloadJson = """{"cash_movement_id":"$movementId","business_id":"$businessId","is_cash_in":$isCashIn,"amount_rp":$amountRp,"created_at_ms":$now}"""
            saleDao.recordOfflineCashMovement(movement, payloadJson)
            statusBannerMessageFlow.value =
                if (isCashIn) "Kas Masuk Laci dicatat" else "Kas Keluar Laci dicatat"
            SyncScheduler.triggerImmediateOutboxSync(appContext, businessId)
        }
    }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val appContext = context.applicationContext
                    val database = DatabaseModule.provideDatabase(appContext)
                    val syncRepo = NetworkModule.provideSyncRepository(database)
                    return DashboardViewModel(
                        appContext = appContext,
                        productDao = database.productDao(),
                        saleDao = database.saleDao(),
                        inventoryDao = database.inventoryDao(),
                        syncRepository = syncRepo
                    ) as T
                }
            }
    }
}

private data class FinancialSourceBundle(
    val business: BusinessEntity?,
    val sales: List<SaleWithItemsAndPayments>,
    val accounts: List<AccountEntity>,
    val expenses: List<ExpenseEntity>,
    val cashMovements: List<CashMovementEntity>
)

private data class OperationalSourceBundle(
    val balances: List<InventoryBalanceEntity>,
    val lowStock: List<LowStockVariantAlertTuple>,
    val journals: List<JournalEntryEntity>,
    val unsyncedCount: Int,
    val fixedBaselineRp: Long
)
