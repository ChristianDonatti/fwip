package io.github.christiandonatti.fwip.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.christiandonatti.fwip.R
import io.github.christiandonatti.fwip.data.WifiTransfer
import io.github.christiandonatti.fwip.remote.NetUris
import io.github.christiandonatti.fwip.remote.Server
import io.github.christiandonatti.fwip.str
import io.github.christiandonatti.fwip.ui.theme.FwipColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Receives comics from a computer's browser while open: shows the address to type there and the
 * files as they arrive. Closing the screen stops it.
 */
@Composable
fun WifiScreen(folders: List<Uri>, servers: List<Server>, gutter: Dp, onReceived: () -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    // Folders on this device only; network folders already have their own way in.
    val places = folders.filter { !NetUris.isNet(it) && it.scheme != "file" }
    var target by remember { mutableStateOf(places.firstOrNull()) }
    val status by WifiTransfer.status.collectAsState()
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(Unit) {
        WifiTransfer.onReceived = {
            // One library scan after a burst of files.
            pending?.cancel()
            pending = scope.launch { delay(1500); onReceived() }
        }
        WifiTransfer.start(context, target)
        onDispose {
            WifiTransfer.onReceived = null
            WifiTransfer.stop()
        }
    }
    LaunchedEffect(target) { WifiTransfer.setTarget(target) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = gutter, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircleIconButton(Icons.Filled.ArrowBack, str(R.string.back), onBack, size = 44.dp)
            Text(str(R.string.wifi_title), style = MaterialTheme.typography.headlineSmall)
            Text(str(R.string.wifi_explain), style = MaterialTheme.typography.bodyMedium, color = FwipColors.TextSoft)

            Column(
                Modifier
                    .fillMaxWidth()
                    .background(FwipColors.Surface, RoundedCornerShape(22.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(str(R.string.wifi_open_this), style = MaterialTheme.typography.labelSmall, color = FwipColors.Amber)
                val address = status.address
                when {
                    address != null -> SelectionContainer {
                        Text(address, style = MaterialTheme.typography.headlineSmall, color = FwipColors.Text)
                    }
                    status.running -> Text(str(R.string.wifi_no_network), style = MaterialTheme.typography.bodyLarge, color = FwipColors.Danger)
                    else -> Text(status.error ?: str(R.string.wifi_starting), style = MaterialTheme.typography.bodyLarge, color = FwipColors.TextMuted)
                }
                Text(str(R.string.wifi_same_network), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
            }

            Text(str(R.string.wifi_save_in), style = MaterialTheme.typography.labelLarge, color = FwipColors.TextSoft)
            places.forEach { uri ->
                SheetButton(
                    folderLabel(uri, servers),
                    { target = uri },
                    leading = { FolderIcon(if (target == uri) FwipColors.Amber else FwipColors.TextSoft) },
                    trailing = if (target == uri) ({ Icon(Icons.Filled.Check, null) }) else null,
                )
            }
            SheetButton(
                str(R.string.downloads_folder),
                { target = null },
                leading = { FolderIcon(if (target == null) FwipColors.Amber else FwipColors.TextSoft) },
                trailing = if (target == null) ({ Icon(Icons.Filled.Check, null) }) else null,
            )

            status.current?.let { name ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(str(R.string.wifi_receiving, name), style = MaterialTheme.typography.bodyMedium)
                    Box(Modifier.fillMaxWidth().height(4.dp).background(FwipColors.Stroke, RoundedCornerShape(2.dp))) {
                        Box(Modifier.fillMaxWidth(status.progress.coerceIn(0f, 1f)).height(4.dp).background(FwipColors.Amber, RoundedCornerShape(2.dp)))
                    }
                }
            }
            if (status.received.isNotEmpty()) {
                Text(str(R.string.wifi_received, status.received.size), style = MaterialTheme.typography.labelLarge, color = FwipColors.TextSoft)
                status.received.asReversed().take(30).forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted, maxLines = 1)
                }
            }
            Box(Modifier.height(40.dp))
        }
    }
}
