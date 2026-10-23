package id.nexbiz.pos.ui.security

import android.content.Context
import android.content.SharedPreferences
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import id.nexbiz.pos.data.local.entity.UserMembershipEntity
import id.nexbiz.pos.data.local.model.SyncStatus
import id.nexbiz.pos.data.local.model.UserRole
import id.nexbiz.pos.di.DatabaseModule
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ActiveCashierSession(
    val membershipId: String,
    val businessId: String,
    val userId: String,
    val fullName: String,
    val email: String,
    val role: UserRole,
    val maxDiscountRp: Long,
    val unlockedAtEpochMs: Long,
    val lastInteractionEpochMs: Long
)

data class AppLockSessionState(
    val isLocked: Boolean = true,
    val activeSession: ActiveCashierSession = DEFAULT_CASHIER_SESSION,
    val availableStaff: List<UserMembershipEntity> = DEFAULT_STAFF_PROFILES,
    val failedAttemptsCount: Int = 0,
    val lockoutUntilEpochMs: Long = 0L,
    val isBiometricAvailable: Boolean = false,
    val authFeedbackMessage: String? = null
) {
    companion object {
        private val now = System.currentTimeMillis()

        val DEFAULT_STAFF_PROFILES = listOf(
            UserMembershipEntity(
                id = "m1000000-0000-4000-8000-000000000002",
                businessId = DatabaseModule.DEFAULT_BUSINESS_ID,
                userId = DatabaseModule.DEFAULT_CASHIER_USER_ID,
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
            ),
            UserMembershipEntity(
                id = "m1000000-0000-4000-8000-000000000001",
                businessId = DatabaseModule.DEFAULT_BUSINESS_ID,
                userId = DatabaseModule.DEFAULT_OWNER_USER_ID,
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
            )
        )

        val DEFAULT_CASHIER_SESSION = ActiveCashierSession(
            membershipId = "m1000000-0000-4000-8000-000000000002",
            businessId = DatabaseModule.DEFAULT_BUSINESS_ID,
            userId = DatabaseModule.DEFAULT_CASHIER_USER_ID,
            fullName = "Siti Rahma (Kasir Utama)",
            email = "siti.kasir@nexbiz.id",
            role = UserRole.CASHIER,
            maxDiscountRp = 50_000L,
            unlockedAtEpochMs = now,
            lastInteractionEpochMs = now
        )
    }
}

/**
 * Fast Cashier PIN & Biometric Session Lock Manager.
 * Persists encrypted PIN digests and active session metadata using Jetpack Security
 * `EncryptedSharedPreferences` (`AES256_GCM` master key) and supports hardware biometric unlock
 * via AndroidX `BiometricPrompt`.
 */
