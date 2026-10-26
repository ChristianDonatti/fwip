package io.github.christiandonatti.fwip

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * Hosts every Fwip widget at a few sizes on one scrolling page (test builds only). Binding needs
 * "adb shell appwidget grantbind --package <app>" first; the emulator check does that.
 */
class WidgetPreviewActivity : Activity() {
    private lateinit var host: AppWidgetHost
    private val scope = MainScope()

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        host = AppWidgetHost(this, 4242)
        host.deleteHost()
        val manager = AppWidgetManager.getInstance(this)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(16f), dp(16f), dp(16f))
            setBackgroundColor(0xFF2B2D3A.toInt())
        }
        val providers = manager.getInstalledProvidersForPackage(packageName, null)
        // The collection widget follows the first collection.
        val firstCollection = io.github.christiandonatti.fwip.data.LibraryRepository(this).collections().firstOrNull()?.id
        val sizes = listOf(150f to 150f, 360f to 170f, 360f to 340f)
        for (info in providers.sortedBy { it.provider.className }) {
            val name = info.provider.className.substringAfterLast('.')
            val wanted = if (name == "ContinueReadingReceiver") sizes else listOf(360f to 190f)
            for ((w, h) in wanted) {
                val id = host.allocateAppWidgetId()
                if (!manager.bindAppWidgetIdIfAllowed(id, info.provider)) {
                    column.addView(TextView(this).apply { text = "$name: not allowed to bind"; setTextColor(-1) })
                    continue
                }
                if (firstCollection != null) {
                    getSharedPreferences("fwip_widgets", MODE_PRIVATE).edit().putString("w$id", firstCollection).apply()
                }
                column.addView(TextView(this).apply { text = "$name ${w.toInt()}x${h.toInt()}"; setTextColor(0xFFBBBBCC.toInt()); setPadding(0, dp(10f), 0, dp(4f)) })
                val view = host.createView(this, id, info)
                if (Build.VERSION.SDK_INT >= 31) view.updateAppWidgetSize(Bundle(), listOf(SizeF(w, h)))
                else view.updateAppWidgetSize(Bundle(), w.toInt(), h.toInt(), w.toInt(), h.toInt())
                column.addView(view, LinearLayout.LayoutParams(dp(w), dp(h)))
            }
        }
        setContentView(ScrollView(this).apply { addView(column) })
        scope.launch { io.github.christiandonatti.fwip.widget.FwipWidgets.refreshAll(this@WidgetPreviewActivity) }
    }

    override fun onStart() {
        super.onStart()
        host.startListening()
    }

    override fun onStop() {
        super.onStop()
        host.stopListening()
    }
}
