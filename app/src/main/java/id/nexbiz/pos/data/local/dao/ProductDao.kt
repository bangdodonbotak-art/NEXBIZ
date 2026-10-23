package id.nexbiz.pos.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import id.nexbiz.pos.data.local.entity.BusinessEntity
import id.nexbiz.pos.data.local.entity.CategoryEntity
import id.nexbiz.pos.data.local.entity.ProductEntity
import id.nexbiz.pos.data.local.entity.ProductVariantEntity
import id.nexbiz.pos.data.local.entity.UserMembershipEntity
import id.nexbiz.pos.data.local.model.SyncStatus
import kotlinx.coroutines.flow.Flow

data class CatalogVariantItemTuple(
    @ColumnInfo(name = "variant_id") val variantId: String,
    @ColumnInfo(name = "product_id") val productId: String,
    @ColumnInfo(name = "business_id") val businessId: String,
    @ColumnInfo(name = "category_id") val categoryId: String?,
    @ColumnInfo(name = "category_name") val categoryName: String?,
    @ColumnInfo(name = "category_color") val categoryColor: String?,
    @ColumnInfo(name = "product_name") val productName: String,
    @ColumnInfo(name = "variant_name") val variantName: String,
    @ColumnInfo(name = "sku") val sku: String,
    @ColumnInfo(name = "barcode") val barcode: String?,
    @ColumnInfo(name = "unit_name") val unitName: String,
    @ColumnInfo(name = "selling_price_rp") val sellingPriceRp: Long,
    @ColumnInfo(name = "average_cost_rp") val averageCostRp: Long,
    @ColumnInfo(name = "is_taxable") val isTaxable: Boolean,
    @ColumnInfo(name = "track_inventory") val trackInventory: Boolean,
    @ColumnInfo(name = "local_estimated_stock") val localEstimatedStock: Long,
    @ColumnInfo(name = "server_confirmed_stock") val serverConfirmedStock: Long,
    @ColumnInfo(name = "min_stock_threshold") val minStockThreshold: Long,
    @ColumnInfo(name = "sync_status") val syncStatus: SyncStatus
)

