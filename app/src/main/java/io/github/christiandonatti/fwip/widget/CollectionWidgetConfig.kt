package io.github.christiandonatti.fwip.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import io.github.christiandonatti.fwip.R
import io.github.christiandonatti.fwip.data.LibraryRepository
import io.github.christiandonatti.fwip.str
import io.github.christiandonatti.fwip.ui.SheetButton
import io.github.christiandonatti.fwip.ui.StackIcon
import io.github.christiandonatti.fwip.ui.UiSettings
import io.github.christiandonatti.fwip.ui.theme.FwipColors
import io.github.christiandonatti.fwip.ui.theme.FwipTheme
import kotlinx.coroutines.launch

/** Picks the collection a collection widget follows (when placed, or by tapping its title). */
class CollectionWidgetConfig : ComponentActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Leaving without a choice cancels placing the widget.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        UiSettings.get(this).apply()
        val collections = LibraryRepository(this).collections()
        setContent {
            FwipTheme {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(FwipColors.Panel)
                        .systemBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(str(R.string.widget_collection_pick_title), style = MaterialTheme.typography.headlineSmall, color = FwipColors.Text)
                    if (collections.isEmpty()) {
                        Text(str(R.string.widget_collection_none), style = MaterialTheme.typography.bodyMedium, color = FwipColors.TextMuted)
                    }
                    collections.forEach { c ->
                        SheetButton(c.name, { pick(c.id) }, leading = { StackIcon(FwipColors.Text) })
                    }
                }
            }
        }
    }

    private fun pick(collectionId: String) {
        prefs(this).edit().putString("w$widgetId", collectionId).apply()
        lifecycleScope.launch {
            runCatching {
                val glanceId = GlanceAppWidgetManager(this@CollectionWidgetConfig).getGlanceIdBy(widgetId)
                CollectionWidget().update(this@CollectionWidgetConfig, glanceId)
            }
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
            finish()
        }
    }

    companion object {
        private fun prefs(context: Context) = context.getSharedPreferences("fwip_widgets", Context.MODE_PRIVATE)

        fun chosen(context: Context, widgetId: Int): String? = prefs(context).getString("w$widgetId", null)

        /** Opens this picker for a widget already on the home screen. */
        fun intent(context: Context, widgetId: Int): Intent =
            Intent(context, CollectionWidgetConfig::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .setData(android.net.Uri.parse("fwip://widget/$widgetId"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
