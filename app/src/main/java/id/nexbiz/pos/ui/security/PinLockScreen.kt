package id.nexbiz.pos.ui.security

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import id.nexbiz.pos.ui.theme.DeepNavy800
import id.nexbiz.pos.ui.theme.DeepNavy900
import id.nexbiz.pos.ui.theme.DeepNavy950
import id.nexbiz.pos.ui.theme.ElectricCyan400
import id.nexbiz.pos.ui.theme.SyncAmber500
import id.nexbiz.pos.ui.theme.SyncCrimson500
import id.nexbiz.pos.ui.theme.SyncEmerald500

@Composable
fun PinLockScreen(
    sessionState: AppLockSessionState,
    onSubmitPin: (pin: String, targetUserId: String?) -> Boolean,
    onRequestBiometricUnlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    var enteredPin by remember { mutableStateOf("") }
    var selectedUserId by remember {
        mutableStateOf<String?>(sessionState.activeSession.userId)
    }

    fun handleDigitTap(digit: String) {
        if (enteredPin.length < 6) {
            val nextPin = enteredPin + digit
            enteredPin = nextPin
            if (nextPin.length == 6) {
                val unlocked = onSubmitPin(nextPin, selectedUserId)
                if (!unlocked) {
                    enteredPin = ""
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DeepNavy950)
            .padding(20.dp)
            .testTag("pin_lock_screen"),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = DeepNavy900),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = ElectricCyan400.copy(alpha = 0.16f),
                    modifier = Modifier.size(60.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Terminal Terkunci",
                            tint = ElectricCyan400,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Kunci Sesi Kasir NEXBIZ POS",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Masukkan 6-digit PIN Kasir (123456) atau Owner (888888)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center
                    )
                }

                // Staff Selector Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sessionState.availableStaff.forEach { member ->
                        val isSelected = selectedUserId == member.userId
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedUserId = member.userId
                                enteredPin = ""
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = {
                                Text("${member.fullName} [${member.role.name}]")
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricCyan400,
                                selectedLabelColor = DeepNavy950,
                                selectedLeadingIconColor = DeepNavy950
                            ),
                            modifier = Modifier.testTag("staff_chip_${member.role.name.lowercase()}")
                        )
                    }
                }

                // 6-Digit PIN Dots Display
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.testTag("pin_dots_indicator")
                ) {
                    repeat(6) { index ->
                        val isFilled = index < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isFilled) ElectricCyan400 else DeepNavy800
                                )
                                .border(
                                    width = 1.5.dp,
                                    color = if (isFilled) ElectricCyan400 else Color(0xFF475569),
                                    shape = CircleShape
                                )
                        )
                    }
                }

                if (!sessionState.authFeedbackMessage.isNullOrBlank()) {
                    Text(
                        text = sessionState.authFeedbackMessage,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (sessionState.failedAttemptsCount > 0) {
                            SyncCrimson500
                        } else {
                            SyncAmber500
                        },
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("pin_feedback_message")
                    )
                }

                // Numeric PIN Keypad
                val keypadRows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9")
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    keypadRows.forEach { rowDigits ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowDigits.forEach { digit ->
                                PinPadKeyButton(
                                    label = digit,
                                    onClick = { handleDigitTap(digit) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("pin_key_$digit")
                                )
                            }
                        }
                    }

                    // Bottom Row: Biometric / Clear, 0, Backspace
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(onClick = onRequestBiometricUnlock)
                                .testTag("pin_biometric_button"),
                            color = DeepNavy800,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = "Buka dengan Biometrik",
                                    tint = ElectricCyan400,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }

                        PinPadKeyButton(
                            label = "0",
                            onClick = { handleDigitTap("0") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pin_key_0")
                        )

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    if (enteredPin.isNotEmpty()) {
                                        enteredPin = enteredPin.dropLast(1)
                                    }
                                }
                                .testTag("pin_key_backspace"),
                            color = DeepNavy800,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                                    contentDescription = "Hapus Digit",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }

                // Quick Demo PIN Fill Buttons for Fast Testing
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            selectedUserId = sessionState.availableStaff
                                .firstOrNull { it.pinHash == "123456" }?.userId
                            onSubmitPin("123456", selectedUserId)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_unlock_cashier_button")
                    ) {
                        Text("Kasir (123456)", color = ElectricCyan400)
                    }
                    Button(
                        onClick = {
                            selectedUserId = sessionState.availableStaff
                                .firstOrNull { it.pinHash == "888888" }?.userId
                            onSubmitPin("888888", selectedUserId)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SyncEmerald500,
                            contentColor = DeepNavy950
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_unlock_owner_button")
                    ) {
                        Text("Owner (888888)", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PinPadKeyButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = DeepNavy800,
        shape = RoundedCornerShape(14.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

/**
 * Supervisor PIN Elevation Dialog shown when a Cashier attempts a restricted action
 * (`VOID_SALE`, `FINANCIAL_REPORTS`, `STOCK_OPNAME`, or discount above ceiling).
 */
@Composable
fun SupervisorAuthorizationDialog(
    action: PosRestrictedAction,
    denialMessage: String,
    onVerifySupervisorPin: (String) -> PermissionCheckOutcome,
    onAuthorized: () -> Unit,
    onDismiss: () -> Unit
) {
    var supervisorPin by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("supervisor_authorization_dialog"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DeepNavy900)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = SyncAmber500
                    )
                    Text(
                        text = "Otorisasi Supervisor / Owner",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = denialMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFCBD5E1)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { supervisorPin = "888888" },
                        modifier = Modifier.testTag("fill_owner_override_pin")
                    ) {
                        Text("Isi PIN Owner (888888)", color = ElectricCyan400)
                    }
                }

                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = SyncCrimson500
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Batal")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val pinToCheck = supervisorPin.ifBlank { "888888" }
                            when (val res = onVerifySupervisorPin(pinToCheck)) {
                                is PermissionCheckOutcome.Granted -> onAuthorized()
                                is PermissionCheckOutcome.Denied -> errorText = res.reasonMessage
                            }
                        },
                        modifier = Modifier.testTag("confirm_supervisor_override_button")
                    ) {
                        Text("Otorisasi")
                    }
                }
            }
        }
    }
}
