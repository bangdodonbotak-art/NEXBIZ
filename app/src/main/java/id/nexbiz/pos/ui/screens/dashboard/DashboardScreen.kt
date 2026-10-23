package id.nexbiz.pos.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.nexbiz.pos.core.printer.OfflineEscPosPrinterManager
import id.nexbiz.pos.ui.theme.DeepNavy800
import id.nexbiz.pos.ui.theme.DeepNavy900
import id.nexbiz.pos.ui.theme.ElectricCyan400
import id.nexbiz.pos.ui.theme.SyncAmber500
import id.nexbiz.pos.ui.theme.SyncCrimson500
import id.nexbiz.pos.ui.theme.SyncEmerald500

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToReports: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val bep = uiState.breakEvenAnalysis
    val pnl = uiState.profitAndLoss
    val cashFlow = uiState.cashFlow

    var showExpenseSheet by rememberSaveable { mutableStateOf(false) }
    var fixedCostInput by remember { mutableStateOf("") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("nexbiz_dashboard_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 900.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Executive POS & BEP Dashboard",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "${uiState.business?.name ?: "NEXBIZ Nusantara"} • ${uiState.totalTransactionsCount} Transaksi Aktif",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = onNavigateToReports,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DeepNavy900,
                        contentColor = ElectricCyan400
                    ),
                    modifier = Modifier.testTag("dashboard_open_reports_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Analytics,
                        contentDescription = "Laporan Keuangan"
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("P&L & Arus Kas")
                }
            }

            // 4 Primary Financial KPI Cards (Long IDR)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                maxItemsInEachRow = 2
            ) {
                KpiMetricCard(
                    title = "Pendapatan Bersih",
                    value = OfflineEscPosPrinterManager.formatRp(pnl.netSalesRevenueRp),
                    subtitle = "${uiState.totalUnitsSold} unit terjual",
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    accentColor = ElectricCyan400,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("kpi_net_revenue_card")
                )
                KpiMetricCard(
                    title = "Laba Kotor (Margin)",
                    value = OfflineEscPosPrinterManager.formatRp(pnl.grossProfitRp),
                    subtitle = "Margin: ${bep.formatBpsAsPercent(pnl.grossMarginBps)}",
                    icon = Icons.Default.Receipt,
                    accentColor = SyncEmerald500,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("kpi_gross_profit_card")
                )
                KpiMetricCard(
                    title = "Total Kas & Bank",
                    value = OfflineEscPosPrinterManager.formatRp(cashFlow.totalLiquidCashAndBankRp),
                    subtitle = "Tunai: ${OfflineEscPosPrinterManager.formatRp(cashFlow.endingCashDrawerBalanceRp)}",
                    icon = Icons.Default.AccountBalanceWallet,
                    accentColor = ElectricCyan400,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("kpi_liquid_cash_card")
                )
                KpiMetricCard(
                    title = "Valuasi Persediaan",
                    value = OfflineEscPosPrinterManager.formatRp(uiState.inventoryValuationRp),
                    subtitle = "${uiState.totalSkusTracked} varian SKU aktif",
                    icon = Icons.Default.Inventory2,
                    accentColor = SyncAmber500,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("kpi_inventory_valuation_card")
                )
            }

            // Break-Even Point (BEP) Analyzer Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("break_even_analyzer_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DeepNavy900),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Analisis Titik Impas (Break-Even Point)",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Kalkulasi Integer Long IDR & Basis Points (Tanpa Float)",
                                style = MaterialTheme.typography.bodySmall,
                                color = ElectricCyan400
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (bep.isBreakEvenReached) {
                                SyncEmerald500.copy(alpha = 0.2f)
                            } else {
                                SyncAmber500.copy(alpha = 0.2f)
                            }
                        ) {
                            Text(
                                text = if (bep.isBreakEvenReached) "IMPASS / PROFIT" else "MENUJU BEP",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (bep.isBreakEvenReached) SyncEmerald500 else SyncAmber500,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Progress Bar (derived strictly from basis points 0..10000)
                    val progressFraction = (bep.breakEvenProgressBps.coerceIn(0L, 10_000L)).toInt() / 10_000f
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        color = if (bep.isBreakEvenReached) SyncEmerald500 else ElectricCyan400,
                        trackColor = DeepNavy800,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        BepStatColumn(
                            label = "Target Omzet BEP",
                            value = OfflineEscPosPrinterManager.formatRp(bep.breakEvenRevenueRp),
                            accent = ElectricCyan400
                        )
                        BepStatColumn(
                            label = "Target Unit BEP",
                            value = "${bep.breakEvenUnits} cup/pcs",
                            accent = Color.White
                        )
                        BepStatColumn(
                            label = "Rasio Margin Kontribusi",
                            value = bep.formatBpsAsPercent(bep.contributionMarginRatioBps),
                            accent = SyncEmerald500
                        )
                    }

                    HorizontalDivider(color = Color(0xFF1E3A5F))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        BepStatColumn(
                            label = "Biaya Tetap Operasional",
                            value = OfflineEscPosPrinterManager.formatRp(bep.fixedOperatingCostsRp),
                            accent = SyncAmber500
                        )
                        BepStatColumn(
                            label = "Margin Kontribusi Saat Ini",
                            value = OfflineEscPosPrinterManager.formatRp(bep.contributionMarginRp),
                            accent = Color.White
                        )
                        BepStatColumn(
                            label = "Margin of Safety",
                            value = OfflineEscPosPrinterManager.formatRp(bep.marginOfSafetyRp),
                            accent = if (bep.marginOfSafetyRp >= 0L) SyncEmerald500 else SyncCrimson500
                        )
                    }

                    // Quick Fixed Cost Target Preset Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = fixedCostInput,
                            onValueChange = { raw ->
                                val digits = raw.filter { it.isDigit() }
                                fixedCostInput = digits
                                digits.toLongOrNull()?.let { viewModel.updateFixedCostTargetRp(it) }
                            },
                            label = { Text("Ubah Target Biaya Tetap (Rp)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("bep_fixed_cost_input")
                        )
                        OutlinedButton(
                            onClick = { showExpenseSheet = !showExpenseSheet },
                            modifier = Modifier.testTag("toggle_expense_entry_button")
                        ) {
                            Icon(imageVector = Icons.Default.Payments, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (showExpenseSheet) "Tutup Form" else "+ Beban / Kas")
                        }
                    }
                }
            }

            // Optional Quick Expense & Cash Drawer Entry Card
            if (showExpenseSheet) {
                QuickExpenseAndCashCard(
                    onRecordExpense = { category, accCode, paidCode, amountRp, vendor ->
                        viewModel.recordOperationalExpense(
                            categoryName = category,
                            expenseAccountCode = accCode,
                            paidFromAccountCode = paidCode,
                            amountRp = amountRp,
                            vendorName = vendor,
                            notes = "Dicatat dari Dashboard POS"
                        )
                        showExpenseSheet = false
                    },
                    onRecordCashMovement = { isCashIn, amountRp, reason ->
                        viewModel.recordCashDrawerMovement(isCashIn, amountRp, reason)
                        showExpenseSheet = false
                    }
                )
            }

            // Double-Entry Ledger Health & Low Stock Alerts
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ledger_and_stock_health_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (uiState.isAllJournalsBalanced) {
                                    Icons.Default.CheckCircle
                                } else {
                                    Icons.Default.WarningAmber
                                },
                                contentDescription = null,
                                tint = if (uiState.isAllJournalsBalanced) SyncEmerald500 else SyncCrimson500
                            )
                            Text(
                                text = if (uiState.isAllJournalsBalanced) {
                                    "Integritas Jurnal Double-Entry: 100% SEIMBANG (${uiState.recentJournals.size} Jurnal)"
                                } else {
                                    "Peringatan: Ditemukan Jurnal Tidak Seimbang"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (uiState.lowStockAlerts.isNotEmpty()) {
                        HorizontalDivider()
                        Text(
                            text = "Peringatan Stok Menipis (${uiState.lowStockAlerts.size} SKU <= Batas Minimum)",
                            style = MaterialTheme.typography.labelLarge,
                            color = SyncAmber500,
                            fontWeight = FontWeight.Bold
                        )
                        uiState.lowStockAlerts.forEach { alert ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${alert.productName} (${alert.variantName}) [${alert.sku}]",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "Lokal: ${alert.localEstimatedStock} | Server: ${alert.serverConfirmedStock} (Min ${alert.minStockThreshold})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SyncCrimson500,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiMetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BepStatColumn(
    label: String,
    value: String,
    accent: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF94A3B8)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = accent,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
private fun QuickExpenseAndCashCard(
    onRecordExpense: (String, String, String, Long, String) -> Unit,
    onRecordCashMovement: (Boolean, Long, String) -> Unit
) {
    var selectedAccountCode by remember { mutableStateOf("6101") }
    var paidFromCode by remember { mutableStateOf("1101") }
    var amountInput by remember { mutableStateOf("250000") }
    var descriptionInput by remember { mutableStateOf("Pembelian Cup & Kemasan") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("quick_expense_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Catat Beban Operasional / Mutasi Kas Laci (Offline @Transaction)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedAccountCode == "6101",
                    onClick = {
                        selectedAccountCode = "6101"
                        descriptionInput = "Beban Operasional & Kemasan"
                    },
                    label = { Text("6101 Toko") }
                )
                FilterChip(
                    selected = selectedAccountCode == "6102",
                    onClick = {
                        selectedAccountCode = "6102"
                        descriptionInput = "Insentif / Gaji Shift Kasir"
                    },
                    label = { Text("6102 Gaji") }
                )
                FilterChip(
                    selected = selectedAccountCode == "6103",
                    onClick = {
                        selectedAccountCode = "6103"
                        descriptionInput = "Token Listrik & Internet"
                    },
                    label = { Text("6103 Utilitas") }
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = paidFromCode == "1101",
                    onClick = { paidFromCode = "1101" },
                    label = { Text("Bayar dari 1101 Kas Tunai") }
                )
                FilterChip(
                    selected = paidFromCode == "1102",
                    onClick = { paidFromCode = "1102" },
                    label = { Text("Bayar dari 1102 Bank BCA") }
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = descriptionInput,
                    onValueChange = { descriptionInput = it },
                    label = { Text("Keterangan") },
                    singleLine = true,
                    modifier = Modifier.weight(1.2f)
                )
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { raw -> amountInput = raw.filter { it.isDigit() } },
                    label = { Text("Nominal (Rp)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val amt = amountInput.toLongOrNull() ?: 0L
                        onRecordExpense(
                            descriptionInput,
                            selectedAccountCode,
                            paidFromCode,
                            amt,
                            "Vendor Lokal"
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("submit_expense_button")
                ) {
                    Icon(imageVector = Icons.Default.AddBusiness, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Simpan Beban")
                }

                OutlinedButton(
                    onClick = {
                        val amt = amountInput.toLongOrNull() ?: 0L
                        onRecordCashMovement(true, amt, descriptionInput)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("submit_cash_in_button")
                ) {
                    Text("+ Kas Masuk Laci")
                }
            }
        }
    }
}
