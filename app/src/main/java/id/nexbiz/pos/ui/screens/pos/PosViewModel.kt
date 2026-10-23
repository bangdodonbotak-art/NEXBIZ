package id.nexbiz.pos.ui.screens.pos

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import id.nexbiz.pos.core.printer.OfflineEscPosPrinterManager
import id.nexbiz.pos.core.printer.PrinterConnectionState
import id.nexbiz.pos.core.printer.ReceiptLineItemModel
import id.nexbiz.pos.core.printer.ReceiptPaymentSplitModel
import id.nexbiz.pos.core.printer.ReceiptPreviewModel
import id.nexbiz.pos.core.printer.ReceiptPrinterService
import id.nexbiz.pos.core.printer.ThermalPaperWidth
import id.nexbiz.pos.core.sync.SyncScheduler
import id.nexbiz.pos.data.local.dao.CatalogVariantItemTuple
import id.nexbiz.pos.data.local.dao.InventoryDao
import id.nexbiz.pos.data.local.dao.LowStockVariantAlertTuple
import id.nexbiz.pos.data.local.dao.ProductDao
import id.nexbiz.pos.data.local.dao.SaleDao
import id.nexbiz.pos.data.local.dao.SaleWithItemsAndPayments
import id.nexbiz.pos.data.local.entity.AccountEntity
import id.nexbiz.pos.data.local.entity.BusinessEntity
import id.nexbiz.pos.data.local.entity.CategoryEntity
import id.nexbiz.pos.data.local.entity.PaymentEntity
import id.nexbiz.pos.data.local.entity.SaleEntity
import id.nexbiz.pos.data.local.entity.SaleItemEntity
import id.nexbiz.pos.data.local.entity.SyncOutboxEntity
import id.nexbiz.pos.data.local.model.MovementType
import id.nexbiz.pos.data.local.model.OutboxOperation
import id.nexbiz.pos.data.local.model.PaymentMethod
import id.nexbiz.pos.data.local.model.SyncStatus
import id.nexbiz.pos.data.local.model.UserRole
import id.nexbiz.pos.data.remote.dto.OutboxConflictDto
import id.nexbiz.pos.data.remote.dto.PaymentPayloadDto
import id.nexbiz.pos.data.remote.dto.SaleItemPayloadDto
import id.nexbiz.pos.data.remote.dto.SaleOutboxPayloadDto
import id.nexbiz.pos.data.remote.dto.StockAdjustmentPayloadDto
import id.nexbiz.pos.data.repository.SyncExecutionOutcome
import id.nexbiz.pos.data.repository.SyncRepository
import id.nexbiz.pos.di.DatabaseModule
import id.nexbiz.pos.di.NetworkModule
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class CartItemUiModel(
    val variantId: String,
    val productId: String,
    val productName: String,
    val variantName: String,
    val sku: String,
    val unitName: String,
    val unitPriceRp: Long,
    val unitCostRp: Long,
    val isTaxable: Boolean,
    val availableStock: Long,
    val quantity: Long,
    val itemDiscountRp: Long = 0L
) {
    val grossLineRp: Long
        get() = unitPriceRp * quantity

    val clampedDiscountRp: Long
        get() = itemDiscountRp.coerceIn(0L, grossLineRp)

    val netBeforeTaxRp: Long
        get() = (grossLineRp - clampedDiscountRp).coerceAtLeast(0L)

    val totalCogsRp: Long
        get() = unitCostRp * quantity
}

data class PaymentSplitLegUi(
    val id: String = UUID.randomUUID().toString(),
    val method: PaymentMethod,
    val tenderedRp: Long,
    val referenceCode: String = ""
)

data class CartFinancialSummary(
    val totalQuantity: Long = 0L,
    val subtotalRp: Long = 0L,
    val itemDiscountTotalRp: Long = 0L,
    val orderDiscountRp: Long = 0L,
    val discountTotalRp: Long = 0L,
    val netTaxableBaseRp: Long = 0L,
    val taxRateBps: Int = 1100,
    val taxTotalRp: Long = 0L,
    val grandTotalRp: Long = 0L,
    val totalCogsRp: Long = 0L,
    val estimatedGrossProfitRp: Long = 0L,
    val totalPaidRp: Long = 0L,
    val remainingDueRp: Long = 0L,
    val changeDueRp: Long = 0L,
    val isFullyPaid: Boolean = false
)

