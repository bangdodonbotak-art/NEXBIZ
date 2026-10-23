package id.nexbiz.pos.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import id.nexbiz.pos.data.local.model.AccountType
import id.nexbiz.pos.data.local.model.OutboxOperation
import id.nexbiz.pos.data.local.model.PaymentMethod
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@JsonClass(generateAdapter = true)
data class OutboxPushBatchRequest(
    @SerialName("business_id")
    @Json(name = "business_id")
    val businessId: String,
    @SerialName("device_id")
    @Json(name = "device_id")
    val deviceId: String,
    @SerialName("client_timestamp_ms")
    @Json(name = "client_timestamp_ms")
    val clientTimestampMs: Long,
    @SerialName("mutations")
    @Json(name = "mutations")
    val mutations: List<OutboxMutationDto>
)

@Serializable
@JsonClass(generateAdapter = true)
data class OutboxMutationDto(
    @SerialName("outbox_id")
    @Json(name = "outbox_id")
    val outboxId: String,
    @SerialName("business_id")
    @Json(name = "business_id")
    val businessId: String,
    @SerialName("idempotency_key")
    @Json(name = "idempotency_key")
    val idempotencyKey: String,
    @SerialName("operation")
    @Json(name = "operation")
    val operation: OutboxOperation,
    @SerialName("entity_type")
    @Json(name = "entity_type")
    val entityType: String,
    @SerialName("entity_id")
    @Json(name = "entity_id")
    val entityId: String,
    @SerialName("payload_json")
    @Json(name = "payload_json")
    val payloadJson: String,
    @SerialName("created_at_ms")
    @Json(name = "created_at_ms")
    val createdAtMs: Long
)

@Serializable
@JsonClass(generateAdapter = true)
data class OutboxPushBatchResponse(
    @SerialName("business_id")
    @Json(name = "business_id")
    val businessId: String,
    @SerialName("server_timestamp_ms")
    @Json(name = "server_timestamp_ms")
    val serverTimestampMs: Long,
    @SerialName("acknowledged")
    @Json(name = "acknowledged")
    val acknowledged: List<OutboxMutationAckDto> = emptyList(),
    @SerialName("conflicts")
    @Json(name = "conflicts")
    val conflicts: List<OutboxConflictDto> = emptyList(),
    @SerialName("stock_confirmations")
    @Json(name = "stock_confirmations")
    val stockConfirmations: List<StockConfirmationDto> = emptyList()
)

@Serializable
@JsonClass(generateAdapter = true)
data class OutboxMutationAckDto(
    @SerialName("outbox_id")
    @Json(name = "outbox_id")
    val outboxId: String,
    @SerialName("idempotency_key")
    @Json(name = "idempotency_key")
    val idempotencyKey: String,
    @SerialName("entity_type")
    @Json(name = "entity_type")
    val entityType: String,
    @SerialName("entity_id")
    @Json(name = "entity_id")
    val entityId: String,
    @SerialName("server_version")
    @Json(name = "server_version")
    val serverVersion: Long,
    @SerialName("processed_at_ms")
    @Json(name = "processed_at_ms")
    val processedAtMs: Long
)

@Serializable
@JsonClass(generateAdapter = true)
data class OutboxConflictDto(
    @SerialName("outbox_id")
    @Json(name = "outbox_id")
    val outboxId: String,
    @SerialName("idempotency_key")
    @Json(name = "idempotency_key")
    val idempotencyKey: String,
    @SerialName("entity_type")
    @Json(name = "entity_type")
    val entityType: String,
    @SerialName("entity_id")
    @Json(name = "entity_id")
    val entityId: String,
    @SerialName("variant_id")
    @Json(name = "variant_id")
    val variantId: String? = null,
    @SerialName("conflict_code")
    @Json(name = "conflict_code")
    val conflictCode: String,
    @SerialName("conflict_message")
    @Json(name = "conflict_message")
    val conflictMessage: String,
    @SerialName("server_version")
    @Json(name = "server_version")
    val serverVersion: Long,
    @SerialName("server_confirmed_stock")
    @Json(name = "server_confirmed_stock")
    val serverConfirmedStock: Long? = null,
    @SerialName("server_unit_cost_rp")
    @Json(name = "server_unit_cost_rp")
    val serverUnitCostRp: Long? = null,
    @SerialName("resolution_strategy")
    @Json(name = "resolution_strategy")
    val resolutionStrategy: String = "SERVER_AUTHORITATIVE_STOCK_REBASE"
)

