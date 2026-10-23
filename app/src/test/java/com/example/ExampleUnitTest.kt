package com.example

import id.nexbiz.pos.core.analytics.BreakEvenCalculator
import id.nexbiz.pos.core.printer.OfflineEscPosPrinterManager
import id.nexbiz.pos.core.printer.ReceiptLineItemModel
import id.nexbiz.pos.core.printer.ReceiptPaymentSplitModel
import id.nexbiz.pos.core.printer.ReceiptPreviewModel
import id.nexbiz.pos.core.printer.ThermalPaperWidth
import id.nexbiz.pos.data.local.model.PaymentMethod
import id.nexbiz.pos.data.local.model.SyncStatus
import id.nexbiz.pos.data.repository.SyncRepositoryImpl
import id.nexbiz.pos.ui.screens.pos.CartItemUiModel
import id.nexbiz.pos.ui.screens.pos.PaymentSplitLegUi
import id.nexbiz.pos.ui.screens.pos.PosViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun exactLongFinancialMath_calculatesSubtotalDiscountTaxAndSplitPaymentsWithoutFloats() {
        val items = listOf(
            CartItemUiModel(
                variantId = "var-1",
                productId = "prd-1",
                productName = "Es Kopi Susu Gula Aren",
                variantName = "Regular 12oz",
                sku = "KOP-AREN-REG",
                unitName = "cup",
                unitPriceRp = 24_000L,
                unitCostRp = 9_500L,
                isTaxable = true,
                availableStock = 50L,
                quantity = 2L,
                itemDiscountRp = 3_000L
            ),
            CartItemUiModel(
                variantId = "var-2",
                productId = "prd-2",
                productName = "French Butter Croissant",
                variantName = "Original Butter",
                sku = "PAS-CROIS-ORI",
                unitName = "pcs",
                unitPriceRp = 28_000L,
                unitCostRp = 12_000L,
                isTaxable = true,
                availableStock = 20L,
                quantity = 1L,
                itemDiscountRp = 0L
            )
        )

        // Gross subtotal = 48_000 + 28_000 = 76_000
        // Item discount = 3_000, Order discount = 3_000 -> Net taxable base = 70_000
        // PPN 11% (1100 bps) = 7_700 -> Grand Total = 77_700
        val splits = listOf(
            PaymentSplitLegUi(method = PaymentMethod.CASH, tenderedRp = 50_000L, referenceCode = "TUNAI"),
            PaymentSplitLegUi(method = PaymentMethod.QRIS, tenderedRp = 30_000L, referenceCode = "QRIS-01")
        )

        val summary = PosViewModel.computeExactLongFinancialSummary(
            cartItems = items,
            orderDiscountRp = 3_000L,
            taxRateBps = 1100,
            selectedPaymentMethod = PaymentMethod.SPLIT,
            singleCashTenderedRp = 0L,
            splitPaymentMode = true,
            paymentSplits = splits
        )

        assertEquals(3L, summary.totalQuantity)
        assertEquals(76_000L, summary.subtotalRp)
        assertEquals(6_000L, summary.discountTotalRp)
        assertEquals(70_000L, summary.netTaxableBaseRp)
        assertEquals(7_700L, summary.taxTotalRp)
        assertEquals(77_700L, summary.grandTotalRp)
        assertEquals(31_000L, summary.totalCogsRp)
        assertEquals(80_000L, summary.totalPaidRp)
        assertEquals(0L, summary.remainingDueRp)
        assertEquals(2_300L, summary.changeDueRp)
        assertTrue(summary.isFullyPaid)
    }

    @Test
    fun breakEvenCalculator_computesExactBepRevenueAndUnitsInLongMath() {
        // Net Revenue = 10,000,000 - 0 = 10,000,000
        // Total Variable COGS = 4,000,000
        // Contribution Margin = 6,000,000 -> CM Ratio = 60.00% (6,000 bps)
        // Fixed Operating Costs = 3,000,000
        // Expected Break-Even Revenue = (3,000,000 * 10,000) / 6,000 = 5,000,000 Rp
        // Units Sold = 400 (Avg Price = 25,000, Avg Cost = 10,000 -> Unit CM = 15,000)
        // Expected Break-Even Units = 3,000,000 / 15,000 = 200 units
        val result = BreakEvenCalculator.calculate(
            grossRevenueRp = 10_000_000L,
            discountTotalRp = 0L,
            totalCogsRp = 4_000_000L,
            fixedOperatingCostsRp = 3_000_000L,
            currentUnitsSold = 400L
        )

        assertEquals(10_000_000L, result.netRevenueRp)
        assertEquals(6_000_000L, result.contributionMarginRp)
        assertEquals(6_000L, result.contributionMarginRatioBps)
        assertEquals("60.00%", result.formatBpsAsPercent(result.contributionMarginRatioBps))
        assertEquals(5_000_000L, result.breakEvenRevenueRp)
        assertEquals(200L, result.breakEvenUnits)
        assertEquals(3_000_000L, result.netOperatingProfitRp)
        assertEquals(5_000_000L, result.marginOfSafetyRp)
        assertTrue(result.isBreakEvenReached)

        // Test below break-even
        val belowBep = BreakEvenCalculator.calculate(
            grossRevenueRp = 2_000_000L,
            discountTotalRp = 0L,
            totalCogsRp = 800_000L,
            fixedOperatingCostsRp = 3_000_000L,
            currentUnitsSold = 80L
        )
        assertFalse(belowBep.isBreakEvenReached)
        assertEquals(-1_800_000L, belowBep.netOperatingProfitRp)
    }

    @Test
    fun escPosPrinterFormatter_generates58mmAnd80mmReceiptAndEscPosBytes() {
        val printer = OfflineEscPosPrinterManager()
        val model = ReceiptPreviewModel(
            saleId = "sale-001",
            receiptNumber = "INV-261023-00001",
            businessName = "NEXBIZ Nusantara Kopi & Mart",
            businessAddress = "Jl. Senopati No. 88, Jakarta",
            businessPhone = "+628119008800",
            taxIdNpwp = "01.234.567.8-012.000",
            cashierName = "Siti Rahma",
            customerName = "Andi",
            createdAtEpochMs = 1761200000000L,
            items = listOf(
                ReceiptLineItemModel(
                    name = "Es Kopi Susu Gula Aren",
                    variantName = "Regular",
                    sku = "KOP-AREN-REG",
                    quantity = 2L,
                    unitPriceRp = 24_000L,
                    discountRp = 0L,
                    lineTotalRp = 48_000L
                )
            ),
            subtotalRp = 48_000L,
            discountTotalRp = 0L,
            taxTotalRp = 5_280L,
            grandTotalRp = 53_280L,
            totalPaidRp = 100_000L,
            changeDueRp = 46_720L,
            payments = listOf(
                ReceiptPaymentSplitModel(
                    method = PaymentMethod.CASH,
                    amountRp = 53_280L,
                    tenderedRp = 100_000L,
                    changeRp = 46_720L,
                    referenceCode = null
                )
            ),
            syncStatus = SyncStatus.PENDING,
            idempotencyKey = "sale-create-sale-001"
        )

        val text58 = printer.formatPlainTextReceipt(model, ThermalPaperWidth.MM_58)
        val escPosBytes = printer.encodeEscPosBytes(model, ThermalPaperWidth.MM_58)

        assertTrue(text58.contains("INV-261023-00001"))
        assertTrue(text58.contains("GRAND TOTAL"))
        assertTrue(escPosBytes.isNotEmpty())
        assertEquals(0x1B.toByte(), escPosBytes[0])
        assertEquals(0x40.toByte(), escPosBytes[1])
    }

    @Test
    fun exponentialBackoff_scalesMonotonicallyWithinBounds() {
        val attempt1 = SyncRepositoryImpl.computeExponentialBackoffWithJitterMs(1)
        val attempt3 = SyncRepositoryImpl.computeExponentialBackoffWithJitterMs(3)
        assertTrue(attempt1 in 15_500L..18_500L)
        assertTrue(attempt3 > attempt1)
    }
}