data class PosScreenUiState(
    val business: BusinessEntity? = null,
    val categories: List<CategoryEntity> = emptyList(),
    val selectedCategoryId: String? = null,
    val searchQuery: String = "",
    val catalogVariants: List<CatalogVariantItemTuple> = emptyList(),
    val lowStockAlerts: List<LowStockVariantAlertTuple> = emptyList(),
    val cartItems: List<CartItemUiModel> = emptyList(),
    val orderDiscountRp: Long = 0L,
    val taxEnabled: Boolean = true,
    val customerName: String = "",
    val selectedPaymentMethod: PaymentMethod = PaymentMethod.CASH,
    val singleCashTenderedRp: Long = 0L,
    val splitPaymentMode: Boolean = false,
    val paymentSplits: List<PaymentSplitLegUi> = emptyList(),
    val financialSummary: CartFinancialSummary = CartFinancialSummary(),
    val unsyncedOutboxCount: Int = 0,
    val conflictOutboxCount: Int = 0,
    val outboxEntries: List<SyncOutboxEntity> = emptyList(),
    val recentSales: List<SaleWithItemsAndPayments> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    val bannerSyncStatus: SyncStatus = SyncStatus.SYNCED,
    val isSyncingNow: Boolean = false,
    val lastSyncEpochMs: Long? = null,
    val syncFeedbackMessage: String? = null,
    val activeReceiptPreview: ReceiptPreviewModel? = null,
    val activeReceiptPlainText: String = "",
    val selectedPaperWidth: ThermalPaperWidth = ThermalPaperWidth.MM_58,
    val printerState: PrinterConnectionState = PrinterConnectionState.ReadyOfflineSpooler,
    val checkoutFeedbackBanner: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class PosViewModel(
    private val appContext: Context,
    private val productDao: ProductDao,
    private val saleDao: SaleDao,
    private val inventoryDao: InventoryDao,
    private val syncRepository: SyncRepository,
    private val printerService: ReceiptPrinterService,
    private val businessId: String = DatabaseModule.DEFAULT_BUSINESS_ID,
    private val cashierUserId: String = DatabaseModule.DEFAULT_CASHIER_USER_ID
) : ViewModel() {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val searchQueryFlow = MutableStateFlow("")
    private val selectedCategoryIdFlow = MutableStateFlow<String?>(null)
    private val cartItemsFlow = MutableStateFlow<List<CartItemUiModel>>(emptyList())
    private val orderDiscountRpFlow = MutableStateFlow(0L)
    private val taxEnabledFlow = MutableStateFlow(true)
    private val customerNameFlow = MutableStateFlow("")
    private val selectedPaymentMethodFlow = MutableStateFlow(PaymentMethod.CASH)
    private val singleCashTenderedRpFlow = MutableStateFlow(0L)
    private val splitPaymentModeFlow = MutableStateFlow(false)
    private val paymentSplitsFlow = MutableStateFlow<List<PaymentSplitLegUi>>(emptyList())
    private val isSyncingNowFlow = MutableStateFlow(false)
    private val lastSyncEpochMsFlow = MutableStateFlow<Long?>(System.currentTimeMillis())
    private val syncFeedbackMessageFlow = MutableStateFlow<String?>(null)
    private val activeReceiptPreviewFlow = MutableStateFlow<ReceiptPreviewModel?>(null)
    private val selectedPaperWidthFlow = MutableStateFlow(ThermalPaperWidth.MM_58)
    private val checkoutFeedbackBannerFlow = MutableStateFlow<String?>(null)

    private val filteredCatalogFlow = combine(
        searchQueryFlow,
        selectedCategoryIdFlow
    ) { query, categoryId ->
        query.trim() to categoryId
    }.flatMapLatest { (query, categoryId) ->
        val baseFlow = if (query.isBlank()) {
            productDao.observeCatalogVariants(businessId)
        } else {
            productDao.searchCatalogVariants(businessId, query)
        }
        combine(baseFlow, selectedCategoryIdFlow) { items, activeCatId ->
            if (activeCatId == null) {
                items
            } else {
                items.filter { it.categoryId == activeCatId }
            }
        }
    }

    private val _uiState = MutableStateFlow(PosScreenUiState())
    val uiState: StateFlow<PosScreenUiState> = _uiState.asStateFlow()

    init {
        SyncScheduler.schedulePeriodicTenantSync(appContext, businessId)

        viewModelScope.launch {
            combine(
                productDao.observeBusiness(businessId),
                productDao.observeActiveCategories(businessId),
                filteredCatalogFlow,
                inventoryDao.observeLowStockAlerts(businessId),
                syncRepository.observeUnsyncedOutboxCount(businessId)
            ) { business, categories, catalog, lowStock, unsyncedCount ->
                CatalogSyncSnapshot(
                    business = business,
                    categories = categories,
                    catalog = catalog,
                    lowStock = lowStock,
                    unsyncedCount = unsyncedCount
                )
            }.combine(
                combine(
                    syncRepository.observeOutboxQueue(businessId, 50),
                    saleDao.observeRecentSalesWithDetails(businessId, 25),
                    saleDao.observeAccounts(businessId),
                    printerService.printerState
                ) { outboxList, recentSales, accounts, printerState ->
                    LedgerSnapshot(
                        outboxList = outboxList,
                        recentSales = recentSales,
                        accounts = accounts,
                        printerState = printerState
                    )
                }
            ) { catalogSnap, ledgerSnap ->
                catalogSnap to ledgerSnap
            }.combine(
                combine(
                    searchQueryFlow,
                    selectedCategoryIdFlow,
                    cartItemsFlow,
                    orderDiscountRpFlow,
                    taxEnabledFlow
                ) { query, catId, cart, orderDisc, taxEnabled ->
                    CartInputSnapshot(
                        query = query,
                        categoryId = catId,
                        cartItems = cart,
                        orderDiscountRp = orderDisc,
                        taxEnabled = taxEnabled
                    )
                }
            ) { (catalogSnap, ledgerSnap), cartInputSnap ->
                Triple(catalogSnap, ledgerSnap, cartInputSnap)
            }.combine(
                combine(
                    customerNameFlow,
                    selectedPaymentMethodFlow,
                    singleCashTenderedRpFlow,
                    splitPaymentModeFlow,
                    paymentSplitsFlow
                ) { customer, method, cashTendered, isSplit, splits ->
                    PaymentInputSnapshot(
                        customerName = customer,
                        selectedMethod = method,
                        singleCashTenderedRp = cashTendered,
                        splitPaymentMode = isSplit,
                        paymentSplits = splits
                    )
                }
            ) { (catalogSnap, ledgerSnap, cartInputSnap), paymentSnap ->
                QuadSnapshot(catalogSnap, ledgerSnap, cartInputSnap, paymentSnap)
            }.combine(
                combine(
                    isSyncingNowFlow,
                    lastSyncEpochMsFlow,
                    syncFeedbackMessageFlow,
                    activeReceiptPreviewFlow,
                    selectedPaperWidthFlow
                ) { syncing, lastSync, syncMsg, receiptPreview, paperWidth ->
                    OverlaySnapshot(
                        isSyncingNow = syncing,
                        lastSyncEpochMs = lastSync,
                        syncFeedbackMessage = syncMsg,
                        activeReceiptPreview = receiptPreview,
                        selectedPaperWidth = paperWidth
                    )
                }
            ) { quad, overlay ->
                val taxRateBps = if (quad.cart.taxEnabled) {
                    quad.catalog.business?.defaultTaxRateBps ?: 1100
                } else {
                    0
                }

                val summary = computeExactLongFinancialSummary(
                    cartItems = quad.cart.cartItems,
                    orderDiscountRp = quad.cart.orderDiscountRp,
                    taxRateBps = taxRateBps,
                    selectedPaymentMethod = quad.payment.selectedMethod,
                    singleCashTenderedRp = quad.payment.singleCashTenderedRp,
                    splitPaymentMode = quad.payment.splitPaymentMode,
                    paymentSplits = quad.payment.paymentSplits
                )

                val conflictCount = quad.ledger.outboxList.count {
                    it.syncStatus == SyncStatus.CONFLICT
                }
                val failedCount = quad.ledger.outboxList.count {
                    it.syncStatus == SyncStatus.FAILED
                }
                val bannerStatus = when {
                    overlay.isSyncingNow -> SyncStatus.SYNCING
                    conflictCount > 0 -> SyncStatus.CONFLICT
                    failedCount > 0 -> SyncStatus.FAILED
                    quad.catalog.unsyncedCount > 0 -> SyncStatus.PENDING
                    else -> SyncStatus.SYNCED
                }

                val formattedReceipt = overlay.activeReceiptPreview?.let { preview ->
                    printerService.formatPlainTextReceipt(preview, overlay.selectedPaperWidth)
                } ?: ""

                PosScreenUiState(
                    business = quad.catalog.business,
                    categories = quad.catalog.categories,
                    selectedCategoryId = quad.cart.categoryId,
                    searchQuery = quad.cart.query,
                    catalogVariants = quad.catalog.catalog,
                    lowStockAlerts = quad.catalog.lowStock,
                    cartItems = quad.cart.cartItems,
                    orderDiscountRp = quad.cart.orderDiscountRp,
                    taxEnabled = quad.cart.taxEnabled,
                    customerName = quad.payment.customerName,
                    selectedPaymentMethod = quad.payment.selectedMethod,
                    singleCashTenderedRp = quad.payment.singleCashTenderedRp,
                    splitPaymentMode = quad.payment.splitPaymentMode,
                    paymentSplits = quad.payment.paymentSplits,
                    financialSummary = summary,
                    unsyncedOutboxCount = quad.catalog.unsyncedCount,
                    conflictOutboxCount = conflictCount,
                    outboxEntries = quad.ledger.outboxList,
                    recentSales = quad.ledger.recentSales,
                    accounts = quad.ledger.accounts,
                    bannerSyncStatus = bannerStatus,
                    isSyncingNow = overlay.isSyncingNow,
                    lastSyncEpochMs = overlay.lastSyncEpochMs,
                    syncFeedbackMessage = overlay.syncFeedbackMessage,
                    activeReceiptPreview = overlay.activeReceiptPreview,
                    activeReceiptPlainText = formattedReceipt,
                    selectedPaperWidth = overlay.selectedPaperWidth,
                    printerState = quad.ledger.printerState,
                    checkoutFeedbackBanner = checkoutFeedbackBannerFlow.value
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        searchQueryFlow.value = query
    }

    fun onSelectCategory(categoryId: String?) {
        selectedCategoryIdFlow.value = categoryId
    }

    fun onScanOrEnterBarcodeOrSku(code: String) {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val match = _uiState.value.catalogVariants.firstOrNull {
                it.barcode.equals(trimmed, ignoreCase = true) ||
                    it.sku.equals(trimmed, ignoreCase = true)
            }
            if (match != null) {
                addCatalogItemToCart(match)
                checkoutFeedbackBannerFlow.value =
                    "Ditambahkan: ${match.productName} (${match.variantName})"
            } else {
                checkoutFeedbackBannerFlow.value =
                    "SKU / Barcode '$trimmed' tidak ditemukan di katalog aktif"
            }
        }
    }

    fun addCatalogItemToCart(item: CatalogVariantItemTuple) {
        cartItemsFlow.update { currentList ->
            val existingIndex = currentList.indexOfFirst { it.variantId == item.variantId }
            val updated = if (existingIndex >= 0) {
                currentList.toMutableList().apply {
                    val existing = this[existingIndex]
                    this[existingIndex] = existing.copy(quantity = existing.quantity + 1L)
                }
            } else {
                currentList + CartItemUiModel(
                    variantId = item.variantId,
                    productId = item.productId,
                    productName = item.productName,
                    variantName = item.variantName,
                    sku = item.sku,
                    unitName = item.unitName,
                    unitPriceRp = item.sellingPriceRp,
                    unitCostRp = item.averageCostRp,
                    isTaxable = item.isTaxable,
                    availableStock = item.localEstimatedStock,
                    quantity = 1L,
                    itemDiscountRp = 0L
                )
            }
            syncDefaultCashTenderWithCart(updated)
            updated
        }
    }

    fun incrementCartItemQuantity(variantId: String) {
        cartItemsFlow.update { list ->
            val updated = list.map {
                if (it.variantId == variantId) it.copy(quantity = it.quantity + 1L) else it
            }
            syncDefaultCashTenderWithCart(updated)
            updated
        }
    }

    fun decrementCartItemQuantity(variantId: String) {
        cartItemsFlow.update { list ->
            val updated = list.mapNotNull { item ->
                if (item.variantId != variantId) {
                    item
                } else if (item.quantity > 1L) {
                    item.copy(quantity = item.quantity - 1L)
                } else {
                    null
                }
            }
            syncDefaultCashTenderWithCart(updated)
            updated
        }
    }

    fun setItemDiscountRp(variantId: String, discountRp: Long) {
        val safeDiscount = discountRp.coerceAtLeast(0L)
        cartItemsFlow.update { list ->
            val updated = list.map { item ->
                if (item.variantId == variantId) {
                    item.copy(itemDiscountRp = safeDiscount.coerceAtMost(item.grossLineRp))
                } else {
                    item
                }
            }
            syncDefaultCashTenderWithCart(updated)
            updated
        }
    }

    fun setOrderDiscountRp(discountRp: Long) {
        orderDiscountRpFlow.value = discountRp.coerceAtLeast(0L)
        syncDefaultCashTenderWithCart(cartItemsFlow.value)
    }

    fun setTaxEnabled(enabled: Boolean) {
        taxEnabledFlow.value = enabled
        syncDefaultCashTenderWithCart(cartItemsFlow.value)
    }

    fun setCustomerName(name: String) {
        customerNameFlow.value = name
    }

    fun clearCart() {
        cartItemsFlow.value = emptyList()
        orderDiscountRpFlow.value = 0L
        singleCashTenderedRpFlow.value = 0L
        paymentSplitsFlow.value = emptyList()
        splitPaymentModeFlow.value = false
    }

    fun selectSinglePaymentMethod(method: PaymentMethod) {
        splitPaymentModeFlow.value = false
        selectedPaymentMethodFlow.value = method
        val grandTotal = _uiState.value.financialSummary.grandTotalRp
        if (singleCashTenderedRpFlow.value < grandTotal) {
            singleCashTenderedRpFlow.value = grandTotal
        }
    }

    fun setSingleCashTenderedRp(amountRp: Long) {
        singleCashTenderedRpFlow.value = amountRp.coerceAtLeast(0L)
    }

    fun applyQuickCashPresetRp(presetRp: Long) {
        splitPaymentModeFlow.value = false
        selectedPaymentMethodFlow.value = PaymentMethod.CASH
        singleCashTenderedRpFlow.value = presetRp.coerceAtLeast(0L)
    }

    fun toggleSplitPaymentMode(enabled: Boolean) {
        splitPaymentModeFlow.value = enabled
        if (enabled && paymentSplitsFlow.value.isEmpty()) {
            val grandTotal = _uiState.value.financialSummary.grandTotalRp
            val halfCash = (grandTotal / 2000L) * 1000L
            val remainderQris = (grandTotal - halfCash).coerceAtLeast(0L)
            val initialSplits = buildList {
                if (halfCash > 0L) {
                    add(
                        PaymentSplitLegUi(
                            method = PaymentMethod.CASH,
                            tenderedRp = halfCash,
                            referenceCode = "TUNAI-1"
                        )
                    )
                }
                if (remainderQris > 0L) {
                    add(
                        PaymentSplitLegUi(
                            method = PaymentMethod.QRIS,
                            tenderedRp = remainderQris,
                            referenceCode = "QRIS-${System.currentTimeMillis().toString().takeLast(6)}"
                        )
                    )
                }
            }
            paymentSplitsFlow.value = initialSplits
        }
    }

    fun addSplitPaymentLeg(method: PaymentMethod, amountRp: Long, referenceCode: String) {
        if (amountRp <= 0L) return
        splitPaymentModeFlow.value = true
        paymentSplitsFlow.update { current ->
            current + PaymentSplitLegUi(
                method = method,
                tenderedRp = amountRp,
                referenceCode = referenceCode.ifBlank {
                    "${method.name}-${System.currentTimeMillis().toString().takeLast(4)}"
                }
            )
        }
    }

    fun removeSplitPaymentLeg(legId: String) {
        paymentSplitsFlow.update { list -> list.filterNot { it.id == legId } }
    }

    /**
     * Executes an atomic offline-first checkout via `SaleDao.completeOfflineSale()`,
     * generates the offline thermal receipt preview, and triggers `OutboxSyncWorker`.
     */
    fun completeCheckout() {
        val currentState = _uiState.value
        val cart = currentState.cartItems
        val summary = currentState.financialSummary
        if (cart.isEmpty() || !summary.isFullyPaid) return

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val saleId = UUID.randomUUID().toString()
            val receiptNumber = generateReceiptNumber(now)

            val saleItems = cart.map { item ->
                // Allocate pro-rata tax per taxable item using integer Long math
                val itemTaxRp = if (item.isTaxable && summary.netTaxableBaseRp > 0L) {
                    (item.netBeforeTaxRp * summary.taxTotalRp) / summary.netTaxableBaseRp
                } else {
                    0L
                }
                SaleItemEntity(
                    id = UUID.randomUUID().toString(),
                    businessId = businessId,
                    saleId = saleId,
                    variantId = item.variantId,
                    productNameSnapshot = item.productName,
                    variantNameSnapshot = item.variantName,
                    skuSnapshot = item.sku,
                    quantity = item.quantity,
                    unitPriceRp = item.unitPriceRp,
                    unitCostRp = item.unitCostRp,
                    discountRp = item.clampedDiscountRp,
                    taxRp = itemTaxRp,
                    lineTotalRp = item.netBeforeTaxRp + itemTaxRp,
                    syncStatus = SyncStatus.PENDING,
                    createdAt = now
                )
            }

            val paymentEntities: List<PaymentEntity> = if (currentState.splitPaymentMode && currentState.paymentSplits.isNotEmpty()) {
                var remainingToCover = summary.grandTotalRp
                currentState.paymentSplits.mapIndexed { index, leg ->
                    val isLastLeg = index == currentState.paymentSplits.lastIndex
                    val appliedAmountRp = if (isLastLeg) {
                        leg.tenderedRp.coerceAtMost(remainingToCover.coerceAtLeast(0L))
                    } else {
                        leg.tenderedRp.coerceAtMost(remainingToCover.coerceAtLeast(0L))
                    }
                    val legChangeRp = (leg.tenderedRp - appliedAmountRp).coerceAtLeast(0L)
                    remainingToCover = (remainingToCover - appliedAmountRp).coerceAtLeast(0L)

                    PaymentEntity(
                        id = UUID.randomUUID().toString(),
                        businessId = businessId,
                        saleId = saleId,
                        paymentMethod = leg.method,
                        amountRp = appliedAmountRp,
                        tenderedRp = leg.tenderedRp,
                        changeRp = legChangeRp,
                        referenceCode = leg.referenceCode.ifBlank { null },
                        paidAt = now,
                        syncStatus = SyncStatus.PENDING,
                        createdAt = now
                    )
                }
            } else {
                val method = currentState.selectedPaymentMethod
                val tendered = summary.totalPaidRp
                val change = summary.changeDueRp
                listOf(
                    PaymentEntity(
                        id = UUID.randomUUID().toString(),
                        businessId = businessId,
                        saleId = saleId,
                        paymentMethod = method,
                        amountRp = summary.grandTotalRp,
                        tenderedRp = tendered,
                        changeRp = change,
                        referenceCode = if (method == PaymentMethod.CASH) null else "POS-${receiptNumber.takeLast(6)}",
                        paidAt = now,
                        syncStatus = SyncStatus.PENDING,
                        createdAt = now
                    )
                )
            }

            val headerPaymentMethod = if (currentState.splitPaymentMode && paymentEntities.size > 1) {
                PaymentMethod.SPLIT
            } else {
                paymentEntities.first().paymentMethod
            }

            val saleEntity = SaleEntity(
                id = saleId,
                businessId = businessId,
                receiptNumber = receiptNumber,
                cashierUserId = cashierUserId,
                customerName = currentState.customerName.trim().ifBlank { null },
                subtotalRp = summary.subtotalRp,
                discountTotalRp = summary.discountTotalRp,
                taxTotalRp = summary.taxTotalRp,
                grandTotalRp = summary.grandTotalRp,
                totalPaidRp = summary.totalPaidRp,
                changeDueRp = summary.changeDueRp,
                totalCogsRp = summary.totalCogsRp,
                paymentMethod = headerPaymentMethod,
                isVoided = false,
                voidReason = null,
                voidedAt = null,
                notes = null,
                syncStatus = SyncStatus.PENDING,
                version = 1L,
                createdAt = now,
                updatedAt = now
            )

            val outboxPayload = SaleOutboxPayloadDto(
                saleId = saleEntity.id,
                businessId = businessId,
                receiptNumber = receiptNumber,
                cashierUserId = cashierUserId,
                customerName = saleEntity.customerName,
                subtotalRp = saleEntity.subtotalRp,
                discountTotalRp = saleEntity.discountTotalRp,
                taxTotalRp = saleEntity.taxTotalRp,
                grandTotalRp = saleEntity.grandTotalRp,
                totalPaidRp = saleEntity.totalPaidRp,
                changeDueRp = saleEntity.changeDueRp,
                totalCogsRp = saleEntity.totalCogsRp,
                paymentMethod = saleEntity.paymentMethod,
                createdAtMs = now,
                items = saleItems.map {
                    SaleItemPayloadDto(
                        id = it.id,
                        variantId = it.variantId,
                        sku = it.skuSnapshot,
                        quantity = it.quantity,
                        unitPriceRp = it.unitPriceRp,
                        unitCostRp = it.unitCostRp,
                        discountRp = it.discountRp,
                        taxRp = it.taxRp,
                        lineTotalRp = it.lineTotalRp
                    )
                },
                payments = paymentEntities.map {
                    PaymentPayloadDto(
                        id = it.id,
                        paymentMethod = it.paymentMethod,
                        amountRp = it.amountRp,
                        tenderedRp = it.tenderedRp,
                        changeRp = it.changeRp,
                        referenceCode = it.referenceCode,
                        paidAtMs = it.paidAt
                    )
                }
            )

            val outboxPayloadJson = json.encodeToString(outboxPayload)

            // Execute Atomic Room @Transaction (Sale + Stock Deduction + Indonesian COA Journal + Outbox)
            saleDao.completeOfflineSale(
                sale = saleEntity,
                items = saleItems,
                payments = paymentEntities,
                outboxPayloadJson = outboxPayloadJson,
                actorRole = UserRole.CASHIER
            )

            // Build Receipt Preview Model for Immediate Offline Display & Thermal Printing
            val business = currentState.business
            val receiptPreview = ReceiptPreviewModel(
                saleId = saleEntity.id,
                receiptNumber = saleEntity.receiptNumber,
                businessName = business?.name ?: "NEXBIZ Nusantara Kopi & Mart",
                businessAddress = business?.address ?: "Jl. Senopati No. 88, Jakarta Selatan",
                businessPhone = business?.phone ?: "+628119008800",
                taxIdNpwp = business?.taxIdNpwp,
                cashierName = "Siti Rahma (Kasir Utama)",
                customerName = saleEntity.customerName,
                createdAtEpochMs = now,
                items = saleItems.map {
                    ReceiptLineItemModel(
                        name = it.productNameSnapshot,
                        variantName = it.variantNameSnapshot,
                        sku = it.skuSnapshot,
                        quantity = it.quantity,
                        unitPriceRp = it.unitPriceRp,
                        discountRp = it.discountRp,
                        lineTotalRp = it.lineTotalRp
                    )
                },
                subtotalRp = saleEntity.subtotalRp,
                discountTotalRp = saleEntity.discountTotalRp,
                taxTotalRp = saleEntity.taxTotalRp,
                grandTotalRp = saleEntity.grandTotalRp,
                totalPaidRp = saleEntity.totalPaidRp,
                changeDueRp = saleEntity.changeDueRp,
                payments = paymentEntities.map {
                    ReceiptPaymentSplitModel(
                        method = it.paymentMethod,
                        amountRp = it.amountRp,
                        tenderedRp = it.tenderedRp,
                        changeRp = it.changeRp,
                        referenceCode = it.referenceCode
                    )
                },
                syncStatus = SyncStatus.PENDING,
                idempotencyKey = "sale-create-${saleEntity.id}"
            )

            activeReceiptPreviewFlow.value = receiptPreview
            clearCart()
            customerNameFlow.value = ""
            checkoutFeedbackBannerFlow.value =
                "Transaksi #$receiptNumber tersimpan di Room WAL & Outbox!"

            // Enqueue WorkManager Outbox Sync
            SyncScheduler.triggerImmediateOutboxSync(appContext, businessId)
        }
    }

    fun triggerManualOutboxSync() {
        if (isSyncingNowFlow.value) return
        viewModelScope.launch {
            isSyncingNowFlow.value = true
            syncFeedbackMessageFlow.value = "Menjalankan Outbox Push & Delta Pull..."
            val report = syncRepository.executeFullSyncCycle(businessId)
            isSyncingNowFlow.value = false
            lastSyncEpochMsFlow.value = report.completedAtEpochMs
            syncFeedbackMessageFlow.value = when (report.outcome) {
                SyncExecutionOutcome.IDLE ->
                    "Outbox bersih • Semua transaksi telah SYNCED"
                SyncExecutionOutcome.SUCCESS ->
                    "Synced ${report.acknowledgedCount} mutasi • ${report.reconciledStockCount} stok direkonsiliasi"
                SyncExecutionOutcome.PERMANENT_CONFLICT ->
                    "${report.conflictCount} konflik stok multi-perangkat direbase ke stok server"
                SyncExecutionOutcome.RETRYABLE_ERROR ->
                    "Offline/Backoff: ${report.errorMessage ?: "Dijadwalkan ulang oleh WorkManager"}"
            }
        }
    }

    /**
     * Triggers a real multi-device stock conflict resolution exercise against the local
     * Room database and `SyncRepository.resolveMultiDeviceStockConflict()` so the architect/user
     * can inspect the exact conflict rebase & audit log behavior on-device.
     */
    fun triggerStockOpnameAdjustmentAndReconcile(
        variant: CatalogVariantItemTuple,
        quantityDelta: Long,
        simulateConcurrentDeviceConflict: Boolean
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val outboxId = UUID.randomUUID().toString()
            val movementId = UUID.randomUUID().toString()
            val payload = StockAdjustmentPayloadDto(
                movementId = movementId,
                businessId = businessId,
                variantId = variant.variantId,
                movementType = MovementType.STOCK_OPNAME_ADJUSTMENT.name,
                quantityDelta = quantityDelta,
                stockBefore = variant.localEstimatedStock,
                stockAfter = variant.localEstimatedStock + quantityDelta,
                unitCostRp = variant.averageCostRp,
                baseVersion = 1L,
                createdAtMs = now
            )
            val payloadJson = json.encodeToString(payload)
            val outboxEntry = SyncOutboxEntity(
                id = outboxId,
                businessId = businessId,
                idempotencyKey = "stock-opname-$movementId",
                operation = OutboxOperation.RECORD_INVENTORY_MOVEMENT,
                entityType = "InventoryMovementEntity",
                entityId = movementId,
                payloadJson = payloadJson,
                syncStatus = SyncStatus.PENDING,
                retryCount = 0,
                maxRetries = 5,
                nextAttemptAt = now,
                lastErrorCode = null,
                lastErrorMessage = null,
                createdAt = now,
                updatedAt = now
            )

            val movement = inventoryDao.adjustLocalStock(
                businessId = businessId,
                variantId = variant.variantId,
                quantityDelta = quantityDelta,
                movementType = MovementType.STOCK_OPNAME_ADJUSTMENT,
                unitCostRp = variant.averageCostRp,
                userId = cashierUserId,
                referenceId = outboxId,
                referenceType = "STOCK_OPNAME",
                notes = "Stock Opname Kasir (${if (quantityDelta >= 0) "+$quantityDelta" else "$quantityDelta"})",
                nowEpochMs = now,
                outboxEntry = outboxEntry.copy(entityId = movementId)
            )

            if (simulateConcurrentDeviceConflict) {
                val authoritativeServerStock = (variant.serverConfirmedStock - 2L).coerceAtLeast(0L)
                val conflictDto = OutboxConflictDto(
                    outboxId = outboxId,
                    idempotencyKey = outboxEntry.idempotencyKey,
                    entityType = "InventoryMovementEntity",
                    entityId = movement.id,
                    variantId = variant.variantId,
                    conflictCode = "ERR_409_STALE_STOCK_VERSION",
                    conflictMessage = "Terminal Kasir-02 menjual 2 ${variant.unitName} saat opname berlangsung",
                    serverVersion = 3L,
                    serverConfirmedStock = authoritativeServerStock,
                    serverUnitCostRp = variant.averageCostRp,
                    resolutionStrategy = "SERVER_AUTHORITATIVE_STOCK_REBASE"
                )
                syncRepository.resolveMultiDeviceStockConflict(
                    businessId = businessId,
                    outboxEntry = outboxEntry.copy(entityId = movement.id),
                    conflict = conflictDto,
                    nowEpochMs = now + 50L
                )
                syncFeedbackMessageFlow.value =
                    "Konflik Stok ${variant.sku} direbase ke Server Stock ($authoritativeServerStock ${variant.unitName})"
            } else {
                SyncScheduler.triggerImmediateOutboxSync(appContext, businessId)
                syncFeedbackMessageFlow.value =
                    "Penyesuaian stok ${variant.sku} masuk antrean Outbox"
            }
        }
    }

    fun selectPaperWidth(paperWidth: ThermalPaperWidth) {
        selectedPaperWidthFlow.value = paperWidth
    }

    fun printActiveReceipt() {
        val receipt = activeReceiptPreviewFlow.value ?: return
        viewModelScope.launch {
            printerService.printReceipt(receipt, selectedPaperWidthFlow.value)
        }
    }

    fun openReceiptForHistoricalSale(saleWithDetails: SaleWithItemsAndPayments) {
        val sale = saleWithDetails.sale
        val business = _uiState.value.business
        activeReceiptPreviewFlow.value = ReceiptPreviewModel(
            saleId = sale.id,
            receiptNumber = sale.receiptNumber,
            businessName = business?.name ?: "NEXBIZ Nusantara Kopi & Mart",
            businessAddress = business?.address ?: "Jl. Senopati No. 88, Jakarta Selatan",
            businessPhone = business?.phone ?: "+628119008800",
            taxIdNpwp = business?.taxIdNpwp,
            cashierName = "Siti Rahma (Kasir Utama)",
            customerName = sale.customerName,
            createdAtEpochMs = sale.createdAt,
            items = saleWithDetails.items.map {
                ReceiptLineItemModel(
                    name = it.productNameSnapshot,
                    variantName = it.variantNameSnapshot,
                    sku = it.skuSnapshot,
                    quantity = it.quantity,
                    unitPriceRp = it.unitPriceRp,
                    discountRp = it.discountRp,
                    lineTotalRp = it.lineTotalRp
                )
            },
            subtotalRp = sale.subtotalRp,
            discountTotalRp = sale.discountTotalRp,
            taxTotalRp = sale.taxTotalRp,
            grandTotalRp = sale.grandTotalRp,
            totalPaidRp = sale.totalPaidRp,
            changeDueRp = sale.changeDueRp,
            payments = saleWithDetails.payments.map {
                ReceiptPaymentSplitModel(
                    method = it.paymentMethod,
                    amountRp = it.amountRp,
                    tenderedRp = it.tenderedRp,
                    changeRp = it.changeRp,
                    referenceCode = it.referenceCode
                )
            },
            syncStatus = sale.syncStatus,
            idempotencyKey = "sale-create-${sale.id}"
        )
    }

    fun dismissReceiptPreview() {
        activeReceiptPreviewFlow.value = null
    }

    fun dismissFeedbackBanner() {
        checkoutFeedbackBannerFlow.value = null
    }

    private fun syncDefaultCashTenderWithCart(cartItems: List<CartItemUiModel>) {
        if (cartItems.isEmpty()) {
            singleCashTenderedRpFlow.value = 0L
            return
        }
        val taxRateBps = if (taxEnabledFlow.value) {
            _uiState.value.business?.defaultTaxRateBps ?: 1100
        } else {
            0
        }
        val summary = computeExactLongFinancialSummary(
            cartItems = cartItems,
            orderDiscountRp = orderDiscountRpFlow.value,
            taxRateBps = taxRateBps,
            selectedPaymentMethod = selectedPaymentMethodFlow.value,
            singleCashTenderedRp = singleCashTenderedRpFlow.value,
            splitPaymentMode = splitPaymentModeFlow.value,
            paymentSplits = paymentSplitsFlow.value
        )
        if (!splitPaymentModeFlow.value && singleCashTenderedRpFlow.value < summary.grandTotalRp) {
            singleCashTenderedRpFlow.value = summary.grandTotalRp
        }
    }

    private fun generateReceiptNumber(epochMs: Long): String {
        val datePart = SimpleDateFormat("yyMMdd", Locale.US).format(Date(epochMs))
        val suffix = (epochMs % 100_000L).toString().padStart(5, '0')
        return "INV-$datePart-$suffix"
    }

    companion object {
        /**
         * Pure Long-only financial calculation engine (Zero Double/Float usage).
         * Tax is calculated in integer basis points (`1100` bps = 11%) with half-up integer rounding:
         * `taxRp = (netTaxableBaseRp * taxRateBps + 5_000L) / 10_000L`
         */
        fun computeExactLongFinancialSummary(
            cartItems: List<CartItemUiModel>,
            orderDiscountRp: Long,
            taxRateBps: Int,
            selectedPaymentMethod: PaymentMethod,
            singleCashTenderedRp: Long,
            splitPaymentMode: Boolean,
            paymentSplits: List<PaymentSplitLegUi>
        ): CartFinancialSummary {
            if (cartItems.isEmpty()) {
                return CartFinancialSummary(taxRateBps = taxRateBps)
            }

            val totalQty = cartItems.sumOf { it.quantity }
            val subtotalRp = cartItems.sumOf { it.grossLineRp }
            val itemDiscountTotalRp = cartItems.sumOf { it.clampedDiscountRp }
            val netAfterItemDiscountRp = (subtotalRp - itemDiscountTotalRp).coerceAtLeast(0L)
            val safeOrderDiscountRp = orderDiscountRp.coerceIn(0L, netAfterItemDiscountRp)
            val discountTotalRp = itemDiscountTotalRp + safeOrderDiscountRp
            val netAfterAllDiscountsRp = (subtotalRp - discountTotalRp).coerceAtLeast(0L)

            val taxableGrossAfterItemDiscRp = cartItems
                .filter { it.isTaxable }
                .sumOf { it.netBeforeTaxRp }

            // Distribute order-level discount proportionally across taxable items using Long math
            val orderDiscountOnTaxableRp = if (netAfterItemDiscountRp > 0L) {
                (safeOrderDiscountRp * taxableGrossAfterItemDiscRp) / netAfterItemDiscountRp
            } else {
                0L
            }

            val netTaxableBaseRp = (taxableGrossAfterItemDiscRp - orderDiscountOnTaxableRp).coerceAtLeast(0L)
            val taxTotalRp = if (taxRateBps > 0 && netTaxableBaseRp > 0L) {
                (netTaxableBaseRp * taxRateBps.toLong() + 5_000L) / 10_000L
            } else {
                0L
            }

            val grandTotalRp = netAfterAllDiscountsRp + taxTotalRp
            val totalCogsRp = cartItems.sumOf { it.totalCogsRp }
            val estimatedGrossProfitRp = netAfterAllDiscountsRp - totalCogsRp

            val totalPaidRp: Long = if (splitPaymentMode) {
                paymentSplits.sumOf { it.tenderedRp }
            } else {
                when (selectedPaymentMethod) {
                    PaymentMethod.CASH -> singleCashTenderedRp.coerceAtLeast(0L)
                    else -> grandTotalRp
                }
            }

            val remainingDueRp = (grandTotalRp - totalPaidRp).coerceAtLeast(0L)
            val changeDueRp = (totalPaidRp - grandTotalRp).coerceAtLeast(0L)
            val isFullyPaid = grandTotalRp > 0L && totalPaidRp >= grandTotalRp

            return CartFinancialSummary(
                totalQuantity = totalQty,
                subtotalRp = subtotalRp,
                itemDiscountTotalRp = itemDiscountTotalRp,
                orderDiscountRp = safeOrderDiscountRp,
                discountTotalRp = discountTotalRp,
                netTaxableBaseRp = netTaxableBaseRp,
                taxRateBps = taxRateBps,
                taxTotalRp = taxTotalRp,
                grandTotalRp = grandTotalRp,
                totalCogsRp = totalCogsRp,
                estimatedGrossProfitRp = estimatedGrossProfitRp,
                totalPaidRp = totalPaidRp,
                remainingDueRp = remainingDueRp,
                changeDueRp = changeDueRp,
                isFullyPaid = isFullyPaid
            )
        }

        fun provideFactory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val appContext = context.applicationContext
                    val database = DatabaseModule.provideDatabase(appContext)
                    val syncRepo = NetworkModule.provideSyncRepository(database)
                    val printer = OfflineEscPosPrinterManager()
                    return PosViewModel(
                        appContext = appContext,
                        productDao = database.productDao(),
                        saleDao = database.saleDao(),
                        inventoryDao = database.inventoryDao(),
                        syncRepository = syncRepo,
                        printerService = printer
                    ) as T
                }
            }
    }
}

