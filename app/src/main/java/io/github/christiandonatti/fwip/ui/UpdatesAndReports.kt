package io.github.christiandonatti.fwip.ui

import io.github.christiandonatti.fwip.R
import io.github.christiandonatti.fwip.str
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.christiandonatti.fwip.data.AppUpdate
import io.github.christiandonatti.fwip.data.UpdateCheck
import io.github.christiandonatti.fwip.data.Updates
import io.github.christiandonatti.fwip.ui.theme.FwipColors
import kotlinx.coroutines.launch

/** Opens a download or a web page in the browser. */
fun openLink(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun updateTitle(u: AppUpdate) =
    if (u.test) str(R.string.update_test_title, u.version) else str(R.string.update_title, u.version)

/** A small card at the bottom of the library when a newer Fwip is out. */
@Composable
fun UpdateBanner(update: AppUpdate, onNotes: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(
        modifier
            .padding(horizontal = 12.dp, vertical = 14.dp)
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(FwipColors.Panel.copy(alpha = 0.96f))
            .border(1.dp, FwipColors.Stroke, RoundedCornerShape(26.dp))
            .clickable(role = Role.Button, onClick = onNotes)
            .padding(start = 18.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(FwipColors.Amber))
        Column(Modifier.weight(1f)) {
            Text(updateTitle(update), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            update.notes.lineSequence().firstOrNull { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        PillButton(str(R.string.update_download), { openLink(context, update.apkUrl) }, style = PillStyle.Amber, height = 42.dp)
        CircleIconButton(Icons.Filled.Close, str(R.string.update_later), { Updates.dismiss(context) }, size = 40.dp)
    }
}

/** Settings > Updates: the installed version, the automatic check, and a manual one. */
@Composable
fun UpdatesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var auto by remember { mutableStateOf(Updates.autoEnabled(context)) }
    FwipDialog(onDismiss) {
        DialogTitle(str(R.string.updates), onDismiss)
        Text(
            str(R.string.update_installed, Updates.currentVersion),
            style = MaterialTheme.typography.bodyMedium,
            color = FwipColors.TextSoft,
        )
        Text(
            str(if (Updates.official) R.string.update_channel_official else R.string.update_channel_test),
            style = MaterialTheme.typography.bodySmall,
            color = FwipColors.TextMuted,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(FwipColors.Surface)
                .clickable(role = Role.Switch) { auto = !auto; Updates.setAuto(context, auto) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(str(R.string.update_auto), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Switch(
                checked = auto,
                onCheckedChange = { auto = it; Updates.setAuto(context, it) },
                colors = SwitchDefaults.colors(checkedTrackColor = FwipColors.Amber, checkedThumbColor = FwipColors.AmberInk),
            )
        }
        when (val m = Updates.manual) {
            UpdateCheck.Checking -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), color = FwipColors.TextSoft, strokeWidth = 2.dp)
                Text(str(R.string.update_checking), style = MaterialTheme.typography.bodyMedium, color = FwipColors.TextSoft)
            }
            UpdateCheck.UpToDate -> Text(str(R.string.update_none), style = MaterialTheme.typography.bodyMedium, color = FwipColors.TextSoft)
            is UpdateCheck.Failed -> Text(str(R.string.update_failed, m.message), style = MaterialTheme.typography.bodyMedium, color = FwipColors.Danger)
            is UpdateCheck.Found -> UpdateDetails(m.update)
            null -> {}
        }
        if (Updates.manual !is UpdateCheck.Found) {
            PillButton(
                str(R.string.update_check_now),
                { scope.launch { Updates.checkNow(context) } },
                style = PillStyle.Ghost,
                height = 48.dp,
                enabled = Updates.manual != UpdateCheck.Checking,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** What changed, and the download button. */
@Composable
fun UpdateDetails(update: AppUpdate) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(FwipColors.Surface)
            .border(1.dp, FwipColors.Stroke, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(updateTitle(update), style = MaterialTheme.typography.titleMedium)
        if (update.notes.isNotBlank()) {
            Text(update.notes, style = MaterialTheme.typography.bodySmall, color = FwipColors.TextSoft)
        }
        Text(str(R.string.update_how), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(str(R.string.update_download), { openLink(context, update.apkUrl) }, style = PillStyle.Amber, height = 46.dp)
            if (update.pageUrl.isNotBlank()) {
                PillButton(str(R.string.update_page), { openLink(context, update.pageUrl) }, style = PillStyle.Ghost, height = 46.dp)
            }
        }
    }
}

/** The notes of an update found automatically, opened from the banner. */
@Composable
fun UpdateNotesDialog(update: AppUpdate, onDismiss: () -> Unit) {
    FwipDialog(onDismiss) {
        DialogTitle(str(R.string.updates), onDismiss)
        UpdateDetails(update)
    }
}

/** Settings > Error report: what goes into it, and saving it. */
@Composable
fun ReportDialog(onSave: () -> Unit, onDismiss: () -> Unit) {
    FwipDialog(onDismiss) {
        DialogTitle(str(R.string.report_title), onDismiss)
        Text(str(R.string.report_explain), style = MaterialTheme.typography.bodyMedium, color = FwipColors.TextSoft)
        Text(str(R.string.report_contents), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
        PillButton(str(R.string.report_save), { onDismiss(); onSave() }, style = PillStyle.Amber, height = 48.dp, modifier = Modifier.fillMaxWidth())
    }
}

/** Shown once after the app closed by itself. */
@Composable
fun CrashDialog(onSave: () -> Unit, onDismiss: () -> Unit) {
    FwipDialog(onDismiss) {
        DialogTitle(str(R.string.crash_title), onDismiss)
        Text(str(R.string.crash_explain), style = MaterialTheme.typography.bodyMedium, color = FwipColors.TextSoft)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(str(R.string.not_now), onDismiss, style = PillStyle.Ghost, height = 46.dp, modifier = Modifier.weight(1f))
            PillButton(str(R.string.report_save), { onDismiss(); onSave() }, style = PillStyle.Amber, height = 46.dp, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(2.dp))
    }
}