@Serializable
@JsonClass(generateAdapter = true)
data class StockConfirmationDto(
    @SerialName("variant_id")
    @Json(name = "variant_id")
    val variantId: String,
    @SerialName("server_confirmed_stock")
    @Json(name = "server_confirmed_stock")
    val serverConfirmedStock: Long,
    @SerialName("unit_cost_rp")
    @Json(name = "unit_cost_rp")
    val unitCostRp: Long,
    @SerialName("server_version")
    @Json(name = "server_version")
    val serverVersion: Long,
    @SerialName("reconciled_at_ms")
    @Json(name = "reconciled_at_ms")
    val reconciledAtMs: Long
)

@Serializable
@JsonClass(generateAdapter = true)
data class DeltaPullResponse(
    @SerialName("business_id")
    @Json(name = "business_id")
    val businessId: String,
    @SerialName("next_cursor")
    @Json(name = "next_cursor")
    val nextCursor: String,
    @SerialName("has_more")
    @Json(name = "has_more")
    val hasMore: Boolean = false,
    @SerialName("server_timestamp_ms")
    @Json(name = "server_timestamp_ms")
    val serverTimestampMs: Long,
    @SerialName("categories")
    @Json(name = "categories")
    val categories: List<CategoryDeltaDto> = emptyList(),
    @SerialName("products")
    @Json(name = "products")
    val products: List<ProductDeltaDto> = emptyList(),
    @SerialName("variants")
    @Json(name = "variants")
    val variants: List<ProductVariantDeltaDto> = emptyList(),
    @SerialName("inventory_balances")
    @Json(name = "inventory_balances")
    val inventoryBalances: List<InventoryBalanceDeltaDto> = emptyList(),
    @SerialName("accounts")
    @Json(name = "accounts")
    val accounts: List<AccountDeltaDto> = emptyList()
)

@Serializable
@JsonClass(generateAdapter = true)
data class CategoryDeltaDto(
    @SerialName("id")
    @Json(name = "id")
    val id: String,
    @SerialName("business_id")
    @Json(name = "business_id")
    val businessId: String,
    @SerialName("name")
    @Json(name = "name")
    val name: String,
    @SerialName("description")
    @Json(name = "description")
    val description: String? = null,
    @SerialName("color_hex")
    @Json(name = "color_hex")
    val colorHex: String = "#059669",
    @SerialName("sort_order")
    @Json(name = "sort_order")
    val sortOrder: Int = 0,
    @SerialName("is_active")
    @Json(name = "is_active")
    val isActive: Boolean = true,
    @SerialName("version")
    @Json(name = "version")
    val version: Long,
    @SerialName("updated_at_ms")
    @Json(name = "updated_at_ms")
    val updatedAtMs: Long
)

@Serializable
@JsonClass(generateAdapter = true)
data class ProductDeltaDto(
    @SerialName("id")
    @Json(name = "id")
    val id: String,
    @SerialName("business_id")
    @Json(name = "business_id")
    val businessId: String,
    @SerialName("category_id")
    @Json(name = "category_id")
    val categoryId: String? = null,
    @SerialName("sku")
    @Json(name = "sku")
    val sku: String,
    @SerialName("name")
    @Json(name = "name")
    val name: String,
    @SerialName("description")
    @Json(name = "description")
    val description: String? = null,
    @SerialName("unit_name")
    @Json(name = "unit_name")
    val unitName: String = "pcs",
    @SerialName("base_price_rp")
    @Json(name = "base_price_rp")
    val basePriceRp: Long,
    @SerialName("base_cost_rp")
    @Json(name = "base_cost_rp")
    val baseCostRp: Long,
    @SerialName("is_taxable")
    @Json(name = "is_taxable")
    val isTaxable: Boolean = true,
    @SerialName("track_inventory")
    @Json(name = "track_inventory")
    val trackInventory: Boolean = true,
    @SerialName("is_active")
    @Json(name = "is_active")
    val isActive: Boolean = true,
    @SerialName("version")
    @Json(name = "version")
    val version: Long,
    @SerialName("updated_at_ms")
    @Json(name = "updated_at_ms")
    val updatedAtMs: Long
)

