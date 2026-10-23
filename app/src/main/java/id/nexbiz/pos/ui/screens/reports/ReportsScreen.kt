package id.nexbiz.pos.ui.screens.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.nexbiz.pos.core.printer.OfflineEscPosPrinterManager
import id.nexbiz.pos.data.local.entity.AccountEntity
import id.nexbiz.pos.data.local.entity.JournalEntryEntity
import id.nexbiz.pos.ui.screens.dashboard.CashFlowReport
import id.nexbiz.pos.ui.screens.dashboard.DashboardUiState
import id.nexbiz.pos.ui.screens.dashboard.DashboardViewModel
import id.nexbiz.pos.ui.screens.dashboard.ProfitAndLossReport
import id.nexbiz.pos.ui.theme.DeepNavy900
import id.nexbiz.pos.ui.theme.ElectricCyan400
import id.nexbiz.pos.ui.theme.SyncCrimson500
import id.nexbiz.pos.ui.theme.SyncEmerald500
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReportsScreen(
    viewModel: DashboardViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("nexbiz_reports_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 900.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column {
                Text(
                    text = "Laporan Keuangan SAK EMKM (Offline-First)",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "${uiState.business?.legalName ?: "PT Nexbiz Niaga Nusantara"} • NPWP: ${uiState.business?.taxIdNpwp ?: "01.234.567.8-012.000"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Report Selector Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    label = { Text("Laba Rugi (P&L)") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Assessment, contentDescription = null)
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DeepNavy900,
                        selectedLabelColor = ElectricCyan400
                    ),
                    modifier = Modifier.testTag("report_tab_pnl")
                )
                FilterChip(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    label = { Text("Arus Kas (Cash Flow)") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.AccountBalance, contentDescription = null)
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DeepNavy900,
                        selectedLabelColor = ElectricCyan400
                    ),
                    modifier = Modifier.testTag("report_tab_cashflow")
                )
                FilterChip(
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 },
                    label = { Text("Jurnal & COA") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Book, contentDescription = null)
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DeepNavy900,
                        selectedLabelColor = ElectricCyan400
                    ),
                    modifier = Modifier.testTag("report_tab_journals")
                )
            }

            when (selectedTabIndex) {
                0 -> ProfitAndLossStatementSection(
                    uiState = uiState,
                    pnl = uiState.profitAndLoss,
                    modifier = Modifier.weight(1f)
                )
                1 -> CashFlowStatementSection(
                    cashFlow = uiState.cashFlow,
                    modifier = Modifier.weight(1f)
                )
                else -> DoubleEntryJournalSection(
                    journals = uiState.recentJournals,
                    accounts = uiState.accounts,
                    isAllBalanced = uiState.isAllJournalsBalanced,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ProfitAndLossStatementSection(
    uiState: DashboardUiState,
    pnl: ProfitAndLossReport,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .testTag("pnl_statement_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "LAPORAN LABA RUGI (PROFIT & LOSS STATEMENT)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Satuan Mata Uang: Rupiah Penuh (Long IDR)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            // Section 1: Revenue
            ReportSectionHeader("1. PENDAPATAN USAHA (REVENUE)")
            StatementLineRow("4101 - Pendapatan Penjualan POS Kotor", pnl.grossSalesRevenueRp)
            StatementLineRow(
                "4102 - Dikurangi: Potongan / Diskon Penjualan",
                -pnl.salesDiscountsRp,
                isNegative = pnl.salesDiscountsRp > 0L
            )
            StatementSubtotalRow("PENDAPATAN BERSIH (NET REVENUE)", pnl.netSalesRevenueRp)

            Spacer(modifier = Modifier.height(4.dp))

            // Section 2: COGS
            ReportSectionHeader("2. HARGA POKOK PENJUALAN (COGS)")
            StatementLineRow(
                "5101 - Harga Pokok Penjualan (HPP Perpetual)",
                -pnl.costOfGoodsSoldRp,
                isNegative = pnl.costOfGoodsSoldRp > 0L
            )
            StatementSubtotalRow(
                label = "LABA KOTOR (GROSS PROFIT • ${uiState.breakEvenAnalysis.formatBpsAsPercent(pnl.grossMarginBps)})",
                amountRp = pnl.grossProfitRp,
                highlightColor = SyncEmerald500
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Section 3: Operating Expenses
            ReportSectionHeader("3. BEBAN OPERASIONAL (OPERATING EXPENSES)")
            StatementLineRow("6101 - Beban Operasional Toko & Kemasan", pnl.storeOperationalExpenseRp)
            StatementLineRow("6102 - Beban Gaji & Insentif Barista/Kasir", pnl.salaryPayrollExpenseRp)
            StatementLineRow("6103 - Beban Listrik, Air & Internet", pnl.utilitiesInternetExpenseRp)
            StatementSubtotalRow(
                label = "TOTAL BEBAN OPERASIONAL",
                amountRp = -pnl.totalOperatingExpensesRp,
                highlightColor = if (pnl.totalOperatingExpensesRp > 0L) SyncCrimson500 else MaterialTheme.colorScheme.onSurface
            )

            HorizontalDivider(thickness = 2.dp)

            // Final Net Operating Profit Banner
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = DeepNavy900,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("net_operating_profit_banner")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "LABA BERSIH OPERASIONAL (NET PROFIT)",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Net Margin: ${uiState.breakEvenAnalysis.formatBpsAsPercent(pnl.netMarginBps)} • PPN Keluaran (2102): ${OfflineEscPosPrinterManager.formatRp(pnl.outputVatPayableRp)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = ElectricCyan400
                        )
                    }
                    Text(
                        text = OfflineEscPosPrinterManager.formatRp(pnl.netOperatingProfitRp),
                        style = MaterialTheme.typography.titleLarge,
                        color = if (pnl.netOperatingProfitRp >= 0L) SyncEmerald500 else SyncCrimson500,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

@Composable
private fun CashFlowStatementSection(
    cashFlow: CashFlowReport,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .testTag("cash_flow_statement_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "LAPORAN ARUS KAS (METODE LANGSUNG)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Rekonsiliasi Kas Tunai Kasir (1101) & Bank/QRIS Settlement (1102)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            ReportSectionHeader("1. ARUS KAS DARI AKTIVITAS OPERASI")
            StatementLineRow("Penerimaan Kas Penjualan Tunai (CASH)", cashFlow.cashSalesInflowRp)
            StatementLineRow("Penerimaan Settlement Non-Tunai (QRIS/Debit/Transfer)", cashFlow.nonCashQrisBankInflowRp)
            StatementLineRow(
                "Pengeluaran Kas untuk Beban Operasional (1101)",
                -cashFlow.cashExpensesOutflowRp,
                isNegative = cashFlow.cashExpensesOutflowRp > 0L
            )
            StatementLineRow(
                "Pengeluaran Bank untuk Beban Operasional (1102)",
                -cashFlow.bankExpensesOutflowRp,
                isNegative = cashFlow.bankExpensesOutflowRp > 0L
            )
            StatementSubtotalRow(
                label = "ARUS KAS BERSIH AKTIVITAS OPERASI",
                amountRp = cashFlow.netOperatingCashFlowRp,
                highlightColor = if (cashFlow.netOperatingCashFlowRp >= 0L) SyncEmerald500 else SyncCrimson500
            )

            Spacer(modifier = Modifier.height(4.dp))

            ReportSectionHeader("2. MUTASI KAS LACI & PENDANAAN SHIFT")
            StatementLineRow("Setoran Modal / Kas Masuk Laci (Shift In)", cashFlow.manualCashInRp)
            StatementLineRow(
                "Penarikan / Setor Kas ke Brankas (Shift Out)",
                -cashFlow.manualCashOutRp,
                isNegative = cashFlow.manualCashOutRp > 0L
            )
            StatementSubtotalRow(
                label = "ARUS KAS BERSIH MUTASI LACI",
                amountRp = cashFlow.netFinancingCashFlowRp
            )

            HorizontalDivider(thickness = 2.dp)

            ReportSectionHeader("3. POSISI SALDO KAS & BANK AKHIR (BUKU BESAR)")
            StatementLineRow("1101 - Saldo Akhir Kas Tunai Kasir (Laci POS)", cashFlow.endingCashDrawerBalanceRp)
            StatementLineRow("1102 - Saldo Akhir Bank BCA & Settlement QRIS", cashFlow.endingBankSettlementBalanceRp)

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = DeepNavy900,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("total_liquid_cash_banner")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TOTAL LIKUIDITAS KAS & BANK (1101 + 1102)",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = OfflineEscPosPrinterManager.formatRp(cashFlow.totalLiquidCashAndBankRp),
                        style = MaterialTheme.typography.titleLarge,
                        color = ElectricCyan400,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

@Composable
private fun DoubleEntryJournalSection(
    journals: List<JournalEntryEntity>,
    accounts: List<AccountEntity>,
    isAllBalanced: Boolean,
    modifier: Modifier = Modifier
) {
    val dateFormat = SimpleDateFormat("dd/MM HH:mm:ss", Locale.forLanguageTag("id-ID"))
    val totalAssetsRp = accounts
        .filter { it.code.startsWith("1") }
        .sumOf { it.currentBalanceRp }

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .testTag("double_entry_journal_list"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = DeepNavy900,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (isAllBalanced) SyncEmerald500 else SyncCrimson500
                        )
                        Column {
                            Text(
                                text = if (isAllBalanced) {
                                    "Verifikasi Double-Entry: Debit == Kredit"
                                } else {
                                    "Ketidakseimbangan Jurnal Terdeteksi"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${journals.size} entri jurnal • Total Aset (1xxx): ${OfflineEscPosPrinterManager.formatRp(totalAssetsRp)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = ElectricCyan400
                            )
                        }
                    }
                }
            }
        }

        items(journals, key = { it.id }) { entry ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${entry.entryNumber} • ${entry.referenceType}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = dateFormat.format(Date(entry.entryDate)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = entry.description,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Debit: ${OfflineEscPosPrinterManager.formatRp(entry.totalDebitRp)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = SyncEmerald500,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Kredit: ${OfflineEscPosPrinterManager.formatRp(entry.totalCreditRp)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = ElectricCyan400,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = ElectricCyan400,
        fontWeight = FontWeight.ExtraBold
    )
}

@Composable
private fun StatementLineRow(
    label: String,
    amountRp: Long,
    isNegative: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = OfflineEscPosPrinterManager.formatRp(amountRp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (isNegative) SyncCrimson500 else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun StatementSubtotalRow(
    label: String,
    amountRp: Long,
    highlightColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = OfflineEscPosPrinterManager.formatRp(amountRp),
                style = MaterialTheme.typography.titleSmall,
                color = highlightColor,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}
