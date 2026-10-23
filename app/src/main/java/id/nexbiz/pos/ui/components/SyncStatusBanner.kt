package id.nexbiz.pos.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.nexbiz.pos.data.local.model.SyncStatus
import id.nexbiz.pos.ui.theme.DeepNavy900
import id.nexbiz.pos.ui.theme.ElectricCyan400
import id.nexbiz.pos.ui.theme.SyncAmber500
import id.nexbiz.pos.ui.theme.SyncCrimson500
import id.nexbiz.pos.ui.theme.SyncEmerald500
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SyncStatusBanner(
    syncStatus: SyncStatus,
    pendingOutboxCount: Int,
    conflictCount: Int,
    isSyncingNow: Boolean,
    lastSyncEpochMs: Long?,
    statusMessage: String?,
    onTriggerSync: () -> Unit,
    onInspectOutbox: () -> Unit,
    modifier: Modifier = Modifier
) {
    val effectiveStatus = when {
        isSyncingNow -> SyncStatus.SYNCING
        conflictCount > 0 -> SyncStatus.CONFLICT
        syncStatus == SyncStatus.FAILED -> SyncStatus.FAILED
        pendingOutboxCount > 0 -> SyncStatus.PENDING
        else -> SyncStatus.SYNCED
    }

    val badgeColor: Color = when (effectiveStatus) {
        SyncStatus.SYNCED -> SyncEmerald500
        SyncStatus.SYNCING -> ElectricCyan400
        SyncStatus.PENDING, SyncStatus.LOCAL_ONLY -> SyncAmber500
        SyncStatus.FAILED, SyncStatus.CONFLICT -> SyncCrimson500
    }

    val statusIcon = when (effectiveStatus) {
        SyncStatus.SYNCED -> Icons.Default.CloudDone
        SyncStatus.SYNCING -> Icons.Default.CloudSync
        SyncStatus.PENDING -> Icons.Default.CloudSync
        SyncStatus.LOCAL_ONLY -> Icons.Default.CloudOff
        SyncStatus.FAILED -> Icons.Default.ErrorOutline
        SyncStatus.CONFLICT -> Icons.Default.WarningAmber
    }

    val titleText = when (effectiveStatus) {
        SyncStatus.SYNCED -> "Semua Data Tersinkronisasi (WAL Aktif)"
        SyncStatus.SYNCING -> "Menyinkronkan Outbox ke Cloud..."
        SyncStatus.PENDING -> "$pendingOutboxCount Transaksi Offline di Outbox"
        SyncStatus.LOCAL_ONLY -> "Mode Lokal Aktif (Offline-First)"
        SyncStatus.FAILED -> "Gagal Sinkronisasi ($pendingOutboxCount Antrean Backoff)"
        SyncStatus.CONFLICT -> "$conflictCount Konflik Stok Multi-Perangkat Terdeteksi"
    }

    val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.forLanguageTag("id-ID"))
    val subtitleText = statusMessage ?: buildString {
        append("Status: ${effectiveStatus.name}")
        if (lastSyncEpochMs != null && lastSyncEpochMs > 0L) {
            append(" • Sync Terakhir ${timeFormatter.format(Date(lastSyncEpochMs))}")
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onInspectOutbox)
            .testTag("sync_status_banner"),
        color = DeepNavy900,
        tonalElevation = 4.dp,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(badgeColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (effectiveStatus == SyncStatus.SYNCING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = badgeColor,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = effectiveStatus.name,
                            tint = badgeColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        AnimatedVisibility(visible = pendingOutboxCount > 0) {
                            Surface(
                                color = badgeColor.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "OUTBOX: $pendingOutboxCount",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = badgeColor,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFCBD5E1)
                    )
                }
            }

            IconButton(
                onClick = onTriggerSync,
                enabled = !isSyncingNow,
                modifier = Modifier.testTag("sync_now_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = "Sinkronkan Sekarang",
                    tint = ElectricCyan400
                )
            }
        }
    }
}
