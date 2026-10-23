package id.nexbiz.pos.ui.screens.pos

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.nexbiz.pos.core.printer.OfflineEscPosPrinterManager
import id.nexbiz.pos.core.printer.PrinterConnectionState
import id.nexbiz.pos.core.printer.ReceiptPreviewModel
import id.nexbiz.pos.core.printer.ThermalPaperWidth
import id.nexbiz.pos.data.local.dao.CatalogVariantItemTuple
import id.nexbiz.pos.data.local.dao.SaleWithItemsAndPayments
import id.nexbiz.pos.data.local.entity.AccountEntity
import id.nexbiz.pos.data.local.entity.SyncOutboxEntity
import id.nexbiz.pos.data.local.model.SyncStatus
import id.nexbiz.pos.ui.theme.DeepNavy900
import id.nexbiz.pos.ui.theme.ElectricCyan400
import id.nexbiz.pos.ui.theme.SyncAmber500
import id.nexbiz.pos.ui.theme.SyncCrimson500
import id.nexbiz.pos.ui.theme.SyncEmerald500

@Composable
fun OfflineReceiptPreviewDialog(
    receipt: ReceiptPreviewModel,
    plainTextReceipt: String,
    selectedPaperWidth: ThermalPaperWidth,
    printerState: PrinterConnectionState,
    onSelectPaperWidth: (ThermalPaperWidth) -> Unit,
    onPrintReceipt: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 16.dp)
                .testTag("offline_receipt_dialog"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = "Struk Offline",
                            tint = ElectricCyan400
                        )
                        Column {
                            Text(
                                text = "Struk Kasir Offline (ESC/POS)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${receipt.receiptNumber} • Sync: ${receipt.syncStatus.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_receipt_dialog_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup Pratinjau Struk"
                        )
                    }
                }

                // Paper Width Selector & Printer Status
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThermalPaperWidth.entries.forEach { width ->
                        FilterChip(
                            selected = selectedPaperWidth == width,
                            onClick = { onSelectPaperWidth(width) },
                            label = { Text(width.label) },
                            modifier = Modifier.testTag("paper_width_${width.name.lowercase()}")
                        )
                    }
                }

                PrinterStatusBadge(printerState = printerState)

                // Thermal Paper Monospace Preview
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    color = Color(0xFFFFFDF7),
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 2.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        Text(
                            text = plainTextReceipt,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = Color(0xFF0F172A),
                            modifier = Modifier.testTag("thermal_receipt_monospace_text")
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("done_receipt_button")
                    ) {
                        Text("Selesai")
                    }
                    Button(
                        onClick = onPrintReceipt,
                        modifier = Modifier
                            .weight(1.3f)
                            .height(48.dp)
                            .testTag("print_escpos_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DeepNavy900,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = "Cetak Struk Thermal"
                        )
                        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                        Text("Cetak Struk Thermal")
                    }
                }
            }
        }
    }
}

@Composable
private fun PrinterStatusBadge(printerState: PrinterConnectionState) {
    val (label, tint) = when (printerState) {
        is PrinterConnectionState.ReadyOfflineSpooler ->
            "Spooler ESC/POS Siap (Mode Offline)" to ElectricCyan400
        is PrinterConnectionState.ConnectedBluetooth ->
            "Printer Terhubung: ${printerState.deviceName}" to SyncEmerald500
        is PrinterConnectionState.Printing ->
            "Mengirim Byte Stream ESC/POS (${printerState.receiptNumber})..." to SyncAmber500
        is PrinterConnectionState.PrintSuccess ->
            "Tercetak (${printerState.byteCount} bytes ESC/POS dikirim)" to SyncEmerald500
        is PrinterConnectionState.Error ->
            "Printer Error: ${printerState.reason}" to SyncCrimson500
    }

    Surface(
        color = tint.copy(alpha = 0.14f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = tint
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = tint,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun OutboxAndLedgerInspectorDialog(
    outboxEntries: List<SyncOutboxEntity>,
    recentSales: List<SaleWithItemsAndPayments>,
    accounts: List<AccountEntity>,
    catalogVariants: List<CatalogVariantItemTuple>,
    onTriggerSyncNow: () -> Unit,
    onSimulateMultiDeviceConflict: (CatalogVariantItemTuple) -> Unit,
    onOpenSaleReceipt: (SaleWithItemsAndPayments) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 16.dp)
                .testTag("outbox_inspector_dialog"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Outbox Queue, Buku Besar COA & Rekonsiliasi Stok",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${outboxEntries.size} log outbox • ${recentSales.size} transaksi • ${accounts.size} akun SAK EMKM",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup Inspector"
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onTriggerSyncNow,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("inspector_sync_now_button")
                    ) {
                        Icon(imageVector = Icons.Default.Sync, contentDescription = null)
                        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                        Text("Push Outbox & Pull Delta")
                    }

                    val firstVariant = catalogVariants.firstOrNull()
                    if (firstVariant != null) {
                        OutlinedButton(
                            onClick = { onSimulateMultiDeviceConflict(firstVariant) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("simulate_conflict_button")
                        ) {
                            Icon(imageVector = Icons.Default.WarningAmber, contentDescription = null)
                            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                            Text("Uji Konflik Multi-Device")
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text(
                            text = "1. Antrean SyncOutboxEntity (Terbaru)",
                            style = MaterialTheme.typography.labelLarge,
                            color = ElectricCyan400
                        )
                    }

                    if (outboxEntries.isEmpty()) {
                        item {
                            Text(
                                text = "Belum ada mutasi di SyncOutboxEntity.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(outboxEntries.take(8), key = { it.id }) { entry ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${entry.operation.name} (${entry.entityType})",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = entry.syncStatus.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = when (entry.syncStatus) {
                                                SyncStatus.SYNCED -> SyncEmerald500
                                                SyncStatus.CONFLICT, SyncStatus.FAILED -> SyncCrimson500
                                                else -> SyncAmber500
                                            },
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                    Text(
                                        text = "Idempotency: ${entry.idempotencyKey} • Retry: ${entry.retryCount}/${entry.maxRetries}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (!entry.lastErrorMessage.isNullOrBlank()) {
                                        Text(
                                            text = "Info: ${entry.lastErrorMessage}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SyncCrimson500
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text(
                            text = "2. Riwayat Penjualan Offline (Klik untuk Cetak Ulang Struk)",
                            style = MaterialTheme.typography.labelLarge,
                            color = ElectricCyan400
                        )
                    }

                    items(recentSales.take(6), key = { it.sale.id }) { saleBundle ->
                        Surface(
                            onClick = { onOpenSaleReceipt(saleBundle) },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "${saleBundle.sale.receiptNumber} (${saleBundle.sale.paymentMethod.name})",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${saleBundle.items.size} SKU • Status: ${saleBundle.sale.syncStatus.name}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = OfflineEscPosPrinterManager.formatRp(saleBundle.sale.grandTotalRp),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }

                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text(
                            text = "3. Saldo Buku Besar COA Indonesia (SAK EMKM)",
                            style = MaterialTheme.typography.labelLarge,
                            color = ElectricCyan400
                        )
                    }

                    items(accounts, key = { it.id }) { account ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${account.code} - ${account.name}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = OfflineEscPosPrinterManager.formatRp(account.currentBalanceRp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
