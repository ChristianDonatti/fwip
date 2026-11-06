package io.github.christiandonatti.fwip.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.christiandonatti.fwip.R
import io.github.christiandonatti.fwip.str

/**
 * What changed, shown once after an update, each with a way to try it. [batch] numbers grow with
 * every release that has news; the app remembers the last batch it showed.
 */
data class NewsItem(
    val batch: Int,
    val glyph: GroupGlyph,
    @StringRes val title: Int,
    @StringRes val text: Int,
    /** A Settings place to try it ("light/ink"), or null. */
    val target: String?,
)

object WhatsNew {
    val items = listOf(
        NewsItem(1, GroupGlyph.Folder, R.string.news_settings, R.string.news_settings_text, ""),
        NewsItem(1, GroupGlyph.Sun, R.string.news_light, R.string.news_light_text, "light/schedule"),
        NewsItem(1, GroupGlyph.Panels, R.string.news_ink, R.string.news_ink_text, "light/ink"),
        NewsItem(1, GroupGlyph.Chart, R.string.news_clock, R.string.news_clock_text, "light/clock"),
        NewsItem(1, GroupGlyph.Wifi, R.string.news_keys, R.string.news_keys_text, "comics/keys"),
        NewsItem(1, GroupGlyph.Info, R.string.news_dream, R.string.news_dream_text, "alerts/dream"),
        NewsItem(1, GroupGlyph.Sync, R.string.news_backup, R.string.news_backup_text, "sync/autobackup"),
        NewsItem(1, GroupGlyph.Disk, R.string.news_drop, R.string.news_drop_text, null),
    )

    val latest: Int get() = items.maxOf { it.batch }
}

/**
 * The news since the last time ([all] shows everything, from Settings › About). [onTry] opens a
 * Settings place; without it the buttons are left out.
 */
@Composable
fun WhatsNewDialog(all: Boolean, onDismiss: () -> Unit, since: Int = 0, onTry: ((String) -> Unit)? = null) {
    val list = WhatsNew.items.filter { all || it.batch > since }.sortedByDescending { it.batch }
    FwipDialog(onDismiss) {
        DialogTitle(str(R.string.whats_new), onDismiss)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            list.forEach { n ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(io.github.christiandonatti.fwip.ui.theme.FwipColors.Surface)
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).background(io.github.christiandonatti.fwip.ui.theme.FwipColors.Amber.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) { GroupIcon(n.glyph, io.github.christiandonatti.fwip.ui.theme.FwipColors.Amber, size = 20.dp) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(str(n.title), style = MaterialTheme.typography.labelLarge)
                        Text(str(n.text), style = MaterialTheme.typography.bodySmall, color = io.github.christiandonatti.fwip.ui.theme.FwipColors.TextMuted)
                        if (onTry != null && n.target != null) {
                            PillButton(str(R.string.news_try), { onTry(n.target) }, style = PillStyle.Ghost, height = 36.dp)
                        }
                    }
                }
            }
        }
        PillButton(str(R.string.news_done), onDismiss, style = PillStyle.Amber, height = 48.dp, modifier = Modifier.fillMaxWidth())
    }
}
