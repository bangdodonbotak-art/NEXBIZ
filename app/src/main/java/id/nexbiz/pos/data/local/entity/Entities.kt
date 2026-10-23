package id.nexbiz.pos.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import id.nexbiz.pos.data.local.model.AccountType
import id.nexbiz.pos.data.local.model.MovementType
import id.nexbiz.pos.data.local.model.OutboxOperation
import id.nexbiz.pos.data.local.model.PaymentMethod
import id.nexbiz.pos.data.local.model.SyncStatus
import id.nexbiz.pos.data.local.model.UserRole

@Entity(
    tableName = "businesses",
    indices = [
        Index(value = ["business_id"], unique = true),
        Index(value = ["code"], unique = true),
        Index(value = ["sync_status"])
    ]
)
data class BusinessEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "business_id")
    val businessId: String,
    @ColumnInfo(name = "code")
    val code: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "legal_name")
    val legalName: String?,
    @ColumnInfo(name = "tax_id_npwp")
    val taxIdNpwp: String?,
    @ColumnInfo(name = "address")
    val address: String,
    @ColumnInfo(name = "phone")
    val phone: String,
    @ColumnInfo(name = "default_tax_rate_bps")
    val defaultTaxRateBps: Int = 1100, // 11.00% PPN in basis points
    @ColumnInfo(name = "currency_code")
    val currencyCode: String = "IDR",
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
    @ColumnInfo(name = "version")
    val version: Long = 1L,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

@Entity(
    tableName = "user_memberships",
    foreignKeys = [
        ForeignKey(
            entity = BusinessEntity::class,
            parentColumns = ["id"],
            childColumns = ["business_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["business_id"]),
        Index(value = ["business_id", "user_id"], unique = true),
        Index(value = ["business_id", "pin_hash"]),
        Index(value = ["sync_status"])
    ]
)
data class UserMembershipEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "business_id")
    val businessId: String,
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "full_name")
    val fullName: String,
    @ColumnInfo(name = "email")
    val email: String,
    @ColumnInfo(name = "role")
    val role: UserRole,
    @ColumnInfo(name = "pin_hash")
    val pinHash: String,
    @ColumnInfo(name = "max_discount_rp")
    val maxDiscountRp: Long = 0L,
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
    @ColumnInfo(name = "version")
    val version: Long = 1L,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = BusinessEntity::class,
            parentColumns = ["id"],
            childColumns = ["business_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["business_id"]),
        Index(value = ["business_id", "name"], unique = true),
        Index(value = ["sync_status"])
    ]
)
data class CategoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "business_id")
    val businessId: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "description")
    val description: String?,
    @ColumnInfo(name = "color_hex")
    val colorHex: String = "#059669",
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
    @ColumnInfo(name = "version")
    val version: Long = 1L,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

@Entity(
    tableName = "products",
    foreignKeys = [
        ForeignKey(
            entity = BusinessEntity::class,
            parentColumns = ["id"],
            childColumns = ["business_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["business_id"]),
        Index(value = ["category_id"]),
        Index(value = ["business_id", "sku"], unique = true),
        Index(value = ["business_id", "name"]),
        Index(value = ["sync_status"])
    ]
)
data class ProductEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "business_id")
    val businessId: String,
    @ColumnInfo(name = "category_id")
    val categoryId: String?,
    @ColumnInfo(name = "sku")
    val sku: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "description")
    val description: String?,
    @ColumnInfo(name = "unit_name")
    val unitName: String = "pcs",
    @ColumnInfo(name = "base_price_rp")
    val basePriceRp: Long,
    @ColumnInfo(name = "base_cost_rp")
    val baseCostRp: Long,
    @ColumnInfo(name = "is_taxable")
    val isTaxable: Boolean = true,
    @ColumnInfo(name = "track_inventory")
    val trackInventory: Boolean = true,
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
    @ColumnInfo(name = "version")
    val version: Long = 1L,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

