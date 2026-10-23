package id.nexbiz.pos.ui.security

import id.nexbiz.pos.data.local.model.UserRole
import id.nexbiz.pos.util.printer.EscPosPrinterHelper

enum class PosRestrictedAction(
    val title: String,
    val description: String
) {
    VOID_SALE(
        title = "Void / Pembatalan Nota Penjualan",
        description = "Membatalkan transaksi yang telah selesai dan mengembalikan stok."
    ),
    MANUAL_DISCOUNT(
        title = "Diskon Manual Nota / Item",
        description = "Memberikan potongan harga manual di luar promo otomatis."
    ),
    FINANCIAL_REPORTS(
        title = "Laporan Keuangan (Laba Rugi & Buku Besar)",
        description = "Mengakses laporan P&L SAK EMKM, BEP, dan saldo akun COA."
    ),
    STOCK_OPNAME(
        title = "Stock Opname & Penyesuaian Persediaan",
        description = "Mengubah stok fisik dan mencatat selisih persediaan."
    ),
    CASH_DRAWER_MOVEMENT(
        title = "Mutasi Kas Masuk / Keluar Laci",
        description = "Menambah modal kas laci atau menarik setoran tunai kasir."
    ),
    EXPORT_REPORTS_AND_CSV(
        title = "Ekspor Dokumen PDF & CSV",
        description = "Mengekspor laporan keuangan PDF dan data katalog/jurnal CSV."
    )
}

sealed class PermissionCheckOutcome {
    data object Granted : PermissionCheckOutcome()
    data class Denied(
        val action: PosRestrictedAction,
        val currentRole: UserRole,
        val requiredRoles: Set<UserRole>,
        val reasonMessage: String
    ) : PermissionCheckOutcome()
}

/**
 * Multi-Tenant Role Permission Enforcer for NEXBIZ POS.
 * Restricts sensitive financial and inventory operations (`VOID_SALE`, `MANUAL_DISCOUNT`,
 * `FINANCIAL_REPORTS`, `STOCK_OPNAME`, `CASH_DRAWER_MOVEMENT`, `EXPORT_REPORTS_AND_CSV`)
 * according to `UserRole` and whole-Rupiah (`Long`) discount ceilings.
 */
object RolePermissionEnforcer {

    private val rolePermissionsMap: Map<PosRestrictedAction, Set<UserRole>> = mapOf(
        PosRestrictedAction.VOID_SALE to setOf(
            UserRole.OWNER,
            UserRole.ADMIN,
            UserRole.MANAGER
        ),
        PosRestrictedAction.MANUAL_DISCOUNT to setOf(
            UserRole.OWNER,
            UserRole.ADMIN,
            UserRole.MANAGER,
            UserRole.CASHIER
        ),
        PosRestrictedAction.FINANCIAL_REPORTS to setOf(
            UserRole.OWNER,
            UserRole.ADMIN,
            UserRole.MANAGER,
            UserRole.ACCOUNTANT
        ),
        PosRestrictedAction.STOCK_OPNAME to setOf(
            UserRole.OWNER,
            UserRole.ADMIN,
            UserRole.MANAGER,
            UserRole.INVENTORY_CLERK
        ),
        PosRestrictedAction.CASH_DRAWER_MOVEMENT to setOf(
            UserRole.OWNER,
            UserRole.ADMIN,
            UserRole.MANAGER,
            UserRole.CASHIER
        ),
        PosRestrictedAction.EXPORT_REPORTS_AND_CSV to setOf(
            UserRole.OWNER,
            UserRole.ADMIN,
            UserRole.MANAGER,
            UserRole.ACCOUNTANT
        )
    )

    fun requiredRolesFor(action: PosRestrictedAction): Set<UserRole> =
        rolePermissionsMap[action] ?: setOf(UserRole.OWNER, UserRole.ADMIN)

    fun isActionPermitted(role: UserRole, action: PosRestrictedAction): Boolean =
        requiredRolesFor(action).contains(role)

    fun enforce(role: UserRole, action: PosRestrictedAction): PermissionCheckOutcome {
        val allowedRoles = requiredRolesFor(action)
        return if (allowedRoles.contains(role)) {
            PermissionCheckOutcome.Granted
        } else {
            PermissionCheckOutcome.Denied(
                action = action,
                currentRole = role,
                requiredRoles = allowedRoles,
                reasonMessage = "Aksi '${action.title}' membutuhkan otorisasi ${
                    allowedRoles.joinToString("/") { it.name }
                } (Peran aktif: ${role.name})."
            )
        }
    }

    /**
     * Validates whether the active user role and membership ceiling allow a manual discount
     * in whole IDR (`Long`).
     */
    fun checkManualDiscountLimitRp(
        role: UserRole,
        membershipMaxDiscountRp: Long,
        requestedDiscountRp: Long
    ): PermissionCheckOutcome {
        val safeRequested = requestedDiscountRp.coerceAtLeast(0L)
        if (safeRequested == 0L) return PermissionCheckOutcome.Granted

        val baseOutcome = enforce(role, PosRestrictedAction.MANUAL_DISCOUNT)
        if (baseOutcome is PermissionCheckOutcome.Denied) return baseOutcome

        // OWNER and ADMIN have unlimited discount authority unless explicitly capped
        if (role == UserRole.OWNER || role == UserRole.ADMIN) {
            return PermissionCheckOutcome.Granted
        }

        val effectiveCeilingRp = if (membershipMaxDiscountRp > 0L) {
            membershipMaxDiscountRp
        } else {
            defaultRoleDiscountCeilingRp(role)
        }

        return if (safeRequested <= effectiveCeilingRp) {
            PermissionCheckOutcome.Granted
        } else {
            PermissionCheckOutcome.Denied(
                action = PosRestrictedAction.MANUAL_DISCOUNT,
                currentRole = role,
                requiredRoles = setOf(UserRole.OWNER, UserRole.ADMIN, UserRole.MANAGER),
                reasonMessage = "Diskon manual ${EscPosPrinterHelper.formatIdr(safeRequested)} melebihi batas otorisasi ${role.name} (${EscPosPrinterHelper.formatIdr(effectiveCeilingRp)}). Gunakan PIN Manager/Owner."
            )
        }
    }

    fun defaultRoleDiscountCeilingRp(role: UserRole): Long = when (role) {
        UserRole.OWNER, UserRole.ADMIN -> Long.MAX_VALUE
        UserRole.MANAGER -> 500_000L
        UserRole.CASHIER -> 50_000L
        UserRole.ACCOUNTANT, UserRole.INVENTORY_CLERK -> 0L
    }
}
