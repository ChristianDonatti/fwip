package io.github.christiandonatti.fwip.ui

import io.github.christiandonatti.fwip.R
import io.github.christiandonatti.fwip.str
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.christiandonatti.fwip.data.LibraryItem
import io.github.christiandonatti.fwip.remote.ComicVine
import io.github.christiandonatti.fwip.remote.CvIssue
import io.github.christiandonatti.fwip.remote.CvVolume
import io.github.christiandonatti.fwip.ui.theme.FwipColors
import kotlinx.coroutines.launch

/** What the metadata screens can ask the app to do. */
class MetaActions(
    val setKey: (String) -> Unit,
    val apply: (CvVolume, Map<String, CvIssue>, Int) -> Unit,
    val loadCredits: (String) -> Unit,
    val saveManual: (id: String, series: String, number: String, title: String, year: String) -> Unit,
    val clear: (List<String>) -> Unit,
    val identifyLibrary: () -> Unit,
    val dismissMessage: () -> Unit,
)

/** Where to get a Comic Vine key, and a field to paste it (tested before saving). */
@Composable
fun ComicVineKeyDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var key by remember { mutableStateOf(current) }
    var testing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    FwipDialog(onDismiss) {
        DialogTitle(str(R.string.cv_title), onDismiss)
        Text(str(R.string.cv_explain), style = MaterialTheme.typography.bodyMedium, color = FwipColors.TextSoft)
        Text(str(R.string.cv_steps), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
        Field(str(R.string.cv_key), key, { key = it.trim(); error = null }, "0123abcd…", keyboard = KeyboardType.Ascii)
        error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFFF8A80)) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            if (testing) CircularProgressIndicator(Modifier.size(22.dp), color = FwipColors.TextSoft, strokeWidth = 2.dp)
            PillButton(
                str(R.string.test_and_save),
                {
                    testing = true
                    scope.launch {
                        runCatching { ComicVine.searchVolumes(key, "Batman") }
                            .onSuccess { testing = false; onSave(key) }
                            .onFailure { testing = false; error = errorText(it) }
                    }
                },
                height = 48.dp,
                style = PillStyle.Amber,
                enabled = key.length >= 20 && !testing,
            )
        }
    }
}

/**
 * Finds a comic's series on Comic Vine. The search starts from the series name; picking a
 * result matches the comics by issue number. A single comic whose number is not in the
 * series gets the issue list to choose from.
 */
@Composable
fun IdentifyDialog(items: List<LibraryItem>, cvKey: String, actions: MetaActions, onDismiss: () -> Unit) {
    val first = items.first()
    val startName = (first.meta?.series ?: first.parsed.series).trim()
    var query by remember { mutableStateOf(startName) }
    var search by remember { mutableIntStateOf(1) }
    var results by remember { mutableStateOf<List<CvVolume>?>(null) }
    var picked by remember { mutableStateOf<CvVolume?>(null) }
    var issues by remember { mutableStateOf<List<CvIssue>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val years = items.mapNotNull { it.meta?.year ?: it.parsed.year }

    LaunchedEffect(search) {
        if (query.isBlank()) return@LaunchedEffect
        busy = true
        error = null
        runCatching { ComicVine.searchVolumes(cvKey, query.trim()) }
            .onSuccess { list ->
                // Exact names first, then the ones starting closest to the comics' years.
                val target = ComicVine.normalize(query)
                val year = years.minOrNull()
                results = list.sortedWith(
                    compareByDescending<CvVolume> { ComicVine.normalize(it.name) == target }
                        .thenBy { v -> if (year == null || v.startYear == null) 0 else kotlin.math.abs(year - v.startYear) }
                )
            }
            .onFailure { error = errorText(it) }
        busy = false
    }

    fun choose(volume: CvVolume) {
        picked = volume
        busy = true
        error = null
        scope.launch {
            runCatching { ComicVine.volumeIssues(cvKey, volume.id) }
                .onSuccess { list ->
                    busy = false
                    val matches = ComicVine.match(items.map { it.comic.id to it.issueNumber }, list)
                    if (items.size > 1 || matches.isNotEmpty()) {
                        actions.apply(volume, matches, items.size)
                        onDismiss()
                    } else {
                        issues = list
                    }
                }
                .onFailure { busy = false; error = errorText(it) }
        }
    }

    FwipDialog(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (issues != null) CircleIconButton(Icons.Filled.ArrowBack, str(R.string.back), { issues = null; picked = null }, size = 40.dp)
            Column(Modifier.weight(1f)) {
                Text(str(R.string.cv_find), style = MaterialTheme.typography.labelSmall, color = FwipColors.Amber)
                Text(
                    if (items.size == 1) first.title else str(R.string.cv_n_comics, first.series.name, items.size),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            CircleIconButton(Icons.Filled.Close, str(R.string.close), onDismiss, size = 40.dp)
        }

        val issueList = issues
        if (issueList != null) {
            Text(str(R.string.cv_pick_issue, picked?.label.orEmpty()), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
            issueList.forEach { issue ->
                ResultRow(
                    image = issue.image,
                    title = "#" + (issue.number ?: "?") + (issue.name?.let { " · $it" } ?: ""),
                    subtitle = issue.coverDate.orEmpty(),
                    onClick = {
                        val volume = picked ?: return@ResultRow
                        actions.apply(volume, mapOf(first.comic.id to issue), 1)
                        onDismiss()
                    },
                )
            }
            return@FwipDialog
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) {
                Field(str(R.string.cv_series_name), query, { query = it }, startName)
            }
            Column {
                Spacer(Modifier.height(22.dp))
                CircleIconButton(Icons.Filled.Search, str(R.string.search), { search++ }, size = 48.dp, filled = true)
            }
        }
        if (busy) {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(26.dp), color = FwipColors.TextSoft, strokeWidth = 2.dp)
            }
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFFF8A80)) }
        val list = results
        if (list != null && list.isEmpty() && !busy) {
            Text(str(R.string.cv_no_results), style = MaterialTheme.typography.bodyMedium, color = FwipColors.TextMuted)
        }
        list?.forEach { v ->
            ResultRow(
                image = v.image,
                title = v.label,
                subtitle = listOfNotNull(v.publisher, str(R.string.cv_issues, v.issueCount)).joinToString(" · "),
                onClick = { if (!busy) choose(v) },
            )
        }
    }
}

