package id.nexbiz.pos.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import id.nexbiz.pos.data.local.dao.InventoryDao
import id.nexbiz.pos.data.local.dao.ProductDao
import id.nexbiz.pos.data.local.dao.SaleDao
import id.nexbiz.pos.data.local.dao.SyncOutboxDao
import id.nexbiz.pos.data.local.entity.AccountEntity
import id.nexbiz.pos.data.local.entity.AuditLogEntity
import id.nexbiz.pos.data.local.entity.BusinessEntity
import id.nexbiz.pos.data.local.entity.CashMovementEntity
import id.nexbiz.pos.data.local.entity.CategoryEntity
import id.nexbiz.pos.data.local.entity.ExpenseEntity
import id.nexbiz.pos.data.local.entity.InventoryBalanceEntity
import id.nexbiz.pos.data.local.entity.InventoryMovementEntity
import id.nexbiz.pos.data.local.entity.JournalEntryEntity
import id.nexbiz.pos.data.local.entity.JournalLineEntity
import id.nexbiz.pos.data.local.entity.PaymentEntity
import id.nexbiz.pos.data.local.entity.ProductEntity
import id.nexbiz.pos.data.local.entity.ProductVariantEntity
import id.nexbiz.pos.data.local.entity.PurchaseEntity
import id.nexbiz.pos.data.local.entity.PurchaseItemEntity
import id.nexbiz.pos.data.local.entity.SaleEntity
import id.nexbiz.pos.data.local.entity.SaleItemEntity
import id.nexbiz.pos.data.local.entity.SupplierEntity
import id.nexbiz.pos.data.local.entity.SyncMetadataEntity
import id.nexbiz.pos.data.local.entity.SyncOutboxEntity
import id.nexbiz.pos.data.local.entity.UserMembershipEntity

@Database(
    entities = [
        BusinessEntity::class,
        UserMembershipEntity::class,
        CategoryEntity::class,
        ProductEntity::class,
        ProductVariantEntity::class,
        InventoryBalanceEntity::class,
        InventoryMovementEntity::class,
        SaleEntity::class,
        SaleItemEntity::class,
        PaymentEntity::class,
        SupplierEntity::class,
        PurchaseEntity::class,
        PurchaseItemEntity::class,
        ExpenseEntity::class,
        CashMovementEntity::class,
        AccountEntity::class,
        JournalEntryEntity::class,
        JournalLineEntity::class,
        SyncOutboxEntity::class,
        SyncMetadataEntity::class,
        AuditLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class NexbizDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun saleDao(): SaleDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun syncOutboxDao(): SyncOutboxDao

    companion object {
        const val DATABASE_NAME: String = "nexbiz_pos_offline.db"
    }
}