@Serializable
@JsonClass(generateAdapter = true)
data class ProductVariantDeltaDto(
    @SerialName("id")
    @Json(name = "id")
    val id: String,
    @SerialName("business_id")
    @Json(name = "business_id")
    val businessId: String,
    @SerialName("product_id")
    @Json(name = "product_id")
    val productId: String,
    @SerialName("variant_name")
    @Json(name = "variant_name")
    val variantName: String,
    @SerialName("sku")
    @Json(name = "sku")
    val sku: String,
    @SerialName("barcode")
    @Json(name = "barcode")
    val barcode: String? = null,
    @SerialName("selling_price_rp")
    @Json(name = "selling_price_rp")
    val sellingPriceRp: Long,
    @SerialName("average_cost_rp")
    @Json(name = "average_cost_rp")
    val averageCostRp: Long,
    @SerialName("min_stock_threshold")
    @Json(name = "min_stock_threshold")
    val minStockThreshold: Long = 5L,
    @SerialName("is_active")
    @Json(name = "is_active")
    val isActive: Boolean = true,
    @SerialName("version")
    @Json(name = "version")
    val version: Long,
    @SerialName("updated_at_ms")
    @Json(name = "updated_at_ms")
    val updatedAtMs: Long
)

@Serializable
@JsonClass(generateAdapter = true)
data class InventoryBalanceDeltaDto(
    @SerialName("id")
    @Json(name = "id")
    val id: String,
    @SerialName("business_id")
    @Json(name = "business_id")
    val businessId: String,
    @SerialName("variant_id")
    @Json(name = "variant_id")
    val variantId: String,
    @SerialName("server_confirmed_stock")
    @Json(name = "server_confirmed_stock")
    val serverConfirmedStock: Long,
    @SerialName("unit_cost_rp")
    @Json(name = "unit_cost_rp")
    val unitCostRp: Long,
    @SerialName("version")
    @Json(name = "version")
    val version: Long,
    @SerialName("updated_at_ms")
    @Json(name = "updated_at_ms")
    val updatedAtMs: Long
)

@Serializable
@JsonClass(generateAdapter = true)
data class AccountDeltaDto(
    @SerialName("id")
    @Json(name = "id")
    val id: String,
    @SerialName("business_id")
    @Json(name = "business_id")
    val businessId: String,
    @SerialName("code")
    @Json(name = "code")
    val code: String,
    @SerialName("name")
    @Json(name = "name")
    val name: String,
    @SerialName("account_type")
    @Json(name = "account_type")
    val accountType: AccountType,
    @SerialName("normal_debit")
    @Json(name = "normal_debit")
    val normalDebit: Boolean,
    @SerialName("current_balance_rp")
    @Json(name = "current_balance_rp")
    val currentBalanceRp: Long,
    @SerialName("is_system_account")
    @Json(name = "is_system_account")
    val isSystemAccount: Boolean,
    @SerialName("is_active")
    @Json(name = "is_active")
    val isActive: Boolean = true,
    @SerialName("version")
    @Json(name = "version")
    val version: Long,
    @SerialName("updated_at_ms")
    @Json(name = "updated_at_ms")
    val updatedAtMs: Long
)

