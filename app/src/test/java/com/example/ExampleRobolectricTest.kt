package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import id.nexbiz.pos.data.local.db.NexbizDatabase
import id.nexbiz.pos.data.local.entity.ExpenseEntity
import id.nexbiz.pos.data.local.entity.PaymentEntity
import id.nexbiz.pos.data.local.entity.SaleEntity
import id.nexbiz.pos.data.local.entity.SaleItemEntity
import id.nexbiz.pos.data.local.model.PaymentMethod
import id.nexbiz.pos.data.local.model.SyncStatus
import id.nexbiz.pos.di.DatabaseModule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("NEXBIZ", appName)
    }

    @Test
    fun `completeOfflineSale deducts stock posts balanced journal and updates COA balances atomically`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, NexbizDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            DatabaseModule.ensureSeeded(db)
            val businessId = DatabaseModule.DEFAULT_BUSINESS_ID
            val variantId = "var-0001-4000-8000-000000000001"

            val beforeBalance = db.inventoryDao().getBalanceForVariant(businessId, variantId)!!
            val cashBefore = db.saleDao().getAccountByCode(businessId, "1101")!!.currentBalanceRp
            val inventoryAccBefore = db.saleDao().getAccountByCode(businessId, "1104")!!.currentBalanceRp
            assertEquals(85L, beforeBalance.localEstimatedStock)

            val now = 1761200000000L
            val saleId = "sale-test-atomic-001"
            // Subtotal = 48_000, Discount = 3_000 -> Net = 45_000, PPN 11% = 4_950 -> Grand Total = 49_950
            // COGS = 2 * 9_500 = 19_000
            val sale = SaleEntity(
                id = saleId,
                businessId = businessId,
                receiptNumber = "INV-TEST-001",
                cashierUserId = DatabaseModule.DEFAULT_CASHIER_USER_ID,
                customerName = "Budi",
                subtotalRp = 48_000L,
                discountTotalRp = 3_000L,
                taxTotalRp = 4_950L,
                grandTotalRp = 49_950L,
                totalPaidRp = 50_000L,
                changeDueRp = 50L,
                totalCogsRp = 19_000L,
                paymentMethod = PaymentMethod.CASH,
                isVoided = false,
                voidReason = null,
                voidedAt = null,
                notes = null,
                syncStatus = SyncStatus.PENDING,
                version = 1L,
                createdAt = now,
                updatedAt = now
            )
            val items = listOf(
                SaleItemEntity(
                    id = "sitem-001",
                    businessId = businessId,
                    saleId = saleId,
                    variantId = variantId,
                    productNameSnapshot = "Es Kopi Susu Gula Aren",
                    variantNameSnapshot = "Regular 12oz",
                    skuSnapshot = "KOP-AREN-REG",
                    quantity = 2L,
                    unitPriceRp = 24_000L,
                    unitCostRp = 9_500L,
                    discountRp = 3_000L,
                    taxRp = 4_950L,
                    lineTotalRp = 49_950L,
                    syncStatus = SyncStatus.PENDING,
                    createdAt = now
                )
            )
            val payments = listOf(
                PaymentEntity(
                    id = "pay-001",
                    businessId = businessId,
                    saleId = saleId,
                    paymentMethod = PaymentMethod.CASH,
                    amountRp = 49_950L,
                    tenderedRp = 50_000L,
                    changeRp = 50L,
                    referenceCode = null,
                    paidAt = now,
                    syncStatus = SyncStatus.PENDING,
                    createdAt = now
                )
            )

            db.saleDao().completeOfflineSale(
                sale = sale,
                items = items,
                payments = payments,
                outboxPayloadJson = """{"sale_id":"$saleId"}"""
            )

            // 1. Verify stock deducted
            val afterBalance = db.inventoryDao().getBalanceForVariant(businessId, variantId)!!
            assertEquals(83L, afterBalance.localEstimatedStock)

            // 2. Verify balanced journal entry & lines (Dr == Cr == 71,950)
            // Dr Kas (49,950) + Dr Diskon (3,000) + Dr HPP (19,000) = 71,950
            // Cr Pendapatan (48,000) + Cr PPN (4,950) + Cr Persediaan (19,000) = 71,950
            val journals = db.saleDao().observeRecentJournalEntries(businessId, 10).first()
            assertEquals(1, journals.size)
            val journal = journals.first()
            assertTrue(journal.isBalanced)
            assertEquals(71_950L, journal.totalDebitRp)
            assertEquals(71_950L, journal.totalCreditRp)

            val lines = db.saleDao().getJournalLinesForEntry(businessId, journal.id)
            assertEquals(6, lines.size)
            assertEquals(lines.sumOf { it.debitRp }, lines.sumOf { it.creditRp })

            // 3. Verify COA account balances updated
            val cashAfter = db.saleDao().getAccountByCode(businessId, "1101")!!.currentBalanceRp
            val revenueAfter = db.saleDao().getAccountByCode(businessId, "4101")!!.currentBalanceRp
            val discountAfter = db.saleDao().getAccountByCode(businessId, "4102")!!.currentBalanceRp
            val vatAfter = db.saleDao().getAccountByCode(businessId, "2102")!!.currentBalanceRp
            val cogsAfter = db.saleDao().getAccountByCode(businessId, "5101")!!.currentBalanceRp
            val inventoryAccAfter = db.saleDao().getAccountByCode(businessId, "1104")!!.currentBalanceRp

            assertEquals(cashBefore + 49_950L, cashAfter)
            assertEquals(48_000L, revenueAfter)
            assertEquals(3_000L, discountAfter)
            assertEquals(4_950L, vatAfter)
            assertEquals(19_000L, cogsAfter)
            assertEquals(inventoryAccBefore - 19_000L, inventoryAccAfter)

            // 4. Verify outbox entry queued
            val outboxList = db.syncOutboxDao().getReadyOutboxBatch(businessId, now + 1000L)
            assertEquals(1, outboxList.size)
            assertEquals("sale-create-$saleId", outboxList.first().idempotencyKey)
        } finally {
            db.close()
        }
    }

    @Test
    fun `recordOfflineExpense posts balanced double-entry journal and updates expense and cash accounts`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, NexbizDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            DatabaseModule.ensureSeeded(db)
            val businessId = DatabaseModule.DEFAULT_BUSINESS_ID
            val now = 1761205000000L

            val cashBefore = db.saleDao().getAccountByCode(businessId, "1101")!!.currentBalanceRp
            val expenseBefore = db.saleDao().getAccountByCode(businessId, "6103")!!.currentBalanceRp

            val expense = ExpenseEntity(
                id = "exp-test-001",
                businessId = businessId,
                expenseNumber = "EXP-0001",
                categoryName = "Token Listrik & Internet",
                expenseAccountCode = "6103",
                paidFromAccountCode = "1101",
                amountRp = 450_000L,
                taxRp = 0L,
                paymentMethod = PaymentMethod.CASH,
                vendorName = "PLN & Telkom",
                notes = "Tagihan Bulanan",
                recordedByUserId = DatabaseModule.DEFAULT_OWNER_USER_ID,
                expenseDate = now,
                syncStatus = SyncStatus.PENDING,
                version = 1L,
                createdAt = now,
                updatedAt = now
            )

            db.saleDao().recordOfflineExpense(
                expense = expense,
                outboxPayloadJson = """{"expense_id":"exp-test-001"}"""
            )

            val cashAfter = db.saleDao().getAccountByCode(businessId, "1101")!!.currentBalanceRp
            val expenseAfter = db.saleDao().getAccountByCode(businessId, "6103")!!.currentBalanceRp
            assertEquals(cashBefore - 450_000L, cashAfter)
            assertEquals(expenseBefore + 450_000L, expenseAfter)

            val journals = db.saleDao().observeRecentJournalEntries(businessId, 10).first()
            assertEquals(1, journals.size)
            assertTrue(journals.first().isBalanced)
            assertEquals(450_000L, journals.first().totalDebitRp)
            assertEquals(450_000L, journals.first().totalCreditRp)
        } finally {
            db.close()
        }
    }
}
