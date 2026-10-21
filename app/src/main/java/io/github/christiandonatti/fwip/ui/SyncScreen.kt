package io.github.christiandonatti.fwip.ui

import io.github.christiandonatti.fwip.R
import io.github.christiandonatti.fwip.str
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.christiandonatti.fwip.SyncInfo
import io.github.christiandonatti.fwip.remote.NetUris
import io.github.christiandonatti.fwip.remote.Server
import io.github.christiandonatti.fwip.ui.theme.FwipColors

/** What the sync and backup screen can ask the app to do. */
class SyncActions(
    val pickLocal: () -> Unit,
    val useNetwork: (Uri) -> Unit,
    val stop: () -> Unit,
    val syncNow: () -> Unit,
    val saveBackup: () -> Unit,
    val restoreBackup: () -> Unit,
)

/**
 * Sync between devices through a shared folder, plus backup files. Each device writes its
 * own small file in a hidden ".fwip" folder there and reads the others'.
 */
@Composable
fun SyncDialog(
    info: SyncInfo,
    folders: List<Uri>,
    servers: List<Server>,
    actions: SyncActions,
    onDismiss: () -> Unit,
) {
    FwipDialog(onDismiss) {
        DialogTitle(str(R.string.sync_title), onDismiss)
        // FwipDialog already scrolls.
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(str(R.string.sync_explain), style = MaterialTheme.typography.bodyMedium, color = FwipColors.TextSoft)

            val place = info.place
            if (place == null) {
                Label(str(R.string.sync_where))
                SheetButton(str(R.string.sync_pick_local), actions.pickLocal, leading = { FolderIcon(FwipColors.Text) })
                // Network folders already in the library first, then the root of each network server.
                val netFolders = folders.filter(NetUris::isNet)
                val roots = servers.filter { it.kind.isFolder }
                    .map { NetUris.of(it.id, "") }
                    .filter { r -> netFolders.none { it.toString().trimEnd('/') == r.toString().trimEnd('/') } }
                (netFolders + roots).forEach { uri ->
                    SheetButton(str(R.string.sync_use, folderLabel(uri, servers)), { actions.useNetwork(uri) }, leading = { FolderIcon(FwipColors.Amber) })
                }
                Text(str(R.string.sync_tip), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(FwipColors.Surface)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(str(R.string.sync_with), style = MaterialTheme.typography.labelSmall, color = FwipColors.Amber)
                    Text(folderLabel(place, servers), style = MaterialTheme.typography.labelLarge)
                    Text(
                        if (info.lastSync > 0) str(R.string.sync_last, android.text.format.DateUtils.getRelativeTimeSpanString(info.lastSync).toString())
                        else str(R.string.sync_never),
                        style = MaterialTheme.typography.bodySmall,
                        color = FwipColors.TextMuted,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (info.devices.isEmpty()) str(R.string.sync_no_devices)
                        else str(R.string.sync_devices, info.devices.joinToString(", ") { it.name.ifBlank { "?" } }),
                        style = MaterialTheme.typography.bodySmall,
                        color = FwipColors.TextSoft,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PillButton(
                        str(R.string.sync_now),
                        actions.syncNow,
                        style = PillStyle.Amber,
                        height = 46.dp,
                        enabled = !info.busy,
                        modifier = Modifier.weight(1f),
                        leading = {
                            if (info.busy) CircularProgressIndicator(Modifier.size(16.dp), color = FwipColors.AmberInk, strokeWidth = 2.dp)
                            else Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                        },
                    )
                    PillButton(str(R.string.sync_stop), actions.stop, style = PillStyle.Ghost, height = 46.dp)
                }
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(FwipColors.Stroke))
            Label(str(R.string.backup_title))
            Text(str(R.string.backup_explain), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
            SheetButton(str(R.string.backup_save), actions.saveBackup)
            SheetButton(str(R.string.backup_restore), actions.restoreBackup)

            info.message?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = if (info.failed) Color(0xFFFF8A80) else FwipColors.Amber)
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = FwipColors.Amber)
}