@Serializable
@JsonClass(generateAdapter = true)
data class SaleOutboxPayloadDto(
    @SerialName("sale_id")
    @Json(name = "sale_id")
    val saleId: String,
    @SerialName("business_id")
    @Json(name = "business_id")
    val businessId: String,
    @SerialName("receipt_number")
    @Json(name = "receipt_number")
    val receiptNumber: String,
    @SerialName("cashier_user_id")
    @Json(name = "cashier_user_id")
    val cashierUserId: String,
    @SerialName("customer_name")
    @Json(name = "customer_name")
    val customerName: String? = null,
    @SerialName("subtotal_rp")
    @Json(name = "subtotal_rp")
    val subtotalRp: Long,
    @SerialName("discount_total_rp")
    @Json(name = "discount_total_rp")
    val discountTotalRp: Long,
    @SerialName("tax_total_rp")
    @Json(name = "tax_total_rp")
    val taxTotalRp: Long,
    @SerialName("grand_total_rp")
    @Json(name = "grand_total_rp")
    val grandTotalRp: Long,
    @SerialName("total_paid_rp")
    @Json(name = "total_paid_rp")
    val totalPaidRp: Long,
    @SerialName("change_due_rp")
    @Json(name = "change_due_rp")
    val changeDueRp: Long,
    @SerialName("total_cogs_rp")
    @Json(name = "total_cogs_rp")
    val totalCogsRp: Long,
    @SerialName("payment_method")
    @Json(name = "payment_method")
    val paymentMethod: PaymentMethod,
    @SerialName("created_at_ms")
    @Json(name = "created_at_ms")
    val createdAtMs: Long,
    @SerialName("items")
    @Json(name = "items")
    val items: List<SaleItemPayloadDto>,
    @SerialName("payments")
    @Json(name = "payments")
    val payments: List<PaymentPayloadDto>
)

@Serializable
@JsonClass(generateAdapter = true)
data class SaleItemPayloadDto(
    @SerialName("id")
    @Json(name = "id")
    val id: String,
    @SerialName("variant_id")
    @Json(name = "variant_id")
    val variantId: String,
    @SerialName("sku")
    @Json(name = "sku")
    val sku: String,
    @SerialName("quantity")
    @Json(name = "quantity")
    val quantity: Long,
    @SerialName("unit_price_rp")
    @Json(name = "unit_price_rp")
    val unitPriceRp: Long,
    @SerialName("unit_cost_rp")
    @Json(name = "unit_cost_rp")
    val unitCostRp: Long,
    @SerialName("discount_rp")
    @Json(name = "discount_rp")
    val discountRp: Long,
    @SerialName("tax_rp")
    @Json(name = "tax_rp")
    val taxRp: Long,
    @SerialName("line_total_rp")
    @Json(name = "line_total_rp")
    val lineTotalRp: Long
)

@Serializable
@JsonClass(generateAdapter = true)
data class PaymentPayloadDto(
    @SerialName("id")
    @Json(name = "id")
    val id: String,
    @SerialName("payment_method")
    @Json(name = "payment_method")
    val paymentMethod: PaymentMethod,
    @SerialName("amount_rp")
    @Json(name = "amount_rp")
    val amountRp: Long,
    @SerialName("tendered_rp")
    @Json(name = "tendered_rp")
    val tenderedRp: Long,
    @SerialName("change_rp")
    @Json(name = "change_rp")
    val changeRp: Long,
    @SerialName("reference_code")
    @Json(name = "reference_code")
    val referenceCode: String? = null,
    @SerialName("paid_at_ms")
    @Json(name = "paid_at_ms")
    val paidAtMs: Long
)

@Serializable
@JsonClass(generateAdapter = true)
data class StockAdjustmentPayloadDto(
    @SerialName("movement_id")
    @Json(name = "movement_id")
    val movementId: String,
    @SerialName("business_id")
    @Json(name = "business_id")
    val businessId: String,
    @SerialName("variant_id")
    @Json(name = "variant_id")
    val variantId: String,
    @SerialName("movement_type")
    @Json(name = "movement_type")
    val movementType: String,
    @SerialName("quantity_delta")
    @Json(name = "quantity_delta")
    val quantityDelta: Long,
    @SerialName("stock_before")
    @Json(name = "stock_before")
    val stockBefore: Long,
    @SerialName("stock_after")
    @Json(name = "stock_after")
    val stockAfter: Long,
    @SerialName("unit_cost_rp")
    @Json(name = "unit_cost_rp")
    val unitCostRp: Long,
    @SerialName("base_version")
    @Json(name = "base_version")
    val baseVersion: Long,
    @SerialName("created_at_ms")
    @Json(name = "created_at_ms")
    val createdAtMs: Long
)
