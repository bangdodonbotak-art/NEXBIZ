package id.nexbiz.pos.data.local.db

import androidx.room.TypeConverter
import id.nexbiz.pos.data.local.model.AccountType
import id.nexbiz.pos.data.local.model.MovementType
import id.nexbiz.pos.data.local.model.OutboxOperation
import id.nexbiz.pos.data.local.model.PaymentMethod
import id.nexbiz.pos.data.local.model.SyncStatus
import id.nexbiz.pos.data.local.model.UserRole
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @TypeConverter
    fun fromSyncStatus(value: SyncStatus?): String = (value ?: SyncStatus.LOCAL_ONLY).name

    @TypeConverter
    fun toSyncStatus(value: String?): SyncStatus =
        value?.let { runCatching { SyncStatus.valueOf(it) }.getOrNull() } ?: SyncStatus.LOCAL_ONLY

    @TypeConverter
    fun fromUserRole(value: UserRole?): String = (value ?: UserRole.CASHIER).name

    @TypeConverter
    fun toUserRole(value: String?): UserRole =
        value?.let { runCatching { UserRole.valueOf(it) }.getOrNull() } ?: UserRole.CASHIER

    @TypeConverter
    fun fromPaymentMethod(value: PaymentMethod?): String = (value ?: PaymentMethod.CASH).name

    @TypeConverter
    fun toPaymentMethod(value: String?): PaymentMethod =
        value?.let { runCatching { PaymentMethod.valueOf(it) }.getOrNull() } ?: PaymentMethod.CASH

    @TypeConverter
    fun fromMovementType(value: MovementType?): String = (value ?: MovementType.STOCK_OPNAME_ADJUSTMENT).name

    @TypeConverter
    fun toMovementType(value: String?): MovementType =
        value?.let { runCatching { MovementType.valueOf(it) }.getOrNull() }
            ?: MovementType.STOCK_OPNAME_ADJUSTMENT

    @TypeConverter
    fun fromAccountType(value: AccountType?): String = (value ?: AccountType.ASSET).name

    @TypeConverter
    fun toAccountType(value: String?): AccountType =
        value?.let { runCatching { AccountType.valueOf(it) }.getOrNull() } ?: AccountType.ASSET

    @TypeConverter
    fun fromOutboxOperation(value: OutboxOperation?): String =
        (value ?: OutboxOperation.CREATE_SALE_TRANSACTION).name

    @TypeConverter
    fun toOutboxOperation(value: String?): OutboxOperation =
        value?.let { runCatching { OutboxOperation.valueOf(it) }.getOrNull() }
            ?: OutboxOperation.CREATE_SALE_TRANSACTION

    @TypeConverter
    fun fromStringList(value: List<String>?): String =
        json.encodeToString(value ?: emptyList())

    @TypeConverter
    fun toStringList(value: String?): List<String> =
        if (value.isNullOrBlank()) {
            emptyList()
        } else {
            runCatching { json.decodeFromString<List<String>>(value) }.getOrDefault(emptyList())
        }
}
