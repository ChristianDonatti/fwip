package io.github.christiandonatti.fwip.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.christiandonatti.fwip.LibraryState
import io.github.christiandonatti.fwip.R
import io.github.christiandonatti.fwip.data.LibraryItem
import io.github.christiandonatti.fwip.str
import io.github.christiandonatti.fwip.ui.theme.FwipColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** Space settings: downloaded comics can clean themselves up once finished. */
object StorageSettings {
    private fun prefs(context: Context) = context.getSharedPreferences("fwip_storage", Context.MODE_PRIVATE)
    fun autoClean(context: Context): Boolean = prefs(context).getBoolean("autoClean", false)
    fun setAutoClean(context: Context, on: Boolean) = prefs(context).edit().putBoolean("autoClean", on).apply()
}

internal fun sizeLabel(bytes: Long): String = when {
    bytes < 1024L * 1024 * 1024 -> fileSizeLabel(bytes).ifBlank { "0 KB" }
    else -> String.format(Locale.US, "%.1f GB", bytes / 1024.0 / 1024.0 / 1024.0).replace('.', ',')
}

private fun folderSize(dir: File): Long = dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

/** How much room downloads, covers and temporary files take, and ways to get it back. */
@Composable
fun StorageScreen(
    state: LibraryState,
    gutter: Dp,
    onDelete: (List<String>) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val downloads = state.items.filter { it.comic.uri.scheme == "file" }.sortedByDescending { it.comic.size }
    val finished = downloads.filter { it.progress?.finished == true }
    val sizes by produceState(Triple(-1L, -1L, -1L), refresh, state.items.size) {
        value = withContext(Dispatchers.IO) {
            Triple(
                folderSize(File(context.filesDir, "Baixadas")),
                folderSize(File(context.filesDir, "covers")) + folderSize(File(context.filesDir, "covers_custom")),
                folderSize(context.cacheDir),
            )
        }
    }
    var autoClean by remember { mutableStateOf(StorageSettings.autoClean(context)) }
    var confirm by remember { mutableStateOf<List<LibraryItem>?>(null) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = gutter, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircleIconButton(Icons.Filled.ArrowBack, str(R.string.back), onBack, size = 44.dp)
            Text(str(R.string.storage_title), style = MaterialTheme.typography.headlineSmall)

            Card {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Figure(str(R.string.storage_downloads), sizes.first, Modifier.weight(1f))
                    Figure(str(R.string.storage_covers), sizes.second, Modifier.weight(1f))
                    Figure(str(R.string.storage_cache), sizes.third, Modifier.weight(1f))
                }
                PillButton(
                    str(R.string.storage_clear_cache),
                    {
                        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                        refresh++
                    },
                    style = PillStyle.Ghost,
                    height = 44.dp,
                )
            }

            Card {
                Text(str(R.string.storage_downloads), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (downloads.isEmpty()) str(R.string.storage_no_downloads) else str(R.string.storage_n_downloads, downloads.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = FwipColors.TextMuted,
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Switch) {
                            autoClean = !autoClean
                            StorageSettings.setAutoClean(context, autoClean)
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(str(R.string.storage_auto), style = MaterialTheme.typography.labelLarge)
                        Text(str(R.string.storage_auto_hint), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
                    }
                    Switch(
                        checked = autoClean,
                        onCheckedChange = { autoClean = it; StorageSettings.setAutoClean(context, it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = FwipColors.Amber, checkedThumbColor = FwipColors.AmberInk),
                    )
                }
                if (finished.isNotEmpty()) {
                    PillButton(
                        str(R.string.storage_delete_finished, finished.size, sizeLabel(finished.sumOf { it.comic.size })),
                        { confirm = finished },
                        style = PillStyle.Amber,
                        height = 46.dp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                downloads.forEach { item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(FwipColors.Panel.copy(alpha = 0.5f))
                            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val bits = listOfNotNull(
                                sizeLabel(item.comic.size),
                                if (item.progress?.finished == true) str(R.string.smart_read) else null,
                                if (item.alsoOnNas) str(R.string.also_on_nas) else null,
                            )
                            Text(bits.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted, maxLines = 1)
                        }
                        CircleIconButton(Icons.Filled.Delete, str(R.string.delete), { confirm = listOf(item) }, size = 40.dp)
                    }
                }
            }
            Box(Modifier.height(40.dp))
        }
    }

    confirm?.let { list ->
        FwipDialog({ confirm = null }) {
            DialogTitle(if (list.size == 1) str(R.string.delete_one_title) else str(R.string.delete_n_title, list.size), { confirm = null })
            Text(str(R.string.storage_delete_explain), style = MaterialTheme.typography.bodyMedium, color = FwipColors.TextSoft)
            if (list.any { !it.alsoOnNas }) {
                Text(str(R.string.storage_not_on_nas), style = MaterialTheme.typography.bodyMedium, color = FwipColors.Danger)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(str(R.string.cancel), { confirm = null }, style = PillStyle.Ghost, height = 46.dp, modifier = Modifier.weight(1f))
                PillButton(str(R.string.delete), {
                    onDelete(list.map { it.comic.id })
                    confirm = null
                    refresh++
                }, style = PillStyle.Amber, height = 46.dp, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(FwipColors.Surface, RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
private fun Figure(label: String, bytes: Long, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(if (bytes < 0) "…" else sizeLabel(bytes), style = MaterialTheme.typography.titleLarge, color = FwipColors.Amber, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted, maxLines = 2)
    }
}