@Dao
interface ProductDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBusiness(business: BusinessEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUserMemberships(memberships: List<UserMembershipEntity>)

    @Query("SELECT * FROM businesses WHERE business_id = :businessId LIMIT 1")
    fun observeBusiness(businessId: String): Flow<BusinessEntity?>

    @Query("SELECT * FROM businesses WHERE business_id = :businessId LIMIT 1")
    suspend fun getBusinessById(businessId: String): BusinessEntity?

    @Query("SELECT COUNT(*) FROM businesses")
    suspend fun countBusinesses(): Int

    @Query(
        """
        SELECT * FROM user_memberships 
        WHERE business_id = :businessId AND is_active = 1 
        ORDER BY role ASC, full_name ASC
        """
    )
    fun observeMemberships(businessId: String): Flow<List<UserMembershipEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategories(categories: List<CategoryEntity>)

    @Query(
        """
        SELECT * FROM categories 
        WHERE business_id = :businessId AND is_active = 1 
        ORDER BY sort_order ASC, name ASC
        """
    )
    fun observeActiveCategories(businessId: String): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProduct(product: ProductEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProducts(products: List<ProductEntity>)

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVariant(variant: ProductVariantEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVariants(variants: List<ProductVariantEntity>)

    @Update
    suspend fun updateVariant(variant: ProductVariantEntity)

    @Query("SELECT * FROM categories WHERE business_id = :businessId AND id = :categoryId LIMIT 1")
    suspend fun getCategoryById(businessId: String, categoryId: String): CategoryEntity?

    @Query("SELECT * FROM products WHERE business_id = :businessId AND id = :productId LIMIT 1")
    suspend fun getProductById(businessId: String, productId: String): ProductEntity?

    @Query("SELECT * FROM product_variants WHERE business_id = :businessId AND id = :variantId LIMIT 1")
    suspend fun getVariantById(businessId: String, variantId: String): ProductVariantEntity?

    @Query(
        """
        SELECT * FROM product_variants 
        WHERE business_id = :businessId AND barcode = :barcode AND is_active = 1 
        LIMIT 1
        """
    )
    suspend fun getVariantByBarcode(businessId: String, barcode: String): ProductVariantEntity?

    @Query(
        """
        SELECT * FROM product_variants 
        WHERE business_id = :businessId AND sku = :sku AND is_active = 1 
        LIMIT 1
        """
    )
    suspend fun getVariantBySku(businessId: String, sku: String): ProductVariantEntity?

    @Transaction
    @Query(
        """
        SELECT 
            pv.id AS variant_id,
            p.id AS product_id,
            pv.business_id AS business_id,
            p.category_id AS category_id,
            c.name AS category_name,
            c.color_hex AS category_color,
            p.name AS product_name,
            pv.variant_name AS variant_name,
            pv.sku AS sku,
            pv.barcode AS barcode,
            p.unit_name AS unit_name,
            pv.selling_price_rp AS selling_price_rp,
            pv.average_cost_rp AS average_cost_rp,
            p.is_taxable AS is_taxable,
            p.track_inventory AS track_inventory,
            COALESCE(ib.local_estimated_stock, 0) AS local_estimated_stock,
            COALESCE(ib.server_confirmed_stock, 0) AS server_confirmed_stock,
            pv.min_stock_threshold AS min_stock_threshold,
            pv.sync_status AS sync_status
        FROM product_variants pv
        INNER JOIN products p ON p.id = pv.product_id AND p.business_id = pv.business_id
        LEFT JOIN categories c ON c.id = p.category_id AND c.business_id = p.business_id
        LEFT JOIN inventory_balances ib ON ib.variant_id = pv.id AND ib.business_id = pv.business_id
        WHERE pv.business_id = :businessId
          AND pv.is_active = 1
          AND p.is_active = 1
        ORDER BY COALESCE(c.sort_order, 999) ASC, p.name ASC, pv.variant_name ASC
        """
    )
    fun observeCatalogVariants(businessId: String): Flow<List<CatalogVariantItemTuple>>

    @Transaction
    @Query(
        """
        SELECT 
            pv.id AS variant_id,
            p.id AS product_id,
            pv.business_id AS business_id,
            p.category_id AS category_id,
            c.name AS category_name,
            c.color_hex AS category_color,
            p.name AS product_name,
            pv.variant_name AS variant_name,
            pv.sku AS sku,
            pv.barcode AS barcode,
            p.unit_name AS unit_name,
            pv.selling_price_rp AS selling_price_rp,
            pv.average_cost_rp AS average_cost_rp,
            p.is_taxable AS is_taxable,
            p.track_inventory AS track_inventory,
            COALESCE(ib.local_estimated_stock, 0) AS local_estimated_stock,
            COALESCE(ib.server_confirmed_stock, 0) AS server_confirmed_stock,
            pv.min_stock_threshold AS min_stock_threshold,
            pv.sync_status AS sync_status
        FROM product_variants pv
        INNER JOIN products p ON p.id = pv.product_id AND p.business_id = pv.business_id
        LEFT JOIN categories c ON c.id = p.category_id AND c.business_id = p.business_id
        LEFT JOIN inventory_balances ib ON ib.variant_id = pv.id AND ib.business_id = pv.business_id
        WHERE pv.business_id = :businessId
          AND pv.is_active = 1
          AND p.is_active = 1
          AND (
              p.name LIKE '%' || :query || '%'
              OR pv.variant_name LIKE '%' || :query || '%'
              OR pv.sku LIKE '%' || :query || '%'
              OR COALESCE(pv.barcode, '') LIKE '%' || :query || '%'
          )
        ORDER BY p.name ASC, pv.variant_name ASC
        """
    )
    fun searchCatalogVariants(businessId: String, query: String): Flow<List<CatalogVariantItemTuple>>

    @Query(
        """
        UPDATE product_variants 
        SET sync_status = :syncStatus, updated_at = :updatedAt 
        WHERE business_id = :businessId AND id = :variantId
        """
    )
    suspend fun updateVariantSyncStatus(
        businessId: String,
        variantId: String,
        syncStatus: SyncStatus,
        updatedAt: Long
    )
}
