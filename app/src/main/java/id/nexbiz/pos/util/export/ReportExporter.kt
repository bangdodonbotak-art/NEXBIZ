package id.nexbiz.pos.util.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import id.nexbiz.pos.core.printer.ReceiptPreviewModel
import id.nexbiz.pos.data.local.dao.CatalogVariantItemTuple
import id.nexbiz.pos.data.local.entity.InventoryMovementEntity
import id.nexbiz.pos.data.local.entity.JournalEntryEntity
import id.nexbiz.pos.data.local.entity.JournalLineEntity
import id.nexbiz.pos.ui.screens.dashboard.CashFlowReport
import id.nexbiz.pos.ui.screens.dashboard.ProfitAndLossReport
import id.nexbiz.pos.util.printer.EscPosPrinterHelper
import java.io.BufferedWriter
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Production PDF & CSV Exporter for NEXBIZ POS.
 * - Generates multi-section PDF documents using Android native `android.graphics.pdf.PdfDocument`
 *   for Ringkasan Penjualan, Laporan Laba Rugi (P&L SAK EMKM), and Struk Kasir.
 * - Generates RFC 4180 UTF-8 CSV files via Storage Access Framework (`Uri`) for Product Catalog,
 *   Stock Movements, and Double-Entry Journal Entries.
 * - All monetary amounts are strictly processed and formatted as `Long` (whole IDR Rupiah).
 */
class ReportExporter(private val context: Context) {