@Composable
private fun ResultRow(image: String?, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(FwipColors.Surface)
            .border(1.dp, FwipColors.Stroke, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClickLabel = title, onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(44.dp, 66.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF1E1F27))) {
            if (image != null) AsyncImage(image, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted, maxLines = 1)
        }
    }
}

/** Series, number, title and year typed in by hand. */
@Composable
fun EditInfoDialog(item: LibraryItem, actions: MetaActions, onDismiss: () -> Unit) {
    val meta = item.meta
    var series by remember { mutableStateOf(meta?.series ?: item.parsed.series) }
    var number by remember { mutableStateOf(meta?.number ?: item.parsed.number.orEmpty()) }
    var title by remember { mutableStateOf(meta?.title ?: item.parsed.title.orEmpty()) }
    var year by remember { mutableStateOf((meta?.year ?: item.parsed.year)?.toString().orEmpty()) }
    FwipDialog(onDismiss) {
        DialogTitle(str(R.string.edit_info), onDismiss)
        Text(item.comic.fileName, style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Field(str(R.string.info_series), series, { series = it }, "Batman")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) { Field(str(R.string.info_number), number, { number = it }, "1", keyboard = KeyboardType.Decimal) }
            Box(Modifier.weight(1f)) { Field(str(R.string.info_year), year, { year = it.filter(Char::isDigit).take(4) }, "2016", keyboard = KeyboardType.Number) }
        }
        Field(str(R.string.info_title), title, { title = it }, str(R.string.optional))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton(str(R.string.info_reset), { actions.clear(listOf(item.comic.id)); onDismiss() }, style = PillStyle.Ghost, height = 46.dp)
            Spacer(Modifier.weight(1f))
            PillButton(
                str(R.string.save),
                { actions.saveManual(item.comic.id, series, number, title, year); onDismiss() },
                style = PillStyle.Amber,
                height = 46.dp,
                enabled = series.isNotBlank(),
            )
        }
    }
}

/** Series the automatic search left for the user, each opening the search. */
@Composable
fun UnresolvedDialog(groups: List<List<LibraryItem>>, onPick: (List<LibraryItem>) -> Unit, onDismiss: () -> Unit) {
    FwipDialog(onDismiss) {
        DialogTitle(str(R.string.cv_review_title), onDismiss)
        Text(str(R.string.cv_review_explain), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
        groups.forEach { group ->
            SheetButton(
                group.first().series.name,
                { onPick(group) },
                trailing = { Text(countLabel(group.size), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted) },
            )
        }
    }
}

/** Floating note at the bottom of the library: a job in progress, or its result. */
@Composable
fun MetaStatus(work: io.github.christiandonatti.fwip.MetaWork?, message: String?, unresolved: Int, onReview: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    if (work == null && message == null) return
    Row(
        modifier
            .padding(16.dp)
            .clip(CircleShape)
            .background(Color(0xF0121318))
            .border(1.dp, FwipColors.Stroke, CircleShape)
            .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (work != null) {
            CircularProgressIndicator(Modifier.size(18.dp), color = FwipColors.Amber, strokeWidth = 2.dp)
            Text(
                str(R.string.cv_working, work.label, work.done + 1, work.total),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 12.dp).height(36.dp).padding(top = 9.dp),
            )
        } else {
            Text(message.orEmpty(), style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (unresolved > 0) PillButton(str(R.string.cv_review), onReview, height = 36.dp, style = PillStyle.Amber)
            CircleIconButton(Icons.Filled.Close, str(R.string.close), onDismiss, size = 36.dp)
        }
    }
}
