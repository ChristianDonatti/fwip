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
import io.github.christiandonatti.fwip.ui.theme.FwipColors
import kotlinx.coroutines.launch

/** What the name, series and cover screens can ask the app to do. */
class MetaActions(
    val saveManual: (id: String, series: String, number: String, title: String, year: String, publisher: String) -> Unit,
    val renameSeries: (ids: List<String>, name: String, startYear: String, publisher: String) -> Unit,
    val clear: (List<String>) -> Unit,
    val coverFromPage: (id: String, page: Int) -> Unit,
    val coverFromImage: (id: String, uri: android.net.Uri) -> Unit,
    val resetCover: (id: String) -> Unit,
)

/** Series, number, title and year typed in by hand. */
@Composable
fun EditInfoDialog(item: LibraryItem, actions: MetaActions, onDismiss: () -> Unit) {
    val meta = item.meta
    var series by remember { mutableStateOf(meta?.series ?: item.parsed.series) }
    var number by remember { mutableStateOf(meta?.issue ?: item.parsed.number.orEmpty()) }
    var title by remember { mutableStateOf(meta?.title ?: item.parsed.title.orEmpty()) }
    var year by remember { mutableStateOf((meta?.year ?: item.parsed.year)?.toString().orEmpty()) }
    var publisher by remember { mutableStateOf(meta?.publisher.orEmpty()) }
    FwipDialog(onDismiss) {
        DialogTitle(str(R.string.edit_info), onDismiss)
        Text(item.comic.fileName, style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Field(str(R.string.info_series), series, { series = it }, "Batman")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) { Field(str(R.string.info_number), number, { number = it }, "1", keyboard = KeyboardType.Decimal) }
            Box(Modifier.weight(1f)) { Field(str(R.string.info_year), year, { year = it.filter(Char::isDigit).take(4) }, "2016", keyboard = KeyboardType.Number) }
        }
        Field(str(R.string.info_title), title, { title = it }, str(R.string.optional))
        Field(str(R.string.kind_publisher), publisher, { publisher = it }, "Marvel")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton(str(R.string.info_reset), { actions.clear(listOf(item.comic.id)); onDismiss() }, style = PillStyle.Ghost, height = 46.dp)
            Spacer(Modifier.weight(1f))
            PillButton(
                str(R.string.save),
                { actions.saveManual(item.comic.id, series, number, title, year, publisher); onDismiss() },
                style = PillStyle.Amber,
                height = 46.dp,
                enabled = series.isNotBlank(),
            )
        }
    }
}

/**
 * Gives a group of comics one series name: fixes a series the file names got wrong, or joins
 * issues that were split into several shelves. Each comic keeps its own number.
 */
@Composable
fun SeriesEditDialog(items: List<LibraryItem>, actions: MetaActions, onDismiss: () -> Unit) {
    val first = items.first()
    var name by remember { mutableStateOf(first.meta?.series ?: first.parsed.series) }
    var year by remember { mutableStateOf(first.meta?.volume?.takeIf { it > 1900 }?.toString().orEmpty()) }
    var publisher by remember { mutableStateOf(items.firstNotNullOfOrNull { it.meta?.publisher }.orEmpty()) }
    FwipDialog(onDismiss) {
        DialogTitle(str(R.string.series_edit), onDismiss)
        Text(str(R.string.series_edit_hint, items.size), style = MaterialTheme.typography.bodySmall, color = FwipColors.TextMuted)
        Field(str(R.string.info_series), name, { name = it }, "Batman")
        Field(str(R.string.series_start_year), year, { year = it.filter(Char::isDigit).take(4) }, str(R.string.optional), keyboard = KeyboardType.Number)
        Field(str(R.string.kind_publisher), publisher, { publisher = it }, "Marvel")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton(str(R.string.info_reset), { actions.clear(items.map { it.comic.id }); onDismiss() }, style = PillStyle.Ghost, height = 46.dp)
            Spacer(Modifier.weight(1f))
            PillButton(
                str(R.string.save),
                { actions.renameSeries(items.map { it.comic.id }, name, year, publisher); onDismiss() },
                style = PillStyle.Amber,
                height = 46.dp,
                enabled = name.isNotBlank(),
            )
        }
    }
}

/** Where a new cover comes from: a page of the comic, a picture on the device, or back to page one. */
@Composable
fun CoverDialog(item: LibraryItem, actions: MetaActions, onDismiss: () -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val pickImage = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) actions.coverFromImage(item.comic.id, uri)
        onDismiss()
    }
    if (picking) {
        io.github.christiandonatti.fwip.reader.PagePickerDialog(
            comic = item.comic,
            title = str(R.string.cover_pick_page_title),
            onPick = { page -> actions.coverFromPage(item.comic.id, page); onDismiss() },
            onDismiss = { picking = false },
        )
        return
    }
    FwipDialog(onDismiss) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverImage(item, Modifier.size(64.dp, 96.dp), corner = 10.dp, titleSize = 10)
            Text(str(R.string.cover_change), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            CircleIconButton(Icons.Filled.Close, str(R.string.close), onDismiss, size = 40.dp)
        }
        SheetButton(str(R.string.cover_from_page), { picking = true })
        SheetButton(str(R.string.cover_from_image), {
            pickImage.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
        })
        SheetButton(str(R.string.cover_reset), { actions.resetCover(item.comic.id); onDismiss() })
    }
}