    private val dateTimeFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.forLanguageTag("id-ID"))

    /**
     * Generates a formal A4 PDF report containing Ringkasan Penjualan, Laporan Laba Rugi (P&L),
     * and Ringkasan Arus Kas into the target Storage Access Framework `Uri`.
     */
    suspend fun exportSalesAndProfitLossPdfToUri(
        targetUri: Uri,
        businessName: String,
        businessLegalName: String?,
        taxIdNpwp: String?,
        periodLabel: String,
        totalTransactionsCount: Int,
        totalUnitsSold: Long,
        pnl: ProfitAndLossReport,
        cashFlow: CashFlowReport
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val stream = context.contentResolver.openOutputStream(targetUri)
                ?: throw IOException("Gagal membuka OutputStream untuk URI PDF: $targetUri")
            stream.use { output ->
                writeSalesAndProfitLossPdfToStream(
                    outputStream = output,
                    businessName = businessName,
                    businessLegalName = businessLegalName,
                    taxIdNpwp = taxIdNpwp,
                    periodLabel = periodLabel,
                    totalTransactionsCount = totalTransactionsCount,
                    totalUnitsSold = totalUnitsSold,
                    pnl = pnl,
                    cashFlow = cashFlow
                )
            }
        }
    }

    fun writeSalesAndProfitLossPdfToStream(
        outputStream: OutputStream,
        businessName: String,
        businessLegalName: String?,
        taxIdNpwp: String?,
        periodLabel: String,
        totalTransactionsCount: Int,
        totalUnitsSold: Long,
        pnl: ProfitAndLossReport,
        cashFlow: CashFlowReport
    ): Long {
        val document = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH_PT, A4_HEIGHT_PT, 1).create()
            val page = document.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(10, 25, 47)
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textSize = 18f
            }
            val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(0, 150, 199)
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textSize = 12f
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(30, 41, 59)
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                textSize = 10.5f
            }
            val boldBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(15, 23, 42)
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textSize = 11f
            }
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(203, 213, 225)
                strokeWidth = 1f
            }

            var y = 48f
            val leftMargin = 40f
            val rightMargin = A4_WIDTH_PT - 40f

            // Header
            canvas.drawText(businessName.uppercase(), leftMargin, y, titlePaint)
            y += 18f
            if (!businessLegalName.isNullOrBlank()) {
                canvas.drawText(
                    "$businessLegalName • NPWP: ${taxIdNpwp ?: "-"}",
                    leftMargin,
                    y,
                    bodyPaint
                )
                y += 15f
            }
            canvas.drawText(
                "RINGKASAN PENJUALAN & LAPORAN LABA RUGI (SAK EMKM) — Periode: $periodLabel",
                leftMargin,
                y,
                boldBodyPaint
            )
            y += 14f
            canvas.drawText(
                "Dicetak pada: ${dateTimeFormatter.format(Date(System.currentTimeMillis()))} (Mata Uang: IDR Long)",
                leftMargin,
                y,
                bodyPaint
            )
            y += 14f
            canvas.drawLine(leftMargin, y, rightMargin, y, linePaint)
            y += 22f

            // Section 1: Ringkasan Penjualan
            canvas.drawText("1. RINGKASAN PENJUALAN POS", leftMargin, y, sectionPaint)
            y += 18f
            y = drawKeyValueRow(
                canvas,
                "Total Transaksi Selesai",
                "$totalTransactionsCount Nota",
                leftMargin,
                rightMargin,
                y,
                bodyPaint,
                boldBodyPaint
            )
            y = drawKeyValueRow(
                canvas,
                "Total Kuantitas Unit Terjual",
                "$totalUnitsSold Unit",
                leftMargin,
                rightMargin,
                y,
                bodyPaint,
                boldBodyPaint
            )
            y = drawKeyValueRow(
                canvas,
                "Penjualan Kotor (Gross Sales - 4101)",
                EscPosPrinterHelper.formatIdr(pnl.grossSalesRevenueRp),
                leftMargin,
                rightMargin,
                y,
                bodyPaint,
                boldBodyPaint
            )
            y = drawKeyValueRow(
                canvas,
                "Total Potongan / Diskon Penjualan (4102)",
                "-${EscPosPrinterHelper.formatIdr(pnl.salesDiscountsRp)}",
                leftMargin,
                rightMargin,
                y,
                bodyPaint,
                boldBodyPaint
            )
            y = drawKeyValueRow(
                canvas,
                "PPN Keluaran Terkumpul (11% - 2102)",
                EscPosPrinterHelper.formatIdr(pnl.outputVatPayableRp),
                leftMargin,
                rightMargin,
                y,
                bodyPaint,
                boldBodyPaint
            )
            y += 8f
            canvas.drawLine(leftMargin, y, rightMargin, y, linePaint)
            y += 22f

            // Section 2: Laporan Laba Rugi (P&L)
            canvas.drawText("2. LAPORAN LABA RUGI (PROFIT & LOSS)", leftMargin, y, sectionPaint)
            y += 18f
            y = drawKeyValueRow(
                canvas,
                "Pendapatan Bersih (Net Sales Revenue)",
                EscPosPrinterHelper.formatIdr(pnl.netSalesRevenueRp),
                leftMargin,
                rightMargin,
                y,
                boldBodyPaint,
                boldBodyPaint
            )
            y = drawKeyValueRow(
                canvas,
                "Dikurangi: Harga Pokok Penjualan / HPP (5101)",
                "-${EscPosPrinterHelper.formatIdr(pnl.costOfGoodsSoldRp)}",
                leftMargin,
                rightMargin,
                y,
                bodyPaint,
                boldBodyPaint
            )
            y = drawKeyValueRow(
                canvas,
                "LABA KOTOR (GROSS PROFIT)",
                EscPosPrinterHelper.formatIdr(pnl.grossProfitRp),
                leftMargin,
                rightMargin,
                y,
                boldBodyPaint,
                boldBodyPaint
            )
            y += 6f
            y = drawKeyValueRow(
                canvas,
                "6101 - Beban Operasional Toko & Kemasan",
                EscPosPrinterHelper.formatIdr(pnl.storeOperationalExpenseRp),
                leftMargin,
                rightMargin,
                y,
                bodyPaint,
                bodyPaint
            )
            y = drawKeyValueRow(
                canvas,
                "6102 - Beban Gaji & Insentif Kasir/Barista",
                EscPosPrinterHelper.formatIdr(pnl.salaryPayrollExpenseRp),
                leftMargin,
                rightMargin,
                y,
                bodyPaint,
                bodyPaint
            )
            y = drawKeyValueRow(
                canvas,
                "6103 - Beban Listrik, Air & Internet",
                EscPosPrinterHelper.formatIdr(pnl.utilitiesInternetExpenseRp),
                leftMargin,
                rightMargin,
                y,
                bodyPaint,
                bodyPaint
            )
            y = drawKeyValueRow(
                canvas,
                "Total Beban Operasional",
                "-${EscPosPrinterHelper.formatIdr(pnl.totalOperatingExpensesRp)}",
                leftMargin,
                rightMargin,
                y,
                boldBodyPaint,
                boldBodyPaint
            )
            y += 4f
            canvas.drawLine(leftMargin, y, rightMargin, y, linePaint)
            y += 18f
            y = drawKeyValueRow(
                canvas,
                "LABA BERSIH OPERASIONAL (NET OPERATING PROFIT)",
                EscPosPrinterHelper.formatIdr(pnl.netOperatingProfitRp),
                leftMargin,
                rightMargin,
                y,
                boldBodyPaint,
                boldBodyPaint
            )
            y += 12f
            canvas.drawLine(leftMargin, y, rightMargin, y, linePaint)
            y += 22f

            // Section 3: Ringkasan Arus Kas & Likuiditas
            canvas.drawText("3. RINGKASAN ARUS KAS & POSISI LIKUIDITAS", leftMargin, y, sectionPaint)
            y += 18f
            y = drawKeyValueRow(
                canvas,
                "Arus Kas Masuk Penjualan Tunai (CASH)",
                EscPosPrinterHelper.formatIdr(cashFlow.cashSalesInflowRp),
                leftMargin,
                rightMargin,
                y,
                bodyPaint,
                boldBodyPaint
            )
            y = drawKeyValueRow(
                canvas,
                "Arus Kas Masuk Settlement Non-Tunai (QRIS/Debit/Bank)",
                EscPosPrinterHelper.formatIdr(cashFlow.nonCashQrisBankInflowRp),
                leftMargin,
                rightMargin,
                y,
                bodyPaint,
                boldBodyPaint
            )
            y = drawKeyValueRow(
                canvas,
                "Arus Kas Bersih Aktivitas Operasi",
                EscPosPrinterHelper.formatIdr(cashFlow.netOperatingCashFlowRp),
                leftMargin,
                rightMargin,
                y,
                boldBodyPaint,
                boldBodyPaint
            )
            y = drawKeyValueRow(
                canvas,
                "Saldo Akhir Kas Tunai Laci (1101)",
                EscPosPrinterHelper.formatIdr(cashFlow.endingCashDrawerBalanceRp),
                leftMargin,
                rightMargin,
                y,
                bodyPaint,
                boldBodyPaint
            )
            y = drawKeyValueRow(
                canvas,
                "Saldo Akhir Bank BCA & Settlement (1102)",
                EscPosPrinterHelper.formatIdr(cashFlow.endingBankSettlementBalanceRp),
                leftMargin,
                rightMargin,
                y,
                bodyPaint,
                boldBodyPaint
            )
            drawKeyValueRow(
                canvas,
                "TOTAL LIKUIDITAS KAS & BANK (1101 + 1102)",
                EscPosPrinterHelper.formatIdr(cashFlow.totalLiquidCashAndBankRp),
                leftMargin,
                rightMargin,
                y,
                boldBodyPaint,
                boldBodyPaint
            )

            document.finishPage(page)
            document.writeTo(outputStream)
            outputStream.flush()
            return System.currentTimeMillis()
        } finally {
            document.close()
        }
    }

    /**
     * Generates a PDF receipt for a single POS sale transaction (`ReceiptPreviewModel`).
     */
    suspend fun exportReceiptPdfToUri(
        targetUri: Uri,
        receipt: ReceiptPreviewModel
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val stream = context.contentResolver.openOutputStream(targetUri)
                ?: throw IOException("Gagal membuka OutputStream untuk Struk PDF: $targetUri")
            stream.use { output ->
                writeReceiptPdfToStream(output, receipt)
            }
        }
    }

    fun writeReceiptPdfToStream(
        outputStream: OutputStream,
        receipt: ReceiptPreviewModel
    ): Long {
        val document = PdfDocument()
        try {
            val estimatedHeight = (360 + receipt.items.size * 34 + receipt.payments.size * 24).coerceAtLeast(480)
            val pageInfo = PdfDocument.PageInfo.Builder(RECEIPT_PDF_WIDTH_PT, estimatedHeight, 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textSize = 11f
                textAlign = Paint.Align.CENTER
            }
            val monoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                textSize = 9.5f
            }
            val monoBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textSize = 10f
            }

            val centerX = RECEIPT_PDF_WIDTH_PT / 2f
            val leftX = 16f
            val rightX = RECEIPT_PDF_WIDTH_PT - 16f
            var y = 26f

            canvas.drawText(receipt.businessName.uppercase(), centerX, y, headerPaint)
            y += 14f
            canvas.drawText(receipt.businessAddress, centerX, y, monoPaint.apply { textAlign = Paint.Align.CENTER })
            y += 13f
            canvas.drawText("Telp: ${receipt.businessPhone}", centerX, y, monoPaint)
            if (!receipt.taxIdNpwp.isNullOrBlank()) {
                y += 13f
                canvas.drawText("NPWP: ${receipt.taxIdNpwp}", centerX, y, monoPaint)
            }
            monoPaint.textAlign = Paint.Align.LEFT
            y += 16f

            y = drawKeyValueRow(canvas, "No. Struk", receipt.receiptNumber, leftX, rightX, y, monoPaint, monoBoldPaint)
            y = drawKeyValueRow(
                canvas,
                "Tanggal",
                dateTimeFormatter.format(Date(receipt.createdAtEpochMs)),
                leftX,
                rightX,
                y,
                monoPaint,
                monoPaint
            )
            y = drawKeyValueRow(canvas, "Kasir", receipt.cashierName, leftX, rightX, y, monoPaint, monoPaint)
            y += 6f

            for (item in receipt.items) {
                canvas.drawText("${item.name} (${item.variantName})", leftX, y, monoBoldPaint)
                y += 13f
                y = drawKeyValueRow(
                    canvas,
                    "${item.quantity} x ${EscPosPrinterHelper.formatIdr(item.unitPriceRp)}",
                    EscPosPrinterHelper.formatIdr(item.lineTotalRp),
                    leftX,
                    rightX,
                    y,
                    monoPaint,
                    monoPaint
                )
                if (item.discountRp > 0L) {
                    y = drawKeyValueRow(
                        canvas,
                        "  Diskon",
                        "-${EscPosPrinterHelper.formatIdr(item.discountRp)}",
                        leftX,
                        rightX,
                        y,
                        monoPaint,
                        monoPaint
                    )
                }
            }

            y += 6f
            y = drawKeyValueRow(canvas, "Subtotal", EscPosPrinterHelper.formatIdr(receipt.subtotalRp), leftX, rightX, y, monoPaint, monoPaint)
            if (receipt.discountTotalRp > 0L) {
                y = drawKeyValueRow(canvas, "Total Diskon", "-${EscPosPrinterHelper.formatIdr(receipt.discountTotalRp)}", leftX, rightX, y, monoPaint, monoPaint)
            }
            if (receipt.taxTotalRp > 0L) {
                y = drawKeyValueRow(canvas, "PPN (11%)", EscPosPrinterHelper.formatIdr(receipt.taxTotalRp), leftX, rightX, y, monoPaint, monoPaint)
            }
            y = drawKeyValueRow(canvas, "GRAND TOTAL", EscPosPrinterHelper.formatIdr(receipt.grandTotalRp), leftX, rightX, y, monoBoldPaint, monoBoldPaint)

            for (payment in receipt.payments) {
                y = drawKeyValueRow(
                    canvas,
                    "Bayar ${payment.method.name}",
                    EscPosPrinterHelper.formatIdr(payment.tenderedRp),
                    leftX,
                    rightX,
                    y,
                    monoPaint,
                    monoPaint
                )
            }
            y = drawKeyValueRow(canvas, "Kembalian", EscPosPrinterHelper.formatIdr(receipt.changeDueRp), leftX, rightX, y, monoBoldPaint, monoBoldPaint)

            document.finishPage(page)
            document.writeTo(outputStream)
            outputStream.flush()
            return System.currentTimeMillis()
        } finally {
            document.close()
        }
    }

    /**
     * Exports the Product Catalog (`CatalogVariantItemTuple` list) to CSV via Storage Access Framework `Uri`.
     */
    suspend fun exportProductCatalogCsv(
        targetUri: Uri,
        catalogItems: List<CatalogVariantItemTuple>
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val stream = context.contentResolver.openOutputStream(targetUri)
                ?: throw IOException("Gagal membuka OutputStream untuk CSV Katalog: $targetUri")
            stream.use { output ->
                writeProductCatalogCsvToStream(output, catalogItems)
            }
        }
    }

    fun writeProductCatalogCsvToStream(
        outputStream: OutputStream,
        catalogItems: List<CatalogVariantItemTuple>
    ): Int {
        val writer = BufferedWriter(OutputStreamWriter(outputStream, StandardCharsets.UTF_8))
        writer.write("\uFEFF") // UTF-8 BOM for spreadsheet compatibility
        writer.write(
            csvRow(
                "variant_id",
                "product_id",
                "business_id",
                "category_name",
                "product_name",
                "variant_name",
                "sku",
                "barcode",
                "unit_name",
                "selling_price_rp",
                "average_cost_rp",
                "local_estimated_stock",
                "server_confirmed_stock",
                "min_stock_threshold",
                "sync_status"
            )
        )
        for (item in catalogItems) {
            writer.write(
                csvRow(
                    item.variantId,
                    item.productId,
                    item.businessId,
                    item.categoryName ?: "",
                    item.productName,
                    item.variantName,
                    item.sku,
                    item.barcode ?: "",
                    item.unitName,
                    item.sellingPriceRp.toString(),
                    item.averageCostRp.toString(),
                    item.localEstimatedStock.toString(),
                    item.serverConfirmedStock.toString(),
                    item.minStockThreshold.toString(),
                    item.syncStatus.name
                )
            )
        }
        writer.flush()
        return catalogItems.size
    }

    /**
     * Exports Stock Movements (`InventoryMovementEntity` list) to CSV via Storage Access Framework `Uri`.
     */
    suspend fun exportStockMovementsCsv(
        targetUri: Uri,
        movements: List<InventoryMovementEntity>
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val stream = context.contentResolver.openOutputStream(targetUri)
                ?: throw IOException("Gagal membuka OutputStream untuk CSV Mutasi Stok: $targetUri")
            stream.use { output ->
                writeStockMovementsCsvToStream(output, movements)
            }
        }
    }

    fun writeStockMovementsCsvToStream(
        outputStream: OutputStream,
        movements: List<InventoryMovementEntity>
    ): Int {
        val writer = BufferedWriter(OutputStreamWriter(outputStream, StandardCharsets.UTF_8))
        writer.write("\uFEFF")
        writer.write(
            csvRow(
                "movement_id",
                "business_id",
                "variant_id",
                "movement_type",
                "quantity_delta",
                "stock_before",
                "stock_after",
                "unit_cost_rp",
                "reference_type",
                "reference_id",
                "notes",
                "created_by_user_id",
                "sync_status",
                "created_at_epoch_ms",
                "created_at_formatted"
            )
        )
        for (mov in movements) {
            writer.write(
                csvRow(
                    mov.id,
                    mov.businessId,
                    mov.variantId,
                    mov.movementType.name,
                    mov.quantityDelta.toString(),
                    mov.stockBefore.toString(),
                    mov.stockAfter.toString(),
                    mov.unitCostRp.toString(),
                    mov.referenceType ?: "",
                    mov.referenceId ?: "",
                    mov.notes ?: "",
                    mov.createdByUserId,
                    mov.syncStatus.name,
                    mov.createdAt.toString(),
                    dateTimeFormatter.format(Date(mov.createdAt))
                )
            )
        }
        writer.flush()
        return movements.size
    }

    /**
     * Exports Double-Entry Journal Entries & Lines (`JournalEntryEntity` + `JournalLineEntity`) to CSV via SAF `Uri`.
     */
    suspend fun exportJournalEntriesCsv(
        targetUri: Uri,
        entries: List<JournalEntryEntity>,
        linesByEntryId: Map<String, List<JournalLineEntity>> = emptyMap()
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val stream = context.contentResolver.openOutputStream(targetUri)
                ?: throw IOException("Gagal membuka OutputStream untuk CSV Jurnal Umum: $targetUri")
            stream.use { output ->
                writeJournalEntriesCsvToStream(output, entries, linesByEntryId)
            }
        }
    }

    fun writeJournalEntriesCsvToStream(
        outputStream: OutputStream,
        entries: List<JournalEntryEntity>,
        linesByEntryId: Map<String, List<JournalLineEntity>> = emptyMap()
    ): Int {
        val writer = BufferedWriter(OutputStreamWriter(outputStream, StandardCharsets.UTF_8))
        writer.write("\uFEFF")
        writer.write(
            csvRow(
                "entry_id",
                "business_id",
                "entry_number",
                "entry_date_epoch_ms",
                "entry_date_formatted",
                "reference_type",
                "reference_id",
                "description",
                "account_code",
                "line_debit_rp",
                "line_credit_rp",
                "entry_total_debit_rp",
                "entry_total_credit_rp",
                "is_balanced",
                "sync_status"
            )
        )
        var rowsWritten = 0
        for (entry in entries) {
            val formattedDate = dateTimeFormatter.format(Date(entry.entryDate))
            val lines = linesByEntryId[entry.id].orEmpty()
            if (lines.isEmpty()) {
                writer.write(
                    csvRow(
                        entry.id,
                        entry.businessId,
                        entry.entryNumber,
                        entry.entryDate.toString(),
                        formattedDate,
                        entry.referenceType,
                        entry.referenceId,
                        entry.description,
                        "HEADER_SUMMARY",
                        entry.totalDebitRp.toString(),
                        entry.totalCreditRp.toString(),
                        entry.totalDebitRp.toString(),
                        entry.totalCreditRp.toString(),
                        entry.isBalanced.toString(),
                        entry.syncStatus.name
                    )
                )
                rowsWritten++
            } else {
                for (line in lines) {
                    writer.write(
                        csvRow(
                            entry.id,
                            entry.businessId,
                            entry.entryNumber,
                            entry.entryDate.toString(),
                            formattedDate,
                            entry.referenceType,
                            entry.referenceId,
                            line.memo ?: entry.description,
                            line.accountCode,
                            line.debitRp.toString(),
                            line.creditRp.toString(),
                            entry.totalDebitRp.toString(),
                            entry.totalCreditRp.toString(),
                            entry.isBalanced.toString(),
                            entry.syncStatus.name
                        )
                    )
                    rowsWritten++
                }
            }
        }
        writer.flush()
        return rowsWritten
    }

    private fun drawKeyValueRow(
        canvas: Canvas,
        label: String,
        value: String,
        leftX: Float,
        rightX: Float,
        y: Float,
        labelPaint: Paint,
        valuePaint: Paint
    ): Float {
        canvas.drawText(label, leftX, y, labelPaint)
        val previousAlign = valuePaint.textAlign
        valuePaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(value, rightX, y, valuePaint)
        valuePaint.textAlign = previousAlign
        return y + 16f
    }

    companion object {
        private const val A4_WIDTH_PT = 595
        private const val A4_HEIGHT_PT = 842
        private const val RECEIPT_PDF_WIDTH_PT = 260

        fun escapeCsvField(field: String): String {
            val needsQuotes = field.contains(',') ||
                field.contains('"') ||
                field.contains('\n') ||
                field.contains('\r')
            return if (needsQuotes) {
                "\"${field.replace("\"", "\"\"")}\""
            } else {
                field
            }
        }

        fun csvRow(vararg fields: String): String =
            fields.joinToString(separator = ",", postfix = "\r\n") { escapeCsvField(it) }
    }
}
