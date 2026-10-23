package id.nexbiz.pos.data.local.model

import androidx.room.TypeConverter
import kotlinx.serialization.Serializable

@Serializable
enum class SyncStatus {
    LOCAL_ONLY,
    PENDING,
    SYNCING,
    SYNCED,
    FAILED,
    CONFLICT
}

@Serializable
enum class UserRole {
    OWNER,
    ADMIN,
    MANAGER,
    CASHIER,
    INVENTORY_CLERK,
    ACCOUNTANT
}

@Serializable
enum class PaymentMethod {
    CASH,
    QRIS,
    DEBIT_CARD,
    CREDIT_CARD,
    BANK_TRANSFER,
    E_WALLET,
    SPLIT,
    CREDIT_TEMPO
}

@Serializable
enum class MovementType {
    INITIAL_STOCK,
    PURCHASE_RECEIVE,
    SALE_DEDUCTION,
    SALE_VOID_RETURN,
    PURCHASE_RETURN,
    STOCK_OPNAME_ADJUSTMENT,
    DAMAGE_WRITE_OFF,
    TRANSFER_IN,
    TRANSFER_OUT
}

@Serializable
enum class AccountType {
    ASSET,
    LIABILITY,
    EQUITY,
    REVENUE,
    COST_OF_GOODS_SOLD,
    EXPENSE
}

@Serializable
enum class OutboxOperation {
    UPSERT_BUSINESS,
    UPSERT_CATEGORY,
    UPSERT_PRODUCT,
    UPSERT_VARIANT,
    RECORD_INVENTORY_MOVEMENT,
    CREATE_SALE_TRANSACTION,
    VOID_SALE_TRANSACTION,
    CREATE_PURCHASE_ORDER,
    RECORD_EXPENSE,
    RECORD_CASH_MOVEMENT,
    POST_JOURNAL_ENTRY
}

class PosTypeConverters {
    @TypeConverter
    fun fromSyncStatus(value: SyncStatus): String = value.name

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus =
        runCatching { SyncStatus.valueOf(value) }.getOrDefault(SyncStatus.LOCAL_ONLY)

    @TypeConverter
    fun fromUserRole(value: UserRole): String = value.name

    @TypeConverter
    fun toUserRole(value: String): UserRole =
        runCatching { UserRole.valueOf(value) }.getOrDefault(UserRole.CASHIER)

    @TypeConverter
    fun fromPaymentMethod(value: PaymentMethod): String = value.name

    @TypeConverter
    fun toPaymentMethod(value: String): PaymentMethod =
        runCatching { PaymentMethod.valueOf(value) }.getOrDefault(PaymentMethod.CASH)

    @TypeConverter
    fun fromMovementType(value: MovementType): String = value.name

    @TypeConverter
    fun toMovementType(value: String): MovementType =
        runCatching { MovementType.valueOf(value) }.getOrDefault(MovementType.STOCK_OPNAME_ADJUSTMENT)

    @TypeConverter
    fun fromAccountType(value: AccountType): String = value.name

    @TypeConverter
    fun toAccountType(value: String): AccountType =
        runCatching { AccountType.valueOf(value) }.getOrDefault(AccountType.ASSET)

    @TypeConverter
    fun fromOutboxOperation(value: OutboxOperation): String = value.name

    @TypeConverter
    fun toOutboxOperation(value: String): OutboxOperation =
        runCatching { OutboxOperation.valueOf(value) }.getOrDefault(OutboxOperation.CREATE_SALE_TRANSACTION)
}
