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
import androidx.compose.foundation.clickable
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import io.github.christiandonatti.fwip.data.NasUpload
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
    /** Copying this device's comics to a NAS folder: turn on (with the folder) or off. */
    val uploadTo: (Uri?) -> Unit = {},
    val uploadNow: () -> Unit = {},
    val uploadWifi: (Boolean) -> Unit = {},
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
            UploadSection(folders, servers, actions)

            Box(Modifier.fillMaxWidth().height(1.dp).background(FwipColors.Stroke))
            Label(str(R.string.backup_title))
            Text(str(R.string.backup_explain), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
            SheetButton(str(R.string.backup_save), actions.saveBackup)
            SheetButton(str(R.string.backup_restore), actions.restoreBackup)

            info.message?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = if (info.failed) FwipColors.Danger else FwipColors.Amber)
            }
        }
    }
}

/** Comics put on this device go to a NAS folder by themselves. */
@Composable
private fun UploadSection(folders: List<Uri>, servers: List<Server>, actions: SyncActions) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val status by NasUpload.status.collectAsState()
    // Saved settings and the last result, read again whenever the status says they changed.
    val target = remember(status.rev) { NasUpload.target(context) }
    val wifi = remember(status.rev) { NasUpload.wifiOnly(context) }
    val last = remember(status.rev) { NasUpload.lastRun(context) }
    val lastCount = remember(status.rev) { NasUpload.lastSent(context) }
    val error = remember(status.rev) { NasUpload.lastError(context) }

    Label(str(R.string.upload_title))
    Text(str(R.string.upload_explain), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
    if (target == null) {
        val netFolders = folders.filter(NetUris::isNet)
        val roots = servers.filter { it.kind.isFolder }
            .map { NetUris.of(it.id, "") }
            .filter { r -> netFolders.none { it.toString().trimEnd('/') == r.toString().trimEnd('/') } }
        if (netFolders.isEmpty() && roots.isEmpty()) {
            Text(str(R.string.upload_no_net), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextSoft)
        }
        (netFolders + roots).forEach { uri ->
            SheetButton(str(R.string.upload_to, folderLabel(uri, servers)), { actions.uploadTo(uri) }, leading = { FolderIcon(FwipColors.Amber) })
        }
        if (netFolders.isNotEmpty() || roots.isNotEmpty()) {
            Text(str(R.string.upload_tip), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
        }
        return
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(FwipColors.Surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(str(R.string.upload_with), style = MaterialTheme.typography.labelSmall, color = FwipColors.Amber)
        Text(folderLabel(target, servers), style = MaterialTheme.typography.labelLarge)
        val line = when {
            status.running && status.total > 0 -> str(R.string.upload_progress, status.done + 1, status.total, status.current.orEmpty())
            status.running -> str(R.string.upload_checking)
            status.queued -> str(if (wifi) R.string.upload_queued_wifi else R.string.upload_queued)
            last > 0 -> str(R.string.upload_last, android.text.format.DateUtils.getRelativeTimeSpanString(last).toString(), lastCount)
            else -> str(R.string.upload_never)
        }
        Text(line, style = MaterialTheme.typography.bodySmall, color = FwipColors.TextSoft, maxLines = 2)
        if (status.running && status.total > 0) {
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(FwipColors.Stroke)) {
                Box(Modifier.fillMaxWidth(status.progress.coerceIn(0f, 1f)).height(4.dp).background(FwipColors.Amber))
            }
        }
        if (error != null && !status.running) {
            Text(str(R.string.upload_failed, error), style = MaterialTheme.typography.bodySmall, color = FwipColors.Danger)
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth().clickable(role = androidx.compose.ui.semantics.Role.Switch) { actions.uploadWifi(!wifi) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(str(R.string.upload_wifi), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Switch(
                checked = wifi,
                onCheckedChange = actions.uploadWifi,
                colors = SwitchDefaults.colors(checkedTrackColor = FwipColors.Amber, checkedThumbColor = FwipColors.AmberInk),
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        PillButton(
            str(R.string.upload_now),
            actions.uploadNow,
            style = PillStyle.Amber,
            height = 46.dp,
            enabled = !status.running,
            modifier = Modifier.weight(1f),
            leading = {
                if (status.running) CircularProgressIndicator(Modifier.size(16.dp), color = FwipColors.AmberInk, strokeWidth = 2.dp)
                else Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
            },
        )
        PillButton(str(R.string.upload_stop), { actions.uploadTo(null) }, style = PillStyle.Ghost, height = 46.dp)
    }
}

@Composable
private fun Label(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = FwipColors.Amber)
}
