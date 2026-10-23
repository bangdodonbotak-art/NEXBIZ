package id.nexbiz.pos.core.printer

import id.nexbiz.pos.data.local.model.PaymentMethod
import id.nexbiz.pos.data.local.model.SyncStatus
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ReceiptLineItemModel(
    val name: String,
    val variantName: String,
    val sku: String,
    val quantity: Long,
    val unitPriceRp: Long,
    val discountRp: Long,
    val lineTotalRp: Long
)

data class ReceiptPaymentSplitModel(
    val method: PaymentMethod,
    val amountRp: Long,
    val tenderedRp: Long,
    val changeRp: Long,
    val referenceCode: String?
)

data class ReceiptPreviewModel(
    val saleId: String,
    val receiptNumber: String,
    val businessName: String,
    val businessAddress: String,
    val businessPhone: String,
    val taxIdNpwp: String?,
    val cashierName: String,
    val customerName: String?,
    val createdAtEpochMs: Long,
    val items: List<ReceiptLineItemModel>,
    val subtotalRp: Long,
    val discountTotalRp: Long,
    val taxTotalRp: Long,
    val grandTotalRp: Long,
    val totalPaidRp: Long,
    val changeDueRp: Long,
    val payments: List<ReceiptPaymentSplitModel>,
    val syncStatus: SyncStatus,
    val idempotencyKey: String
)

enum class ThermalPaperWidth(val columns: Int, val label: String) {
    MM_58(32, "58mm (32 Kolom)"),
    MM_80(48, "80mm (48 Kolom)")
}

sealed class PrinterConnectionState {
    data object ReadyOfflineSpooler : PrinterConnectionState()
    data class ConnectedBluetooth(val deviceName: String) : PrinterConnectionState()
    data class Printing(val receiptNumber: String) : PrinterConnectionState()
    data class PrintSuccess(
        val receiptNumber: String,
        val printedAtMs: Long,
        val byteCount: Int
    ) : PrinterConnectionState()
    data class Error(val reason: String) : PrinterConnectionState()
}

interface ReceiptPrinterService {
    val printerState: StateFlow<PrinterConnectionState>
    fun formatPlainTextReceipt(
        receipt: ReceiptPreviewModel,
        paperWidth: ThermalPaperWidth = ThermalPaperWidth.MM_58
    ): String
    fun encodeEscPosBytes(
        receipt: ReceiptPreviewModel,
        paperWidth: ThermalPaperWidth = ThermalPaperWidth.MM_58
    ): ByteArray
    suspend fun printReceipt(
        receipt: ReceiptPreviewModel,
        paperWidth: ThermalPaperWidth = ThermalPaperWidth.MM_58
    ): Result<Int>
}

class OfflineEscPosPrinterManager : ReceiptPrinterService {

    private val _printerState =
        MutableStateFlow<PrinterConnectionState>(
            PrinterConnectionState.ConnectedBluetooth("RPP02N-58mm-Kasir01")
        )
    override val printerState: StateFlow<PrinterConnectionState> = _printerState.asStateFlow()

    override fun formatPlainTextReceipt(
        receipt: ReceiptPreviewModel,
        paperWidth: ThermalPaperWidth
    ): String {
        val cols = paperWidth.columns
        val divider = "-".repeat(cols)
        val doubleDivider = "=".repeat(cols)
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.forLanguageTag("id-ID"))