class AppLockManager(
    context: Context,
    startLocked: Boolean = false
) {
    private val appContext: Context = context.applicationContext
    private val securePrefs: SharedPreferences = createEncryptedOrFallbackPrefs(appContext)

    private val _sessionState = MutableStateFlow(
        loadInitialSessionState(startLocked = startLocked)
    )
    val sessionState: StateFlow<AppLockSessionState> = _sessionState.asStateFlow()

    init {
        ensureDefaultPinsSeeded()
    }

    fun updateAvailableStaffMemberships(memberships: List<UserMembershipEntity>) {
        if (memberships.isEmpty()) return
        for (member in memberships) {
            val key = pinHashPrefKey(member.businessId, member.userId)
            if (!securePrefs.contains(key)) {
                val normalizedHash = if (member.pinHash.length == 6 && member.pinHash.all { it.isDigit() }) {
                    sha256Hex(member.pinHash)
                } else {
                    member.pinHash
                }
                securePrefs.edit().putString(key, normalizedHash).apply()
            }
        }
        _sessionState.update { it.copy(availableStaff = memberships) }
    }

    /**
     * Verifies a 6-digit cashier/owner PIN against `EncryptedSharedPreferences` and unlocks
     * the terminal session as the matching `UserMembershipEntity`.
     */
    fun verifyPinAndUnlock(rawPin: String, targetUserId: String? = null): Boolean {
        val now = System.currentTimeMillis()
        val current = _sessionState.value

        if (current.lockoutUntilEpochMs > now) {
            val remainingSec = ((current.lockoutUntilEpochMs - now) / 1000L).coerceAtLeast(1L)
            _sessionState.update {
                it.copy(authFeedbackMessage = "Terminal terkunci sementara. Coba lagi dalam ${remainingSec}d.")
            }
            return false
        }

        val cleanPin = rawPin.trim()
        if (cleanPin.length < 4) {
            _sessionState.update {
                it.copy(authFeedbackMessage = "PIN minimal 4–6 digit angka.")
            }
            return false
        }

        val inputHash = sha256Hex(cleanPin)
        val candidates = if (targetUserId != null) {
            current.availableStaff.filter { it.userId == targetUserId }
        } else {
            current.availableStaff
        }

        val matchedMember = candidates.firstOrNull { member ->
            val storedDigest = securePrefs.getString(
                pinHashPrefKey(member.businessId, member.userId),
                null
            )
            storedDigest == inputHash ||
                member.pinHash == cleanPin ||
                member.pinHash == inputHash
        }

        return if (matchedMember != null) {
            val unlockedSession = ActiveCashierSession(
                membershipId = matchedMember.id,
                businessId = matchedMember.businessId,
                userId = matchedMember.userId,
                fullName = matchedMember.fullName,
                email = matchedMember.email,
                role = matchedMember.role,
                maxDiscountRp = matchedMember.maxDiscountRp,
                unlockedAtEpochMs = now,
                lastInteractionEpochMs = now
            )
            persistActiveSession(unlockedSession, isLocked = false)
            _sessionState.update {
                it.copy(
                    isLocked = false,
                    activeSession = unlockedSession,
                    failedAttemptsCount = 0,
                    lockoutUntilEpochMs = 0L,
                    authFeedbackMessage = "Sesi dibuka: ${matchedMember.fullName} (${matchedMember.role.name})"
                )
            }
            true
        } else {
            val newFailures = current.failedAttemptsCount + 1
            val lockoutUntil = if (newFailures >= MAX_PIN_ATTEMPTS_BEFORE_LOCKOUT) {
                now + LOCKOUT_DURATION_MS
            } else {
                0L
            }
            _sessionState.update {
                it.copy(
                    failedAttemptsCount = newFailures,
                    lockoutUntilEpochMs = lockoutUntil,
                    authFeedbackMessage = if (lockoutUntil > now) {
                        "Terlalu banyak percobaan salah (${newFailures}x). Dikunci 30 detik."
                    } else {
                        "PIN Kasir/Otorisasi tidak valid (Percobaan $newFailures/$MAX_PIN_ATTEMPTS_BEFORE_LOCKOUT)."
                    }
                )
            }
            false
        }
    }

    /**
     * Verifies a Manager or Owner supervisor PIN for a restricted action (`PosRestrictedAction`)
     * without permanently replacing the active cashier's shift unless requested.
     */
    fun verifySupervisorOverridePin(
        rawPin: String,
        action: PosRestrictedAction,
        requestedDiscountRp: Long = 0L
    ): PermissionCheckOutcome {
        val cleanPin = rawPin.trim()
        val inputHash = sha256Hex(cleanPin)

        val matchedMember = _sessionState.value.availableStaff.firstOrNull { member ->
            val storedDigest = securePrefs.getString(
                pinHashPrefKey(member.businessId, member.userId),
                null
            )
            storedDigest == inputHash ||
                member.pinHash == cleanPin ||
                member.pinHash == inputHash
        } ?: return PermissionCheckOutcome.Denied(
            action = action,
            currentRole = _sessionState.value.activeSession.role,
            requiredRoles = RolePermissionEnforcer.requiredRolesFor(action),
            reasonMessage = "PIN Supervisor tidak dikenali."
        )

        return if (action == PosRestrictedAction.MANUAL_DISCOUNT && requestedDiscountRp > 0L) {
            RolePermissionEnforcer.checkManualDiscountLimitRp(
                role = matchedMember.role,
                membershipMaxDiscountRp = matchedMember.maxDiscountRp,
                requestedDiscountRp = requestedDiscountRp
            )
        } else {
            RolePermissionEnforcer.enforce(matchedMember.role, action)
        }
    }

    /**
     * Launches AndroidX `BiometricPrompt` for fast fingerprint / face biometric unlock.
     */
    fun promptBiometricUnlock(
        activity: FragmentActivity,
        onUnlocked: (ActiveCashierSession) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (!canAuthenticateWithBiometrics(appContext)) {
            val msg = "Biometrik perangkat tidak tersedia atau belum dikonfigurasi."
            _sessionState.update { it.copy(authFeedbackMessage = msg) }
            onError(msg)
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                val now = System.currentTimeMillis()
                val currentSession = _sessionState.value.activeSession.copy(
                    unlockedAtEpochMs = now,
                    lastInteractionEpochMs = now
                )
                persistActiveSession(currentSession, isLocked = false)
                _sessionState.update {
                    it.copy(
                        isLocked = false,
                        activeSession = currentSession,
                        failedAttemptsCount = 0,
                        lockoutUntilEpochMs = 0L,
                        authFeedbackMessage = "Biometrik terverifikasi: ${currentSession.fullName}"
                    )
                }
                onUnlocked(currentSession)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                val message = errString.toString()
                _sessionState.update { it.copy(authFeedbackMessage = message) }
                onError(message)
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                _sessionState.update {
                    it.copy(authFeedbackMessage = "Sidik jari / wajah tidak cocok. Gunakan PIN 6-digit.")
                }
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Buka Kunci Kasir NEXBIZ POS")
            .setSubtitle("Autentikasi biometrik untuk melanjutkan sesi ${sessionState.value.activeSession.fullName}")
            .setNegativeButtonText("Gunakan PIN")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            .build()

        prompt.authenticate(promptInfo)
    }

    fun lockSessionNow(reason: String = "Sesi Kasir Dikunci Manual") {
        val current = _sessionState.value.activeSession
        persistActiveSession(current, isLocked = true)
        _sessionState.update {
            it.copy(
                isLocked = true,
                authFeedbackMessage = reason
            )
        }
    }

    fun recordUserInteractionAndCheckTimeout(
        timeoutDurationMs: Long = DEFAULT_INACTIVITY_TIMEOUT_MS
    ): Boolean {
        val now = System.currentTimeMillis()
        val state = _sessionState.value
        if (state.isLocked) return true

        val elapsed = now - state.activeSession.lastInteractionEpochMs
        return if (elapsed > timeoutDurationMs) {
            lockSessionNow("Sesi dikunci otomatis karena tidak aktif")
            true
        } else {
            val updatedSession = state.activeSession.copy(lastInteractionEpochMs = now)
            _sessionState.update { it.copy(activeSession = updatedSession) }
            false
        }
    }

    private fun ensureDefaultPinsSeeded() {
        for (member in AppLockSessionState.DEFAULT_STAFF_PROFILES) {
            val key = pinHashPrefKey(member.businessId, member.userId)
            if (!securePrefs.contains(key)) {
                securePrefs.edit().putString(key, sha256Hex(member.pinHash)).apply()
            }
        }
    }

    private fun loadInitialSessionState(startLocked: Boolean): AppLockSessionState {
        val savedUserId = securePrefs.getString(KEY_ACTIVE_USER_ID, DatabaseModule.DEFAULT_CASHIER_USER_ID)
        val savedRoleName = securePrefs.getString(KEY_ACTIVE_ROLE, UserRole.CASHIER.name)
        val savedName = securePrefs.getString(KEY_ACTIVE_FULL_NAME, "Siti Rahma (Kasir Utama)")
        val savedMaxDisc = securePrefs.getLong(KEY_ACTIVE_MAX_DISCOUNT_RP, 50_000L)
        val savedLocked = if (securePrefs.contains(KEY_IS_LOCKED)) {
            securePrefs.getBoolean(KEY_IS_LOCKED, startLocked)
        } else {
            startLocked
        }

        val role = runCatching { UserRole.valueOf(savedRoleName ?: UserRole.CASHIER.name) }
            .getOrDefault(UserRole.CASHIER)
        val now = System.currentTimeMillis()

        val activeSession = ActiveCashierSession(
            membershipId = "m-$savedUserId",
            businessId = DatabaseModule.DEFAULT_BUSINESS_ID,
            userId = savedUserId ?: DatabaseModule.DEFAULT_CASHIER_USER_ID,
            fullName = savedName ?: "Siti Rahma (Kasir Utama)",
            email = if (role == UserRole.OWNER) "budi@nexbiz.id" else "siti.kasir@nexbiz.id",
            role = role,
            maxDiscountRp = savedMaxDisc,
            unlockedAtEpochMs = now,
            lastInteractionEpochMs = now
        )

        return AppLockSessionState(
            isLocked = savedLocked,
            activeSession = activeSession,
            availableStaff = AppLockSessionState.DEFAULT_STAFF_PROFILES,
            isBiometricAvailable = canAuthenticateWithBiometrics(appContext)
        )
    }

    private fun persistActiveSession(session: ActiveCashierSession, isLocked: Boolean) {
        securePrefs.edit()
            .putString(KEY_ACTIVE_USER_ID, session.userId)
            .putString(KEY_ACTIVE_FULL_NAME, session.fullName)
            .putString(KEY_ACTIVE_ROLE, session.role.name)
            .putLong(KEY_ACTIVE_MAX_DISCOUNT_RP, session.maxDiscountRp)
            .putBoolean(KEY_IS_LOCKED, isLocked)
            .apply()
    }

    companion object {
        private const val ENCRYPTED_PREFS_FILE_NAME = "nexbiz_pos_encrypted_session_prefs"
        private const val FALLBACK_PREFS_FILE_NAME = "nexbiz_pos_fallback_session_prefs"
        private const val KEY_ACTIVE_USER_ID = "active_user_id"
        private const val KEY_ACTIVE_FULL_NAME = "active_full_name"
        private const val KEY_ACTIVE_ROLE = "active_role"
        private const val KEY_ACTIVE_MAX_DISCOUNT_RP = "active_max_discount_rp"
        private const val KEY_IS_LOCKED = "is_session_locked"

        private const val MAX_PIN_ATTEMPTS_BEFORE_LOCKOUT = 5
        private const val LOCKOUT_DURATION_MS = 30_000L
        const val DEFAULT_INACTIVITY_TIMEOUT_MS = 15 * 60 * 1000L // 15 minutes

        private fun pinHashPrefKey(businessId: String, userId: String): String =
            "pin_sha256_${businessId}_$userId"

        fun sha256Hex(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(input.toByteArray(StandardCharsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }

        fun canAuthenticateWithBiometrics(context: Context): Boolean {
            return runCatching {
                val manager = BiometricManager.from(context)
                val status = manager.canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.BIOMETRIC_WEAK
                )
                status == BiometricManager.BIOMETRIC_SUCCESS
            }.getOrDefault(false)
        }

        private fun createEncryptedOrFallbackPrefs(context: Context): SharedPreferences {
            return runCatching {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    ENCRYPTED_PREFS_FILE_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            }.getOrElse {
                context.getSharedPreferences(FALLBACK_PREFS_FILE_NAME, Context.MODE_PRIVATE)
            }
        }
    }
}
