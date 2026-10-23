package id.nexbiz.pos.ui.screens.pos

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCartCheckout
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.nexbiz.pos.core.printer.OfflineEscPosPrinterManager
import id.nexbiz.pos.data.local.dao.CatalogVariantItemTuple
import id.nexbiz.pos.data.local.model.PaymentMethod
import id.nexbiz.pos.ui.components.SyncStatusBanner
import id.nexbiz.pos.ui.theme.DeepNavy800
import id.nexbiz.pos.ui.theme.DeepNavy900
import id.nexbiz.pos.ui.theme.ElectricCyan400
import id.nexbiz.pos.ui.theme.ElectricCyan500
import id.nexbiz.pos.ui.theme.SyncAmber500
import id.nexbiz.pos.ui.theme.SyncCrimson500
import id.nexbiz.pos.ui.theme.SyncEmerald500

@Composable
fun PosScreen(
    viewModel: PosViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var isCheckoutSheetExpanded by rememberSaveable { mutableStateOf(false) }
    var showOutboxInspector by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = isCheckoutSheetExpanded) {
        isCheckoutSheetExpanded = false
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("nexbiz_pos_screen"),
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            val isWideTablet = maxWidth >= 720.dp

            if (isWideTablet) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1.35f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PosTopHeaderAndSyncBar(
                            uiState = uiState,
                            onTriggerSync = viewModel::triggerManualOutboxSync,
                            onInspectOutbox = { showOutboxInspector = true }
                        )
                        PosSearchAndCategoryBar(
                            uiState = uiState,
                            onSearchChange = viewModel::onSearchQueryChange,
                            onSelectCategory = viewModel::onSelectCategory,
                            onBarcodeSubmit = viewModel::onScanOrEnterBarcodeOrSku
                        )
                        CatalogGridSection(
                            variants = uiState.catalogVariants,
                            cartQuantities = uiState.cartItems.associate { it.variantId to it.quantity },
                            onAddVariant = viewModel::addCatalogItemToCart,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        shape = RoundedCornerShape(20.dp),
                        tonalElevation = 4.dp,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        OneHandedCheckoutDrawerContent(
                            uiState = uiState,
                            isExpanded = true,
                            onToggleExpand = {},
                            onIncrementQty = viewModel::incrementCartItemQuantity,
                            onDecrementQty = viewModel::decrementCartItemQuantity,
                            onClearCart = viewModel::clearCart,
                            onOrderDiscountChange = viewModel::setOrderDiscountRp,
                            onTaxToggle = viewModel::setTaxEnabled,
                            onSelectSingleMethod = viewModel::selectSinglePaymentMethod,
                            onSingleCashTenderedChange = viewModel::setSingleCashTenderedRp,
                            onQuickCashPreset = viewModel::applyQuickCashPresetRp,
                            onToggleSplitMode = viewModel::toggleSplitPaymentMode,
                            onAddSplitLeg = viewModel::addSplitPaymentLeg,
                            onRemoveSplitLeg = viewModel::removeSplitPaymentLeg,
                            onCompleteCheckout = viewModel::completeCheckout
                        )
                    }
                }
            } else {
                // Handheld One-Handed Ergonomic Layout: Top Sync + Catalog + Bottom Thumb Dock
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PosTopHeaderAndSyncBar(
                            uiState = uiState,
                            onTriggerSync = viewModel::triggerManualOutboxSync,
                            onInspectOutbox = { showOutboxInspector = true }
                        )

                        PosSearchAndCategoryBar(
                            uiState = uiState,
                            onSearchChange = viewModel::onSearchQueryChange,
                            onSelectCategory = viewModel::onSelectCategory,
                            onBarcodeSubmit = viewModel::onScanOrEnterBarcodeOrSku
                        )

                        CatalogGridSection(
                            variants = uiState.catalogVariants,
                            cartQuantities = uiState.cartItems.associate { it.variantId to it.quantity },
                            onAddVariant = viewModel::addCatalogItemToCart,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Bottom One-Handed Thumb-Zone Checkout Dock
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .testTag("one_handed_checkout_dock"),
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        color = DeepNavy900,
                        tonalElevation = 10.dp,
                        shadowElevation = 12.dp
                    ) {
                        OneHandedCheckoutDrawerContent(
                            uiState = uiState,
                            isExpanded = isCheckoutSheetExpanded,
                            onToggleExpand = { isCheckoutSheetExpanded = !isCheckoutSheetExpanded },
                            onIncrementQty = viewModel::incrementCartItemQuantity,
                            onDecrementQty = viewModel::decrementCartItemQuantity,
                            onClearCart = viewModel::clearCart,
                            onOrderDiscountChange = viewModel::setOrderDiscountRp,
                            onTaxToggle = viewModel::setTaxEnabled,
                            onSelectSingleMethod = viewModel::selectSinglePaymentMethod,
                            onSingleCashTenderedChange = viewModel::setSingleCashTenderedRp,
                            onQuickCashPreset = viewModel::applyQuickCashPresetRp,
                            onToggleSplitMode = viewModel::toggleSplitPaymentMode,
                            onAddSplitLeg = viewModel::addSplitPaymentLeg,
                            onRemoveSplitLeg = viewModel::removeSplitPaymentLeg,
                            onCompleteCheckout = {
                                viewModel.completeCheckout()
                                isCheckoutSheetExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }

    val activeReceipt = uiState.activeReceiptPreview
    if (activeReceipt != null) {
        OfflineReceiptPreviewDialog(
            receipt = activeReceipt,
            plainTextReceipt = uiState.activeReceiptPlainText,
            selectedPaperWidth = uiState.selectedPaperWidth,
            printerState = uiState.printerState,
            onSelectPaperWidth = viewModel::selectPaperWidth,
            onPrintReceipt = viewModel::printActiveReceipt,
            onDismiss = viewModel::dismissReceiptPreview
        )
    }

    if (showOutboxInspector) {
        OutboxAndLedgerInspectorDialog(
            outboxEntries = uiState.outboxEntries,
            recentSales = uiState.recentSales,
            accounts = uiState.accounts,
            catalogVariants = uiState.catalogVariants,
            onTriggerSyncNow = viewModel::triggerManualOutboxSync,
            onSimulateMultiDeviceConflict = { variant ->
                viewModel.triggerStockOpnameAdjustmentAndReconcile(
                    variant = variant,
                    quantityDelta = -1L,
                    simulateConcurrentDeviceConflict = true
                )
            },
            onOpenSaleReceipt = { saleBundle ->
                showOutboxInspector = false
                viewModel.openReceiptForHistoricalSale(saleBundle)
            },
            onDismiss = { showOutboxInspector = false }
        )
    }
}

@Composable
private fun PosTopHeaderAndSyncBar(
    uiState: PosScreenUiState,
    onTriggerSync: () -> Unit,
    onInspectOutbox: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DeepNavy900,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PointOfSale,
                            contentDescription = "NEXBIZ POS",
                            tint = ElectricCyan400
                        )
                    }
                }
                Column {
                    Text(
                        text = uiState.business?.name ?: "NEXBIZ Nusantara Kopi & Mart",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Tenant: ${uiState.business?.code ?: "NXB-JKT-01"} • Kasir: Siti Rahma",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedButton(
                onClick = onInspectOutbox,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("open_outbox_inspector_button")
            ) {
                Icon(
                    imageVector = Icons.Default.ReceiptLong,
                    contentDescription = "Outbox & COA",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Outbox & COA")
            }
        }

        SyncStatusBanner(
            syncStatus = uiState.bannerSyncStatus,
            pendingOutboxCount = uiState.unsyncedOutboxCount,
            conflictCount = uiState.conflictOutboxCount,
            isSyncingNow = uiState.isSyncingNow,
            lastSyncEpochMs = uiState.lastSyncEpochMs,
            statusMessage = uiState.syncFeedbackMessage ?: uiState.checkoutFeedbackBanner,
            onTriggerSync = onTriggerSync,
            onInspectOutbox = onInspectOutbox
        )
    }
}