private data class CatalogSyncSnapshot(
    val business: BusinessEntity?,
    val categories: List<CategoryEntity>,
    val catalog: List<CatalogVariantItemTuple>,
    val lowStock: List<LowStockVariantAlertTuple>,
    val unsyncedCount: Int
)

private data class LedgerSnapshot(
    val outboxList: List<SyncOutboxEntity>,
    val recentSales: List<SaleWithItemsAndPayments>,
    val accounts: List<AccountEntity>,
    val printerState: PrinterConnectionState
)

private data class CartInputSnapshot(
    val query: String,
    val categoryId: String?,
    val cartItems: List<CartItemUiModel>,
    val orderDiscountRp: Long,
    val taxEnabled: Boolean
)

private data class PaymentInputSnapshot(
    val customerName: String,
    val selectedMethod: PaymentMethod,
    val singleCashTenderedRp: Long,
    val splitPaymentMode: Boolean,
    val paymentSplits: List<PaymentSplitLegUi>
)

private data class QuadSnapshot(
    val catalog: CatalogSyncSnapshot,
    val ledger: LedgerSnapshot,
    val cart: CartInputSnapshot,
    val payment: PaymentInputSnapshot
)

private data class OverlaySnapshot(
    val isSyncingNow: Boolean,
    val lastSyncEpochMs: Long?,
    val syncFeedbackMessage: String?,
    val activeReceiptPreview: ReceiptPreviewModel?,
    val selectedPaperWidth: ThermalPaperWidth
)