        return buildString {
            appendLine(centerText(receipt.businessName.uppercase(), cols))
            appendLine(centerText(receipt.businessAddress, cols))
            appendLine(centerText("Telp: ${receipt.businessPhone}", cols))
            if (!receipt.taxIdNpwp.isNullOrBlank()) {
                appendLine(centerText("NPWP: ${receipt.taxIdNpwp}", cols))
            }
            appendLine(doubleDivider)
            appendLine(twoColumn("No Struk", receipt.receiptNumber, cols))
            appendLine(twoColumn("Tanggal", dateFormat.format(Date(receipt.createdAtEpochMs)), cols))
            appendLine(twoColumn("Kasir", receipt.cashierName, cols))
            if (!receipt.customerName.isNullOrBlank()) {
                appendLine(twoColumn("Pelanggan", receipt.customerName, cols))
            }
            appendLine(divider)

            for (item in receipt.items) {
                val title = "${item.name} (${item.variantName})"
                appendLine(title.take(cols))
                val qtyPrice = "${item.quantity} x ${formatRp(item.unitPriceRp)}"
                val lineTotal = formatRp(item.lineTotalRp)
                appendLine(twoColumn(qtyPrice, lineTotal, cols))
                if (item.discountRp > 0L) {
                    appendLine(twoColumn("  Diskon Item", "-${formatRp(item.discountRp)}", cols))
                }
            }

            appendLine(divider)
            appendLine(twoColumn("Subtotal", formatRp(receipt.subtotalRp), cols))
            if (receipt.discountTotalRp > 0L) {
                appendLine(twoColumn("Total Diskon", "-${formatRp(receipt.discountTotalRp)}", cols))
            }
            if (receipt.taxTotalRp > 0L) {
                appendLine(twoColumn("PPN (11%)", formatRp(receipt.taxTotalRp), cols))
            }
            appendLine(doubleDivider)
            appendLine(twoColumn("GRAND TOTAL", formatRp(receipt.grandTotalRp), cols))
            appendLine(divider)

            for (payment in receipt.payments) {
                val label = if (receipt.payments.size > 1) {
                    "Bayar ${payment.method.name}"
                } else {
                    "Metode (${payment.method.name})"
                }
                appendLine(twoColumn(label, formatRp(payment.tenderedRp), cols))
                if (!payment.referenceCode.isNullOrBlank()) {
                    appendLine(twoColumn("  Ref", payment.referenceCode, cols))
                }
            }
            appendLine(twoColumn("Total Dibayar", formatRp(receipt.totalPaidRp), cols))
            appendLine(twoColumn("Kembalian", formatRp(receipt.changeDueRp), cols))
            appendLine(divider)
            appendLine(centerText("Status Sync: ${receipt.syncStatus.name}", cols))
            appendLine(centerText("ID: ${receipt.idempotencyKey.takeLast(14)}", cols))
            appendLine(centerText("Terima Kasih Atas Kunjungan Anda", cols))
            appendLine(centerText("Barang yang sudah dibeli", cols))
            appendLine(centerText("tidak dapat ditukar/dikembalikan", cols))
        }
    }

    override fun encodeEscPosBytes(
        receipt: ReceiptPreviewModel,
        paperWidth: ThermalPaperWidth
    ): ByteArray {
        val initCommand = byteArrayOf(0x1B, 0x40) // ESC @ Initialize printer
        val bodyBytes = formatPlainTextReceipt(receipt, paperWidth).encodeToByteArray()
        val feedAndCut = byteArrayOf(0x0A, 0x0A, 0x0A, 0x1D, 0x56, 0x41, 0x10) // GS V Cut
        return initCommand + bodyBytes + feedAndCut
    }

    override suspend fun printReceipt(
        receipt: ReceiptPreviewModel,
        paperWidth: ThermalPaperWidth
    ): Result<Int> {
        return runCatching {
            _printerState.value = PrinterConnectionState.Printing(receipt.receiptNumber)
            val payloadBytes = encodeEscPosBytes(receipt, paperWidth)
            delay(350L)
            val now = System.currentTimeMillis()
            _printerState.value = PrinterConnectionState.PrintSuccess(
                receiptNumber = receipt.receiptNumber,
                printedAtMs = now,
                byteCount = payloadBytes.size
            )
            payloadBytes.size
        }.onFailure { error ->
            _printerState.value = PrinterConnectionState.Error(
                error.message ?: "Gagal mencetak struk ESC/POS"
            )
        }
    }

    private fun centerText(text: String, width: Int): String {
        val trimmed = text.take(width)
        val totalPad = (width - trimmed.length).coerceAtLeast(0)
        val leftPad = totalPad / 2
        return " ".repeat(leftPad) + trimmed
    }

    private fun twoColumn(left: String, right: String, width: Int): String {
        val maxLeft = (width - right.length - 1).coerceAtLeast(1)
        val safeLeft = left.take(maxLeft)
        val spaces = (width - safeLeft.length - right.length).coerceAtLeast(1)
        return safeLeft + " ".repeat(spaces) + right
    }

    companion object {
        private val idrLocale = Locale.forLanguageTag("id-ID")

        fun formatRp(amountRp: Long): String {
            val formatter = NumberFormat.getNumberInstance(idrLocale).apply {
                maximumFractionDigits = 0
                minimumFractionDigits = 0
            }
            val sign = if (amountRp < 0L) "-Rp " else "Rp "
            return sign + formatter.format(kotlin.math.abs(amountRp))
        }
    }
}