@Composable
private fun PosSearchAndCategoryBar(
    uiState: PosScreenUiState,
    onSearchChange: (String) -> Unit,
    onSelectCategory: (String?) -> Unit,
    onBarcodeSubmit: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Cari nama produk, SKU, atau scan barcode...") },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Search, contentDescription = "Cari")
            },
            trailingIcon = {
                IconButton(
                    onClick = {
                        if (uiState.searchQuery.isNotBlank()) {
                            onBarcodeSubmit(uiState.searchQuery)
                        } else {
                            onBarcodeSubmit("8991002000011")
                        }
                    },
                    modifier = Modifier.testTag("quick_scan_barcode_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Scan SKU Cepat",
                        tint = ElectricCyan500
                    )
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = { onBarcodeSubmit(uiState.searchQuery) }
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("pos_search_input")
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = uiState.selectedCategoryId == null,
                onClick = { onSelectCategory(null) },
                label = { Text("Semua Kategori") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = DeepNavy900,
                    selectedLabelColor = ElectricCyan400
                ),
                modifier = Modifier.testTag("category_chip_all")
            )
            uiState.categories.forEach { category ->
                FilterChip(
                    selected = uiState.selectedCategoryId == category.id,
                    onClick = {
                        onSelectCategory(
                            if (uiState.selectedCategoryId == category.id) null else category.id
                        )
                    },
                    label = { Text(category.name) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DeepNavy900,
                        selectedLabelColor = ElectricCyan400
                    ),
                    modifier = Modifier.testTag("category_chip_${category.id}")
                )
            }
        }
    }
}