@Entity(
    tableName = "product_variants",
    foreignKeys = [
        ForeignKey(
            entity = BusinessEntity::class,
            parentColumns = ["id"],
            childColumns = ["business_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["business_id"]),
        Index(value = ["product_id"]),
        Index(value = ["business_id", "sku"], unique = true),
        Index(value = ["business_id", "barcode"]),
        Index(value = ["sync_status"])
    ]
)
data class ProductVariantEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "business_id")
    val businessId: String,
    @ColumnInfo(name = "product_id")
    val productId: String,
    @ColumnInfo(name = "variant_name")
    val variantName: String,
    @ColumnInfo(name = "sku")
    val sku: String,
    @ColumnInfo(name = "barcode")
    val barcode: String?,
    @ColumnInfo(name = "selling_price_rp")
    val sellingPriceRp: Long,
    @ColumnInfo(name = "average_cost_rp")
    val averageCostRp: Long,
    @ColumnInfo(name = "min_stock_threshold")
    val minStockThreshold: Long = 5L,
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
    @ColumnInfo(name = "version")
    val version: Long = 1L,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

@Entity(
    tableName = "inventory_balances",
    foreignKeys = [
        ForeignKey(
            entity = BusinessEntity::class,
            parentColumns = ["id"],
            childColumns = ["business_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProductVariantEntity::class,
            parentColumns = ["id"],
            childColumns = ["variant_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["business_id"]),
        Index(value = ["business_id", "variant_id"], unique = true),
        Index(value = ["variant_id"]),
        Index(value = ["sync_status"])
    ]
)
data class InventoryBalanceEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "business_id")
    val businessId: String,
    @ColumnInfo(name = "variant_id")
    val variantId: String,
    @ColumnInfo(name = "local_estimated_stock")
    val localEstimatedStock: Long,
    @ColumnInfo(name = "server_confirmed_stock")
    val serverConfirmedStock: Long,
    @ColumnInfo(name = "reserved_stock")
    val reservedStock: Long = 0L,
    @ColumnInfo(name = "valuation_cost_rp")
    val valuationCostRp: Long = 0L,
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
    @ColumnInfo(name = "last_reconciled_at")
    val lastReconciledAt: Long?,
    @ColumnInfo(name = "version")
    val version: Long = 1L,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

@Entity(
    tableName = "inventory_movements",
    foreignKeys = [
        ForeignKey(
            entity = BusinessEntity::class,
            parentColumns = ["id"],
            childColumns = ["business_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProductVariantEntity::class,
            parentColumns = ["id"],
            childColumns = ["variant_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["business_id"]),
        Index(value = ["variant_id"]),
        Index(value = ["business_id", "reference_id"]),
        Index(value = ["business_id", "created_at"]),
        Index(value = ["sync_status"])
    ]
)
data class InventoryMovementEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "business_id")
    val businessId: String,
    @ColumnInfo(name = "variant_id")
    val variantId: String,
    @ColumnInfo(name = "movement_type")
    val movementType: MovementType,
    @ColumnInfo(name = "quantity_delta")
    val quantityDelta: Long,
    @ColumnInfo(name = "stock_before")
    val stockBefore: Long,
    @ColumnInfo(name = "stock_after")
    val stockAfter: Long,
    @ColumnInfo(name = "unit_cost_rp")
    val unitCostRp: Long,
    @ColumnInfo(name = "reference_id")
    val referenceId: String?,
    @ColumnInfo(name = "reference_type")
    val referenceType: String?,
    @ColumnInfo(name = "notes")
    val notes: String?,
    @ColumnInfo(name = "created_by_user_id")
    val createdByUserId: String,
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)

@Entity(
    tableName = "sales",
    foreignKeys = [
        ForeignKey(
            entity = BusinessEntity::class,
            parentColumns = ["id"],
            childColumns = ["business_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["business_id"]),
        Index(value = ["business_id", "receipt_number"], unique = true),
        Index(value = ["business_id", "created_at"]),
        Index(value = ["cashier_user_id"]),
        Index(value = ["sync_status"])
    ]
)
data class SaleEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "business_id")
    val businessId: String,
    @ColumnInfo(name = "receipt_number")
    val receiptNumber: String,
    @ColumnInfo(name = "cashier_user_id")
    val cashierUserId: String,
    @ColumnInfo(name = "customer_name")
    val customerName: String?,
    @ColumnInfo(name = "subtotal_rp")
    val subtotalRp: Long,
    @ColumnInfo(name = "discount_total_rp")
    val discountTotalRp: Long = 0L,
    @ColumnInfo(name = "tax_total_rp")
    val taxTotalRp: Long = 0L,
    @ColumnInfo(name = "grand_total_rp")
    val grandTotalRp: Long,
    @ColumnInfo(name = "total_paid_rp")
    val totalPaidRp: Long,
    @ColumnInfo(name = "change_due_rp")
    val changeDueRp: Long = 0L,
    @ColumnInfo(name = "total_cogs_rp")
    val totalCogsRp: Long = 0L,
    @ColumnInfo(name = "payment_method")
    val paymentMethod: PaymentMethod,
    @ColumnInfo(name = "is_voided")
    val isVoided: Boolean = false,
    @ColumnInfo(name = "void_reason")
    val voidReason: String?,
    @ColumnInfo(name = "voided_at")
    val voidedAt: Long?,
    @ColumnInfo(name = "notes")
    val notes: String?,
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    @ColumnInfo(name = "version")
    val version: Long = 1L,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

@Entity(
    tableName = "sale_items",
    foreignKeys = [
        ForeignKey(
            entity = BusinessEntity::class,
            parentColumns = ["id"],
            childColumns = ["business_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SaleEntity::class,
            parentColumns = ["id"],
            childColumns = ["sale_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProductVariantEntity::class,
            parentColumns = ["id"],
            childColumns = ["variant_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["business_id"]),
        Index(value = ["sale_id"]),
        Index(value = ["variant_id"]),
        Index(value = ["sync_status"])
    ]
)
data class SaleItemEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "business_id")
    val businessId: String,
    @ColumnInfo(name = "sale_id")
    val saleId: String,
    @ColumnInfo(name = "variant_id")
    val variantId: String,
    @ColumnInfo(name = "product_name_snapshot")
    val productNameSnapshot: String,
    @ColumnInfo(name = "variant_name_snapshot")
    val variantNameSnapshot: String,
    @ColumnInfo(name = "sku_snapshot")
    val skuSnapshot: String,
    @ColumnInfo(name = "quantity")
    val quantity: Long,
    @ColumnInfo(name = "unit_price_rp")
    val unitPriceRp: Long,
    @ColumnInfo(name = "unit_cost_rp")
    val unitCostRp: Long,
    @ColumnInfo(name = "discount_rp")
    val discountRp: Long = 0L,
    @ColumnInfo(name = "tax_rp")
    val taxRp: Long = 0L,
    @ColumnInfo(name = "line_total_rp")
    val lineTotalRp: Long,
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)

@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = BusinessEntity::class,
            parentColumns = ["id"],
            childColumns = ["business_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SaleEntity::class,
            parentColumns = ["id"],
            childColumns = ["sale_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["business_id"]),
        Index(value = ["sale_id"]),
        Index(value = ["business_id", "payment_method"]),
        Index(value = ["sync_status"])
    ]
)
data class PaymentEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "business_id")
    val businessId: String,
    @ColumnInfo(name = "sale_id")
    val saleId: String,
    @ColumnInfo(name = "payment_method")
    val paymentMethod: PaymentMethod,
    @ColumnInfo(name = "amount_rp")
    val amountRp: Long,
    @ColumnInfo(name = "tendered_rp")
    val tenderedRp: Long,
    @ColumnInfo(name = "change_rp")
    val changeRp: Long = 0L,
    @ColumnInfo(name = "reference_code")
    val referenceCode: String?,
    @ColumnInfo(name = "paid_at")
    val paidAt: Long,
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)

@Entity(
    tableName = "suppliers",
    foreignKeys = [
        ForeignKey(
            entity = BusinessEntity::class,
            parentColumns = ["id"],
            childColumns = ["business_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["business_id"]),
        Index(value = ["business_id", "code"], unique = true),
        Index(value = ["sync_status"])
    ]
)
data class SupplierEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "business_id")
    val businessId: String,
    @ColumnInfo(name = "code")
    val code: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "contact_person")
    val contactPerson: String?,
    @ColumnInfo(name = "phone")
    val phone: String?,
    @ColumnInfo(name = "email")
    val email: String?,
    @ColumnInfo(name = "address")
    val address: String?,
    @ColumnInfo(name = "payable_balance_rp")
    val payableBalanceRp: Long = 0L,
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
    @ColumnInfo(name = "version")
    val version: Long = 1L,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

@Entity(
    tableName = "purchases",
    foreignKeys = [
        ForeignKey(
            entity = BusinessEntity::class,
            parentColumns = ["id"],
            childColumns = ["business_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SupplierEntity::class,
            parentColumns = ["id"],
            childColumns = ["supplier_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["business_id"]),
        Index(value = ["supplier_id"]),
        Index(value = ["business_id", "po_number"], unique = true),
        Index(value = ["sync_status"])
    ]
)
data class PurchaseEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "business_id")
    val businessId: String,
    @ColumnInfo(name = "supplier_id")
    val supplierId: String,
    @ColumnInfo(name = "po_number")
    val poNumber: String,
    @ColumnInfo(name = "invoice_number")
    val invoiceNumber: String?,
    @ColumnInfo(name = "subtotal_rp")
    val subtotalRp: Long,
    @ColumnInfo(name = "discount_rp")
    val discountRp: Long = 0L,
    @ColumnInfo(name = "tax_rp")
    val taxRp: Long = 0L,
    @ColumnInfo(name = "shipping_cost_rp")
    val shippingCostRp: Long = 0L,
    @ColumnInfo(name = "grand_total_rp")
    val grandTotalRp: Long,
    @ColumnInfo(name = "paid_amount_rp")
    val paidAmountRp: Long = 0L,
    @ColumnInfo(name = "payment_method")
    val paymentMethod: PaymentMethod,
    @ColumnInfo(name = "received_by_user_id")
    val receivedByUserId: String,
    @ColumnInfo(name = "notes")
    val notes: String?,
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    @ColumnInfo(name = "version")
    val version: Long = 1L,
    @ColumnInfo(name = "purchased_at")
    val purchasedAt: Long,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

@Entity(
    tableName = "purchase_items",
    foreignKeys = [
        ForeignKey(
            entity = BusinessEntity::class,
            parentColumns = ["id"],
            childColumns = ["business_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PurchaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["purchase_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProductVariantEntity::class,
            parentColumns = ["id"],
            childColumns = ["variant_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["business_id"]),
        Index(value = ["purchase_id"]),
        Index(value = ["variant_id"]),
        Index(value = ["sync_status"])
    ]
)
data class PurchaseItemEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "business_id")
    val businessId: String,
    @ColumnInfo(name = "purchase_id")
    val purchaseId: String,
    @ColumnInfo(name = "variant_id")
    val variantId: String,
    @ColumnInfo(name = "quantity_received")
    val quantityReceived: Long,
    @ColumnInfo(name = "unit_cost_rp")
    val unitCostRp: Long,
    @ColumnInfo(name = "discount_rp")
    val discountRp: Long = 0L,
    @ColumnInfo(name = "line_total_rp")
    val lineTotalRp: Long,
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)

@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = BusinessEntity::class,
            parentColumns = ["id"],
            childColumns = ["business_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["business_id"]),
        Index(value = ["business_id", "expense_date"]),
        Index(value = ["expense_account_code"]),
        Index(value = ["sync_status"])
    ]
)
data class ExpenseEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "business_id")
    val businessId: String,
    @ColumnInfo(name = "expense_number")
    val expenseNumber: String,
    @ColumnInfo(name = "category_name")
    val categoryName: String,
    @ColumnInfo(name = "expense_account_code")
    val expenseAccountCode: String,
    @ColumnInfo(name = "paid_from_account_code")
    val paidFromAccountCode: String,
    @ColumnInfo(name = "amount_rp")
    val amountRp: Long,
    @ColumnInfo(name = "tax_rp")
    val taxRp: Long = 0L,
    @ColumnInfo(name = "payment_method")
    val paymentMethod: PaymentMethod,
    @ColumnInfo(name = "vendor_name")
    val vendorName: String?,
    @ColumnInfo(name = "notes")
    val notes: String?,
    @ColumnInfo(name = "recorded_by_user_id")
    val recordedByUserId: String,
    @ColumnInfo(name = "expense_date")
    val expenseDate: Long,
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    @ColumnInfo(name = "version")
    val version: Long = 1L,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

@Entity(
    tableName = "cash_movements",
    foreignKeys = [
        ForeignKey(
            entity = BusinessEntity::class,
            parentColumns = ["id"],
            childColumns = ["business_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["business_id"]),
        Index(value = ["business_id", "shift_id"]),
        Index(value = ["business_id", "created_at"]),
        Index(value = ["sync_status"])
    ]
)
data class CashMovementEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "business_id")
    val businessId: String,
    @ColumnInfo(name = "shift_id")
    val shiftId: String?,
    @ColumnInfo(name = "cash_account_code")
    val cashAccountCode: String,
    @ColumnInfo(name = "is_cash_in")
    val isCashIn: Boolean,
    @ColumnInfo(name = "amount_rp")
    val amountRp: Long,
    @ColumnInfo(name = "reason")
    val reason: String,
    @ColumnInfo(name = "reference_id")
    val referenceId: String?,
    @ColumnInfo(name = "performed_by_user_id")
    val performedByUserId: String,
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)

@Entity(
    tableName = "accounts",
    foreignKeys = [
        ForeignKey(
            entity = BusinessEntity::class,
            parentColumns = ["id"],
            childColumns = ["business_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["business_id"]),
        Index(value = ["business_id", "code"], unique = true),
        Index(value = ["business_id", "account_type"]),
        Index(value = ["sync_status"])
    ]
)
data class AccountEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "business_id")
    val businessId: String,
    @ColumnInfo(name = "code")
    val code: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "account_type")
    val accountType: AccountType,
    @ColumnInfo(name = "normal_debit")
    val normalDebit: Boolean,
    @ColumnInfo(name = "current_balance_rp")
    val currentBalanceRp: Long = 0L,
    @ColumnInfo(name = "is_system_account")
    val isSystemAccount: Boolean = false,
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
    @ColumnInfo(name = "version")
    val version: Long = 1L,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

@Entity(
    tableName = "journal_entries",
    foreignKeys = [
        ForeignKey(
            entity = BusinessEntity::class,
            parentColumns = ["id"],
            childColumns = ["business_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["business_id"]),
        Index(value = ["business_id", "entry_number"], unique = true),
        Index(value = ["business_id", "reference_id"]),
        Index(value = ["business_id", "entry_date"]),
        Index(value = ["sync_status"])
    ]
)
data class JournalEntryEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "business_id")
    val businessId: String,
    @ColumnInfo(name = "entry_number")
    val entryNumber: String,
    @ColumnInfo(name = "reference_type")
    val referenceType: String,
    @ColumnInfo(name = "reference_id")
    val referenceId: String,
    @ColumnInfo(name = "description")
    val description: String,
    @ColumnInfo(name = "total_debit_rp")
    val totalDebitRp: Long,
    @ColumnInfo(name = "total_credit_rp")
    val totalCreditRp: Long,
    @ColumnInfo(name = "is_balanced")
    val isBalanced: Boolean = true,
    @ColumnInfo(name = "posted_by_user_id")
    val postedByUserId: String,
    @ColumnInfo(name = "entry_date")
    val entryDate: Long,
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)

@Entity(
    tableName = "journal_lines",
    foreignKeys = [
        ForeignKey(
            entity = BusinessEntity::class,
            parentColumns = ["id"],
            childColumns = ["business_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = JournalEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["journal_entry_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["business_id"]),
        Index(value = ["journal_entry_id"]),
        Index(value = ["account_id"]),
        Index(value = ["sync_status"])
    ]
)
data class JournalLineEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "business_id")
    val businessId: String,
    @ColumnInfo(name = "journal_entry_id")
    val journalEntryId: String,
    @ColumnInfo(name = "account_id")
    val accountId: String,
    @ColumnInfo(name = "account_code")
    val accountCode: String,
    @ColumnInfo(name = "debit_rp")
    val debitRp: Long = 0L,
    @ColumnInfo(name = "credit_rp")
    val creditRp: Long = 0L,
    @ColumnInfo(name = "memo")
    val memo: String?,
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)

@Entity(
    tableName = "sync_outbox",
    indices = [
        Index(value = ["business_id"]),
        Index(value = ["business_id", "sync_status", "created_at"]),
        Index(value = ["idempotency_key"], unique = true),
        Index(value = ["entity_type", "entity_id"])
    ]
)
data class SyncOutboxEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "business_id")
    val businessId: String,
    @ColumnInfo(name = "idempotency_key")
    val idempotencyKey: String,
    @ColumnInfo(name = "operation")
    val operation: OutboxOperation,
    @ColumnInfo(name = "entity_type")
    val entityType: String,
    @ColumnInfo(name = "entity_id")
    val entityId: String,
    @ColumnInfo(name = "payload_json")
    val payloadJson: String,
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,
    @ColumnInfo(name = "max_retries")
    val maxRetries: Int = 5,
    @ColumnInfo(name = "next_attempt_at")
    val nextAttemptAt: Long = 0L,
    @ColumnInfo(name = "last_error_code")
    val lastErrorCode: String?,
    @ColumnInfo(name = "last_error_message")
    val lastErrorMessage: String?,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

@Entity(
    tableName = "sync_metadata",
    indices = [
        Index(value = ["business_id"]),
        Index(value = ["business_id", "resource_name"], unique = true)
    ]
)
data class SyncMetadataEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "business_id")
    val businessId: String,
    @ColumnInfo(name = "resource_name")
    val resourceName: String,
    @ColumnInfo(name = "last_pull_cursor")
    val lastPullCursor: String?,
    @ColumnInfo(name = "last_pull_epoch_ms")
    val lastPullEpochMs: Long = 0L,
    @ColumnInfo(name = "last_push_epoch_ms")
    val lastPushEpochMs: Long = 0L,
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

@Entity(
    tableName = "audit_logs",
    indices = [
        Index(value = ["business_id"]),
        Index(value = ["business_id", "created_at"]),
        Index(value = ["business_id", "entity_type", "entity_id"]),
        Index(value = ["actor_user_id"]),
        Index(value = ["sync_status"])
    ]
)
data class AuditLogEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "business_id")
    val businessId: String,
    @ColumnInfo(name = "actor_user_id")
    val actorUserId: String,
    @ColumnInfo(name = "actor_role")
    val actorRole: UserRole,
    @ColumnInfo(name = "action")
    val action: String,
    @ColumnInfo(name = "entity_type")
    val entityType: String,
    @ColumnInfo(name = "entity_id")
    val entityId: String,
    @ColumnInfo(name = "before_state_json")
    val beforeStateJson: String?,
    @ColumnInfo(name = "after_state_json")
    val afterStateJson: String?,
    @ColumnInfo(name = "device_id")
    val deviceId: String,
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
