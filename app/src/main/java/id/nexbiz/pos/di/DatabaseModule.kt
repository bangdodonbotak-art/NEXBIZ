package id.nexbiz.pos.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import id.nexbiz.pos.data.local.dao.InventoryDao
import id.nexbiz.pos.data.local.dao.ProductDao
import id.nexbiz.pos.data.local.dao.SaleDao
import id.nexbiz.pos.data.local.dao.SyncOutboxDao
import id.nexbiz.pos.data.local.db.NexbizDatabase
import id.nexbiz.pos.data.local.entity.AccountEntity
import id.nexbiz.pos.data.local.entity.BusinessEntity
import id.nexbiz.pos.data.local.entity.CategoryEntity
import id.nexbiz.pos.data.local.entity.InventoryBalanceEntity
import id.nexbiz.pos.data.local.entity.InventoryMovementEntity
import id.nexbiz.pos.data.local.entity.ProductEntity
import id.nexbiz.pos.data.local.entity.ProductVariantEntity
import id.nexbiz.pos.data.local.entity.SupplierEntity
import id.nexbiz.pos.data.local.entity.SyncMetadataEntity
import id.nexbiz.pos.data.local.entity.UserMembershipEntity
import id.nexbiz.pos.data.local.model.AccountType
import id.nexbiz.pos.data.local.model.MovementType
import id.nexbiz.pos.data.local.model.SyncStatus
import id.nexbiz.pos.data.local.model.UserRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object DatabaseModule {

    const val DEFAULT_BUSINESS_ID: String = "b1000000-0000-4000-8000-000000000001"
    const val DEFAULT_OWNER_USER_ID: String = "u1000000-0000-4000-8000-000000000001"
    const val DEFAULT_CASHIER_USER_ID: String = "u1000000-0000-4000-8000-000000000002"

    @Volatile
    private var INSTANCE: NexbizDatabase? = null

    private val databaseScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun provideDatabase(context: Context): NexbizDatabase {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: buildDatabase(context.applicationContext).also { db ->
                INSTANCE = db
                databaseScope.launch {
                    ensureSeeded(db)
                }
            }
        }
    }

    fun provideProductDao(database: NexbizDatabase): ProductDao = database.productDao()

    fun provideSaleDao(database: NexbizDatabase): SaleDao = database.saleDao()

    fun provideInventoryDao(database: NexbizDatabase): InventoryDao = database.inventoryDao()

    fun provideSyncOutboxDao(database: NexbizDatabase): SyncOutboxDao = database.syncOutboxDao()

    private fun buildDatabase(appContext: Context): NexbizDatabase {
        return Room.databaseBuilder(
            appContext,
            NexbizDatabase::class.java,
            NexbizDatabase.DATABASE_NAME
        )
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addCallback(
                object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        db.execSQL("PRAGMA foreign_keys = ON;")
                        db.execSQL("PRAGMA synchronous = NORMAL;")
                    }
                }
            )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    suspend fun ensureSeeded(database: NexbizDatabase) {
        val productDao = database.productDao()
        if (productDao.countBusinesses() > 0) return

        val now = System.currentTimeMillis()
        val businessId = DEFAULT_BUSINESS_ID

        database.withTransaction {
            if (productDao.countBusinesses() > 0) return@withTransaction

            // 1. Default Multi-Tenant Business (Indonesian PKP / UMKM Retail & Roastery)
            val demoBusiness = BusinessEntity(
                id = businessId,
                businessId = businessId,
                code = "NXB-JKT-01",
                name = "NEXBIZ Nusantara Kopi & Mart",
                legalName = "PT Nexbiz Niaga Nusantara",
                taxIdNpwp = "01.234.567.8-012.000",
                address = "Jl. Senopati No. 88, Kebayoran Baru, Jakarta Selatan",
                phone = "+628119008800",
                defaultTaxRateBps = 1100, // 11% PPN
                currencyCode = "IDR",
                isActive = true,
                syncStatus = SyncStatus.SYNCED,
                version = 1L,
                createdAt = now,
                updatedAt = now
            )
            productDao.upsertBusiness(demoBusiness)

            // 2. Tenant Staff Memberships (Owner & Cashier)
            val memberships = listOf(
                UserMembershipEntity(
                    id = "m1000000-0000-4000-8000-000000000001",
                    businessId = businessId,
                    userId = DEFAULT_OWNER_USER_ID,
                    fullName = "Budi Santoso (Owner)",
                    email = "budi@nexbiz.id",
                    role = UserRole.OWNER,
                    pinHash = "888888",
                    maxDiscountRp = 500_000L,
                    isActive = true,
                    syncStatus = SyncStatus.SYNCED,
                    version = 1L,
                    createdAt = now,
                    updatedAt = now
                ),
                UserMembershipEntity(
                    id = "m1000000-0000-4000-8000-000000000002",
                    businessId = businessId,
                    userId = DEFAULT_CASHIER_USER_ID,
                    fullName = "Siti Rahma (Kasir Utama)",
                    email = "siti.kasir@nexbiz.id",
                    role = UserRole.CASHIER,
                    pinHash = "123456",
                    maxDiscountRp = 50_000L,
                    isActive = true,
                    syncStatus = SyncStatus.SYNCED,
                    version = 1L,
                    createdAt = now,
                    updatedAt = now
                )
            )
            productDao.upsertUserMemberships(memberships)

            // 3. Default Indonesian Chart of Accounts (SAK EMKM Standard)
            val defaultAccounts = buildDefaultIndonesianChartOfAccounts(businessId, now)
            database.saleDao().upsertAccounts(defaultAccounts)

            // 4. Default Suppliers
            val suppliers = listOf(
                SupplierEntity(
                    id = "sup-0001-4000-8000-000000000001",
                    businessId = businessId,
                    code = "SUP-GAYO",
                    name = "Koperasi Kopi Gayo Mandiri",
                    contactPerson = "Hendra Wijaya",
                    phone = "+628126001122",
                    email = "order@gayomandiri.co.id",
                    address = "Takengon, Aceh Tengah",
                    payableBalanceRp = 0L,
                    isActive = true,
                    syncStatus = SyncStatus.SYNCED,
                    version = 1L,
                    createdAt = now,
                    updatedAt = now
                ),
                SupplierEntity(
                    id = "sup-0002-4000-8000-000000000002",
                    businessId = businessId,
                    code = "SUP-DAIRY",
                    name = "PT Segar Susu Nusantara",
                    contactPerson = "Dewi Lestari",
                    phone = "+628138009900",
                    email = "sales@segarsusu.co.id",
                    address = "Kawasan Industri Jababeka, Bekasi",
                    payableBalanceRp = 0L,
                    isActive = true,
                    syncStatus = SyncStatus.SYNCED,
                    version = 1L,
                    createdAt = now,
                    updatedAt = now
                )
            )
            database.inventoryDao().upsertSuppliers(suppliers)

            // 5. Categories
            val catCoffeeId = "cat-0001-4000-8000-000000000001"
            val catPastryId = "cat-0002-4000-8000-000000000002"
            val catBeansId = "cat-0003-4000-8000-000000000003"
            val catRtdId = "cat-0004-4000-8000-000000000004"

            val categories = listOf(
                CategoryEntity(
                    id = catCoffeeId,
                    businessId = businessId,
                    name = "Espresso & Kopi Susu",
                    description = "Minuman kopi seduh segar bar",
                    colorHex = "#059669",
                    sortOrder = 1,
                    isActive = true,
                    syncStatus = SyncStatus.SYNCED,
                    version = 1L,
                    createdAt = now,
                    updatedAt = now
                ),
                CategoryEntity(
                    id = catPastryId,
                    businessId = businessId,
                    name = "Artisan Pastry & Bakery",
                    description = "Croissant, danish, dan roti panggang harian",
                    colorHex = "#D97706",
                    sortOrder = 2,
                    isActive = true,
                    syncStatus = SyncStatus.SYNCED,
                    version = 1L,
                    createdAt = now,
                    updatedAt = now
                ),
                CategoryEntity(
                    id = catBeansId,
                    businessId = businessId,
                    name = "Biji Kopi Nusantara",
                    description = "Whole bean & ground pack 250g/1kg",
                    colorHex = "#0284C7",
                    sortOrder = 3,
                    isActive = true,
                    syncStatus = SyncStatus.SYNCED,
                    version = 1L,
                    createdAt = now,
                    updatedAt = now
                ),
                CategoryEntity(
                    id = catRtdId,
                    businessId = businessId,
                    name = "Botol Literan & RTD",
                    description = "Kemasan botol siap minum",
                    colorHex = "#7C3AED",
                    sortOrder = 4,
                    isActive = true,
                    syncStatus = SyncStatus.SYNCED,
                    version = 1L,
                    createdAt = now,
                    updatedAt = now
                )
            )
            productDao.upsertCategories(categories)

            // 6. Products & Product Variants (Whole IDR Long Amounts)
            val prodArenId = "prd-0001-4000-8000-000000000001"
            val prodAmericanoId = "prd-0002-4000-8000-000000000002"
            val prodCroissantId = "prd-0003-4000-8000-000000000003"
            val prodGayoBeansId = "prd-0004-4000-8000-000000000004"
            val prodLiteranId = "prd-0005-4000-8000-000000000005"

            val products = listOf(
                ProductEntity(
                    id = prodArenId,
                    businessId = businessId,
                    categoryId = catCoffeeId,
                    sku = "KOP-AREN",
                    name = "Es Kopi Susu Gula Aren",
                    description = "Signature house blend arabica dengan gula aren organik",
                    unitName = "cup",
                    basePriceRp = 24_000L,
                    baseCostRp = 9_500L,
                    isTaxable = true,
                    trackInventory = true,
                    isActive = true,
                    syncStatus = SyncStatus.SYNCED,
                    version = 1L,
                    createdAt = now,
                    updatedAt = now
                ),
                ProductEntity(
                    id = prodAmericanoId,
                    businessId = businessId,
                    categoryId = catCoffeeId,
                    sku = "KOP-AMER",
                    name = "Iced Long Black Arabica",
                    description = "Double shot 100% Arabica Aceh Gayo",
                    unitName = "cup",
                    basePriceRp = 22_000L,
                    baseCostRp = 7_000L,
                    isTaxable = true,
                    trackInventory = true,
                    isActive = true,
                    syncStatus = SyncStatus.SYNCED,
                    version = 1L,
                    createdAt = now,
                    updatedAt = now
                ),
                ProductEntity(
                    id = prodCroissantId,
                    businessId = businessId,
                    categoryId = catPastryId,
                    sku = "PAS-CROIS",
                    name = "French Butter Croissant",
                    description = "Laminated pastry dengan French Elle & Vire butter",
                    unitName = "pcs",
                    basePriceRp = 28_000L,
                    baseCostRp = 12_000L,
                    isTaxable = true,
                    trackInventory = true,
                    isActive = true,
                    syncStatus = SyncStatus.SYNCED,
                    version = 1L,
                    createdAt = now,
                    updatedAt = now
                ),
                ProductEntity(
                    id = prodGayoBeansId,
                    businessId = businessId,
                    categoryId = catBeansId,
                    sku = "BEAN-GAYO",
                    name = "Arabica Aceh Gayo Wine Process",
                    description = "Roast profile Medium, kemasan valve bag",
                    unitName = "pack",
                    basePriceRp = 95_000L,
                    baseCostRp = 58_000L,
                    isTaxable = true,
                    trackInventory = true,
                    isActive = true,
                    syncStatus = SyncStatus.SYNCED,
                    version = 1L,
                    createdAt = now,
                    updatedAt = now
                ),
                ProductEntity(
                    id = prodLiteranId,
                    businessId = businessId,
                    categoryId = catRtdId,
                    sku = "RTD-1000",
                    name = "Kopi Susu Nusantara 1 Liter",
                    description = "Botol segel higienis 1000ml untuk keluarga",
                    unitName = "btl",
                    basePriceRp = 85_000L,
                    baseCostRp = 38_000L,
                    isTaxable = true,
                    trackInventory = true,
                    isActive = true,
                    syncStatus = SyncStatus.SYNCED,
                    version = 1L,
                    createdAt = now,
                    updatedAt = now
                )
            )
            productDao.upsertProducts(products)

            val variants = listOf(
                ProductVariantEntity(
                    id = "var-0001-4000-8000-000000000001",
                    businessId = businessId,
                    productId = prodArenId,
                    variantName = "Regular 12oz",
                    sku = "KOP-AREN-REG",
                    barcode = "8991002000011",
                    sellingPriceRp = 24_000L,
                    averageCostRp = 9_500L,
                    minStockThreshold = 15L,
                    isActive = true,
                    syncStatus = SyncStatus.SYNCED,
                    version = 1L,
                    createdAt = now,
                    updatedAt = now
                ),
                ProductVariantEntity(
                    id = "var-0002-4000-8000-000000000002",
                    businessId = businessId,
                    productId = prodArenId,
                    variantName = "Large 16oz",
                    sku = "KOP-AREN-LRG",
                    barcode = "8991002000028",
                    sellingPriceRp = 29_000L,
                    averageCostRp = 11_500L,
                    minStockThreshold = 15L,
                    isActive = true,
                    syncStatus = SyncStatus.SYNCED,
                    version = 1L,
                    createdAt = now,
                    updatedAt = now
                ),
                ProductVariantEntity(
                    id = "var-0003-4000-8000-000000000003",
                    businessId = businessId,
                    productId = prodAmericanoId,
                    variantName = "Iced Regular",
                    sku = "KOP-AMER-ICE",
                    barcode = "8991002000035",
                    sellingPriceRp = 22_000L,
                    averageCostRp = 7_000L,
                    minStockThreshold = 15L,
                    isActive = true,
                    syncStatus = SyncStatus.SYNCED,
                    version = 1L,
                    createdAt = now,
                    updatedAt = now
                ),
                ProductVariantEntity(
                    id = "var-0004-4000-8000-000000000004",
                    businessId = businessId,
                    productId = prodCroissantId,
                    variantName = "Original Butter",
                    sku = "PAS-CROIS-ORI",
                    barcode = "8991002000042",
                    sellingPriceRp = 28_000L,
                    averageCostRp = 12_000L,
                    minStockThreshold = 10L,
                    isActive = true,
                    syncStatus = SyncStatus.SYNCED,
                    version = 1L,
                    createdAt = now,
                    updatedAt = now
                ),
                ProductVariantEntity(
                    id = "var-0005-4000-8000-000000000005",
                    businessId = businessId,
                    productId = prodCroissantId,
                    variantName = "Almond Pain au Chocolat",
                    sku = "PAS-CROIS-ALM",
                    barcode = "8991002000059",
                    sellingPriceRp = 34_000L,
                    averageCostRp = 15_000L,
                    minStockThreshold = 8L,
                    isActive = true,
                    syncStatus = SyncStatus.SYNCED,
                    version = 1L,
                    createdAt = now,
                    updatedAt = now
                ),
                ProductVariantEntity(
                    id = "var-0006-4000-8000-000000000006",
                    businessId = businessId,
                    productId = prodGayoBeansId,
                    variantName = "Whole Bean 250g",
                    sku = "BEAN-GAYO-250",
                    barcode = "8991002000066",
                    sellingPriceRp = 95_000L,
                    averageCostRp = 58_000L,
                    minStockThreshold = 5L,
                    isActive = true,
                    syncStatus = SyncStatus.SYNCED,
                    version = 1L,
                    createdAt = now,
                    updatedAt = now
                ),
                ProductVariantEntity(
                    id = "var-0007-4000-8000-000000000007",
                    businessId = businessId,
                    productId = prodLiteranId,
                    variantName = "Botol 1 Liter",
                    sku = "RTD-1000-REG",
                    barcode = "8991002000073",
                    sellingPriceRp = 85_000L,
                    averageCostRp = 38_000L,
                    minStockThreshold = 6L,
                    isActive = true,
                    syncStatus = SyncStatus.SYNCED,
                    version = 1L,
                    createdAt = now,
                    updatedAt = now
                )
            )
            productDao.upsertVariants(variants)

            // 7. Initial Inventory Balances & Movements (localEstimatedStock & serverConfirmedStock)
            val initialStockMap = mapOf(
                "var-0001-4000-8000-000000000001" to 85L,
                "var-0002-4000-8000-000000000002" to 60L,
                "var-0003-4000-8000-000000000003" to 90L,
                "var-0004-4000-8000-000000000004" to 24L,
                "var-0005-4000-8000-000000000005" to 6L, // Low stock alert candidate (<= 8)
                "var-0006-4000-8000-000000000006" to 18L,
                "var-0007-4000-8000-000000000007" to 14L
            )

            val balances = variants.mapIndexed { index, variant ->
                val stock = initialStockMap[variant.id] ?: 20L
                InventoryBalanceEntity(
                    id = "bal-000${index + 1}-4000-8000-00000000000${index + 1}",
                    businessId = businessId,
                    variantId = variant.id,
                    localEstimatedStock = stock,
                    serverConfirmedStock = stock,
                    reservedStock = 0L,
                    valuationCostRp = stock * variant.averageCostRp,
                    syncStatus = SyncStatus.SYNCED,
                    lastReconciledAt = now,
                    version = 1L,
                    updatedAt = now
                )
            }
            database.inventoryDao().upsertBalances(balances)

            val initialMovements = variants.mapIndexed { index, variant ->
                val stock = initialStockMap[variant.id] ?: 20L
                InventoryMovementEntity(
                    id = "mov-000${index + 1}-4000-8000-00000000000${index + 1}",
                    businessId = businessId,
                    variantId = variant.id,
                    movementType = MovementType.INITIAL_STOCK,
                    quantityDelta = stock,
                    stockBefore = 0L,
                    stockAfter = stock,
                    unitCostRp = variant.averageCostRp,
                    referenceId = "INIT-SEED-001",
                    referenceType = "INITIAL_SEED",
                    notes = "Saldo awal persediaan toko",
                    createdByUserId = DEFAULT_OWNER_USER_ID,
                    syncStatus = SyncStatus.SYNCED,
                    createdAt = now
                )
            }
            database.inventoryDao().insertMovements(initialMovements)

            // 8. Sync Metadata Cursor Initialization
            database.syncOutboxDao().upsertSyncMetadata(
                SyncMetadataEntity(
                    id = "meta-0001-4000-8000-000000000001",
                    businessId = businessId,
                    resourceName = "ALL_POS_RESOURCES",
                    lastPullCursor = "cursor_init_v1",
                    lastPullEpochMs = now,
                    lastPushEpochMs = now,
                    syncStatus = SyncStatus.SYNCED,
                    updatedAt = now
                )
            )
        }
    }

    fun buildDefaultIndonesianChartOfAccounts(
        businessId: String,
        nowEpochMs: Long
    ): List<AccountEntity> {
        return listOf(
            AccountEntity(
                id = "acc-1101-$businessId",
                businessId = businessId,
                code = "1101",
                name = "Kas Tunai Kasir (Laci POS)",
                accountType = AccountType.ASSET,
                normalDebit = true,
                currentBalanceRp = 5_000_000L,
                isSystemAccount = true,
                isActive = true,
                syncStatus = SyncStatus.SYNCED,
                version = 1L,
                createdAt = nowEpochMs,
                updatedAt = nowEpochMs
            ),
            AccountEntity(
                id = "acc-1102-$businessId",
                businessId = businessId,
                code = "1102",
                name = "Bank BCA & Settlement QRIS / EDC",
                accountType = AccountType.ASSET,
                normalDebit = true,
                currentBalanceRp = 45_000_000L,
                isSystemAccount = true,
                isActive = true,
                syncStatus = SyncStatus.SYNCED,
                version = 1L,
                createdAt = nowEpochMs,
                updatedAt = nowEpochMs
            ),
            AccountEntity(
                id = "acc-1103-$businessId",
                businessId = businessId,
                code = "1103",
                name = "Piutang Usaha Pelanggan (Tempo)",
                accountType = AccountType.ASSET,
                normalDebit = true,
                currentBalanceRp = 0L,
                isSystemAccount = true,
                isActive = true,
                syncStatus = SyncStatus.SYNCED,
                version = 1L,
                createdAt = nowEpochMs,
                updatedAt = nowEpochMs
            ),
            AccountEntity(
                id = "acc-1104-$businessId",
                businessId = businessId,
                code = "1104",
                name = "Persediaan Barang Dagang",
                accountType = AccountType.ASSET,
                normalDebit = true,
                currentBalanceRp = 4_066_500L,
                isSystemAccount = true,
                isActive = true,
                syncStatus = SyncStatus.SYNCED,
                version = 1L,
                createdAt = nowEpochMs,
                updatedAt = nowEpochMs
            ),
            AccountEntity(
                id = "acc-2101-$businessId",
                businessId = businessId,
                code = "2101",
                name = "Utang Usaha Supplier",
                accountType = AccountType.LIABILITY,
                normalDebit = false,
                currentBalanceRp = 0L,
                isSystemAccount = true,
                isActive = true,
                syncStatus = SyncStatus.SYNCED,
                version = 1L,
                createdAt = nowEpochMs,
                updatedAt = nowEpochMs
            ),
            AccountEntity(
                id = "acc-2102-$businessId",
                businessId = businessId,
                code = "2102",
                name = "Utang PPN Keluaran (11%)",
                accountType = AccountType.LIABILITY,
                normalDebit = false,
                currentBalanceRp = 0L,
                isSystemAccount = true,
                isActive = true,
                syncStatus = SyncStatus.SYNCED,
                version = 1L,
                createdAt = nowEpochMs,
                updatedAt = nowEpochMs
            ),
            AccountEntity(
                id = "acc-3101-$businessId",
                businessId = businessId,
                code = "3101",
                name = "Modal Disetor Pemilik",
                accountType = AccountType.EQUITY,
                normalDebit = false,
                currentBalanceRp = 54_066_500L,
                isSystemAccount = true,
                isActive = true,
                syncStatus = SyncStatus.SYNCED,
                version = 1L,
                createdAt = nowEpochMs,
                updatedAt = nowEpochMs
            ),
            AccountEntity(
                id = "acc-3102-$businessId",
                businessId = businessId,
                code = "3102",
                name = "Laba Ditahan",
                accountType = AccountType.EQUITY,
                normalDebit = false,
                currentBalanceRp = 0L,
                isSystemAccount = true,
                isActive = true,
                syncStatus = SyncStatus.SYNCED,
                version = 1L,
                createdAt = nowEpochMs,
                updatedAt = nowEpochMs
            ),
            AccountEntity(
                id = "acc-4101-$businessId",
                businessId = businessId,
                code = "4101",
                name = "Pendapatan Penjualan POS",
                accountType = AccountType.REVENUE,
                normalDebit = false,
                currentBalanceRp = 0L,
                isSystemAccount = true,
                isActive = true,
                syncStatus = SyncStatus.SYNCED,
                version = 1L,
                createdAt = nowEpochMs,
                updatedAt = nowEpochMs
            ),
            AccountEntity(
                id = "acc-4102-$businessId",
                businessId = businessId,
                code = "4102",
                name = "Potongan / Diskon Penjualan",
                accountType = AccountType.REVENUE,
                normalDebit = true,
                currentBalanceRp = 0L,
                isSystemAccount = true,
                isActive = true,
                syncStatus = SyncStatus.SYNCED,
                version = 1L,
                createdAt = nowEpochMs,
                updatedAt = nowEpochMs
            ),
            AccountEntity(
                id = "acc-5101-$businessId",
                businessId = businessId,
                code = "5101",
                name = "Harga Pokok Penjualan (HPP)",
                accountType = AccountType.COST_OF_GOODS_SOLD,
                normalDebit = true,
                currentBalanceRp = 0L,
                isSystemAccount = true,
                isActive = true,
                syncStatus = SyncStatus.SYNCED,
                version = 1L,
                createdAt = nowEpochMs,
                updatedAt = nowEpochMs
            ),
            AccountEntity(
                id = "acc-6101-$businessId",
                businessId = businessId,
                code = "6101",
                name = "Beban Operasional Toko & Kemasan",
                accountType = AccountType.EXPENSE,
                normalDebit = true,
                currentBalanceRp = 0L,
                isSystemAccount = true,
                isActive = true,
                syncStatus = SyncStatus.SYNCED,
                version = 1L,
                createdAt = nowEpochMs,
                updatedAt = nowEpochMs
            ),
            AccountEntity(
                id = "acc-6102-$businessId",
                businessId = businessId,
                code = "6102",
                name = "Beban Gaji & Insentif Barista/Kasir",
                accountType = AccountType.EXPENSE,
                normalDebit = true,
                currentBalanceRp = 0L,
                isSystemAccount = false,
                isActive = true,
                syncStatus = SyncStatus.SYNCED,
                version = 1L,
                createdAt = nowEpochMs,
                updatedAt = nowEpochMs
            ),
            AccountEntity(
                id = "acc-6103-$businessId",
                businessId = businessId,
                code = "6103",
                name = "Beban Listrik, Air & Internet",
                accountType = AccountType.EXPENSE,
                normalDebit = true,
                currentBalanceRp = 0L,
                isSystemAccount = false,
                isActive = true,
                syncStatus = SyncStatus.SYNCED,
                version = 1L,
                createdAt = nowEpochMs,
                updatedAt = nowEpochMs
            )
        )
    }
}