@Composable
private fun CatalogGridSection(
    variants: List<CatalogVariantItemTuple>,
    cartQuantities: Map<String, Long>,
    onAddVariant: (CatalogVariantItemTuple) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 156.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("pos_catalog_grid"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 8.dp)
    ) {
        items(variants, key = { it.variantId }) { variant ->
            val qtyInCart = cartQuantities[variant.variantId] ?: 0L
            val isLowStock = variant.localEstimatedStock <= variant.minStockThreshold

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAddVariant(variant) }
                    .then(
                        if (qtyInCart > 0L) {
                            Modifier.border(
                                width = 2.dp,
                                color = ElectricCyan400,
                                shape = RoundedCornerShape(16.dp)
                            )
                        } else {
                            Modifier
                        }
                    )
                    .testTag("catalog_card_${variant.sku}"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = variant.sku,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (qtyInCart > 0L) {
                            Surface(
                                shape = CircleShape,
                                color = ElectricCyan400
                            ) {
                                Text(
                                    text = "${qtyInCart}x",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DeepNavy900,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = variant.productName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = variant.variantName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = OfflineEscPosPrinterManager.formatRp(variant.sellingPriceRp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = ElectricCyan500
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isLowStock) {
                                SyncCrimson500.copy(alpha = 0.15f)
                            } else {
                                SyncEmerald500.copy(alpha = 0.15f)
                            }
                        ) {
                            Text(
                                text = "Stok: ${variant.localEstimatedStock}/${variant.serverConfirmedStock}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isLowStock) SyncCrimson500 else SyncEmerald500,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OneHandedCheckoutDrawerContent(
    uiState: PosScreenUiState,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onIncrementQty: (String) -> Unit,
    onDecrementQty: (String) -> Unit,
    onClearCart: () -> Unit,
    onOrderDiscountChange: (Long) -> Unit,
    onTaxToggle: (Boolean) -> Unit,
    onSelectSingleMethod: (PaymentMethod) -> Unit,
    onSingleCashTenderedChange: (Long) -> Unit,
    onQuickCashPreset: (Long) -> Unit,
    onToggleSplitMode: (Boolean) -> Unit,
    onAddSplitLeg: (PaymentMethod, Long, String) -> Unit,
    onRemoveSplitLeg: (String) -> Unit,
    onCompleteCheckout: () -> Unit
) {
    val summary = uiState.financialSummary
    var customDiscountInput by remember { mutableStateOf("") }
    var customCashTenderInput by remember { mutableStateOf("") }
    var splitAmountInput by remember { mutableStateOf("") }
    var splitMethodSelection by remember { mutableStateOf(PaymentMethod.QRIS) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Compact One-Handed Header Bar (Always visible in thumb zone)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand)
                .testTag("toggle_checkout_sheet_header"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = ElectricCyan400,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "${summary.totalQuantity}",
                            style = MaterialTheme.typography.labelLarge,
                            color = DeepNavy900,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                Column {
                    Text(
                        text = OfflineEscPosPrinterManager.formatRp(summary.grandTotalRp),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = if (summary.changeDueRp > 0L) {
                            "Kembalian: ${OfflineEscPosPrinterManager.formatRp(summary.changeDueRp)}"
                        } else if (summary.remainingDueRp > 0L) {
                            "Kurang Bayar: ${OfflineEscPosPrinterManager.formatRp(summary.remainingDueRp)}"
                        } else {
                            "PPN 11%: ${OfflineEscPosPrinterManager.formatRp(summary.taxTotalRp)} • Uang Pas"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = ElectricCyan400
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onCompleteCheckout,
                    enabled = summary.isFullyPaid && uiState.cartItems.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan400,
                        contentColor = DeepNavy900,
                        disabledContainerColor = DeepNavy800,
                        disabledContentColor = Color(0xFF64748B)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("quick_checkout_pay_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCartCheckout,
                        contentDescription = "Bayar"
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "BAYAR",
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.testTag("expand_cart_sheet_button")
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                        contentDescription = "Buka Detail Keranjang & Split Bill",
                        tint = Color.White
                    )
                }
            }
        }

        // Quick One-Handed Cash & QRIS Thumb Bar (Visible even when collapsed if cart is non-empty)
        if (uiState.cartItems.isNotEmpty() && !isExpanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !uiState.splitPaymentMode && uiState.selectedPaymentMethod == PaymentMethod.CASH &&
                        summary.totalPaidRp == summary.grandTotalRp,
                    onClick = { onQuickCashPreset(summary.grandTotalRp) },
                    label = { Text("Uang Pas") },
                    modifier = Modifier.testTag("quick_tender_exact")
                )
                listOf(50_000L, 100_000L, 200_000L).forEach { denomRp ->
                    FilterChip(
                        selected = !uiState.splitPaymentMode && uiState.selectedPaymentMethod == PaymentMethod.CASH &&
                            uiState.singleCashTenderedRp == denomRp,
                        onClick = { onQuickCashPreset(denomRp) },
                        label = { Text(OfflineEscPosPrinterManager.formatRp(denomRp)) },
                        modifier = Modifier.testTag("quick_tender_$denomRp")
                    )
                }
                FilterChip(
                    selected = !uiState.splitPaymentMode && uiState.selectedPaymentMethod == PaymentMethod.QRIS,
                    onClick = { onSelectSingleMethod(PaymentMethod.QRIS) },
                    label = { Text("QRIS") },
                    modifier = Modifier.testTag("quick_method_qris")
                )
                FilterChip(
                    selected = uiState.splitPaymentMode,
                    onClick = {
                        onToggleSplitMode(true)
                        onToggleExpand()
                    },
                    label = { Text("Split Bayar") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.CallSplit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("quick_split_payment_chip")
                )
            }
        }

        // Expanded Cart, Tax/Discount, and Split Payment Sheet
        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HorizontalDivider(color = Color(0xFF334155))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Item Keranjang (${uiState.cartItems.size} SKU)",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    if (uiState.cartItems.isNotEmpty()) {
                        OutlinedButton(
                            onClick = onClearCart,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("clear_cart_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Kosongkan Keranjang",
                                tint = SyncCrimson500,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset", color = SyncCrimson500)
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(uiState.cartItems, key = { it.variantId }) { item ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = DeepNavy800,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${item.productName} (${item.variantName})",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${OfflineEscPosPrinterManager.formatRp(item.unitPriceRp)} x ${item.quantity} = ${OfflineEscPosPrinterManager.formatRp(item.netBeforeTaxRp)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ElectricCyan400
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    IconButton(
                                        onClick = { onDecrementQty(item.variantId) },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .testTag("dec_qty_${item.sku}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Remove,
                                            contentDescription = "Kurangi",
                                            tint = Color.White
                                        )
                                    }
                                    Text(
                                        text = "${item.quantity}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    IconButton(
                                        onClick = { onIncrementQty(item.variantId) },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .testTag("inc_qty_${item.sku}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Tambah",
                                            tint = ElectricCyan400
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Tax & Order Discount Controls (Long IDR)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = customDiscountInput,
                        onValueChange = { raw ->
                            val digits = raw.filter { it.isDigit() }
                            customDiscountInput = digits
                            onOrderDiscountChange(digits.toLongOrNull() ?: 0L)
                        },
                        label = { Text("Diskon Nota (Rp)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("order_discount_input")
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "PPN 11%",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                        Switch(
                            checked = uiState.taxEnabled,
                            onCheckedChange = onTaxToggle,
                            modifier = Modifier.testTag("tax_ppn_switch")
                        )
                    }
                }

                // Single vs Split Payment Mode Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (uiState.splitPaymentMode) {
                            "Mode Split Payment (Multi-Metode Long IDR)"
                        } else {
                            "Metode Pembayaran: ${uiState.selectedPaymentMethod.name}"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = ElectricCyan400
                    )
                    FilterChip(
                        selected = uiState.splitPaymentMode,
                        onClick = { onToggleSplitMode(!uiState.splitPaymentMode) },
                        label = { Text(if (uiState.splitPaymentMode) "Split Aktif" else "Gunakan Split") },
                        modifier = Modifier.testTag("toggle_split_mode_button")
                    )
                }

                if (!uiState.splitPaymentMode) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            PaymentMethod.CASH,
                            PaymentMethod.QRIS,
                            PaymentMethod.DEBIT_CARD,
                            PaymentMethod.E_WALLET,
                            PaymentMethod.BANK_TRANSFER
                        ).forEach { method ->
                            FilterChip(
                                selected = uiState.selectedPaymentMethod == method,
                                onClick = { onSelectSingleMethod(method) },
                                label = { Text(method.name) },
                                modifier = Modifier.testTag("pay_method_${method.name.lowercase()}")
                            )
                        }
                    }

                    if (uiState.selectedPaymentMethod == PaymentMethod.CASH) {
                        OutlinedTextField(
                            value = customCashTenderInput,
                            onValueChange = { raw ->
                                val digits = raw.filter { it.isDigit() }
                                customCashTenderInput = digits
                                onSingleCashTenderedChange(digits.toLongOrNull() ?: 0L)
                            },
                            label = {
                                Text(
                                    "Uang Tunai Diterima (Saat ini: ${OfflineEscPosPrinterManager.formatRp(uiState.singleCashTenderedRp)})"
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("cash_tendered_input")
                        )
                    }
                } else {
                    // Split Payment Legs List + Add Leg Row
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        uiState.paymentSplits.forEach { leg ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = DeepNavy800,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${leg.method.name} (${leg.referenceCode})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = OfflineEscPosPrinterManager.formatRp(leg.tenderedRp),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = ElectricCyan400,
                                            fontWeight = FontWeight.Bold
                                        )
                                        IconButton(
                                            onClick = { onRemoveSplitLeg(leg.id) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Hapus Split",
                                                tint = SyncCrimson500,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = true,
                                onClick = {
                                    splitMethodSelection = when (splitMethodSelection) {
                                        PaymentMethod.CASH -> PaymentMethod.QRIS
                                        PaymentMethod.QRIS -> PaymentMethod.DEBIT_CARD
                                        PaymentMethod.DEBIT_CARD -> PaymentMethod.E_WALLET
                                        else -> PaymentMethod.CASH
                                    }
                                },
                                label = { Text(splitMethodSelection.name) }
                            )
                            OutlinedTextField(
                                value = splitAmountInput,
                                onValueChange = { raw ->
                                    splitAmountInput = raw.filter { it.isDigit() }
                                },
                                placeholder = {
                                    Text("Sisa: ${OfflineEscPosPrinterManager.formatRp(summary.remainingDueRp)}")
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    val amount = splitAmountInput.toLongOrNull() ?: summary.remainingDueRp
                                    if (amount > 0L) {
                                        onAddSplitLeg(splitMethodSelection, amount, "")
                                        splitAmountInput = ""
                                    }
                                },
                                modifier = Modifier.testTag("add_split_leg_button")
                            ) {
                                Text("+ Split")
                            }
                        }
                    }
                }
            }
        }
    }
}
