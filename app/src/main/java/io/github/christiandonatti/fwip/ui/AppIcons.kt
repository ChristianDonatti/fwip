package io.github.christiandonatti.fwip.ui

import io.github.christiandonatti.fwip.R
import io.github.christiandonatti.fwip.str
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/** Launcher icon choices. [Accent] follows the highlight colour. */
enum class AppIcon(@StringRes private val labelRes: Int, @StringRes private val descRes: Int, val variant: String?) {
    Amber(R.string.icon_amber, R.string.icon_amber_desc, "amber"),
    Night(R.string.theme_night, R.string.icon_night_desc, "night"),
    Paper(R.string.theme_paper, R.string.icon_paper_desc, "paper"),
    Oled(R.string.theme_oled, R.string.icon_oled_desc, "oled"),
    Comic(R.string.theme_comic, R.string.icon_comic_desc, "comic"),
    Manga(R.string.theme_manga, R.string.icon_manga_desc, "manga"),
    Accent(R.string.icon_accent, R.string.icon_accent_desc, null);

    val label: String get() = str(labelRes)
    val description: String get() = str(descRes)
}

/** Switches the launcher icon by enabling one activity alias and disabling the rest. */
object AppIcons {
    /** The variant drawn for [icon]; for the accent icon, the one closest to [accent]. */
    fun variantFor(icon: AppIcon, accent: Color): IconVariant {
        icon.variant?.let { id -> ICON_VARIANTS.firstOrNull { it.id == id }?.let { return it } }
        val target = accent.toArgb()
        return ICON_VARIANTS.filter { it.id.startsWith("a_") }.minBy { distance(it.bg, target) }
    }

    private fun distance(a: Int, b: Int): Int {
        val dr = (a shr 16 and 0xFF) - (b shr 16 and 0xFF)
        val dg = (a shr 8 and 0xFF) - (b shr 8 and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return dr * dr + dg * dg + db * db
    }

    fun apply(context: Context, icon: AppIcon, accent: Color) {
        val wanted = variantFor(icon, accent).id
        val pm = context.packageManager
        runCatching {
            // Enable the new one first so the app never has no launcher entry.
            ICON_VARIANTS.sortedBy { if (it.id == wanted) 0 else 1 }.forEach { v ->
                val component = ComponentName(context.packageName, "${context.packageName}.Icon_${v.id}")
                val on = v.id == wanted
                // "Default" means what the manifest says: only the amber icon starts enabled.
                val current = when (pm.getComponentEnabledSetting(component)) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> v.id == "amber"
                    else -> false
                }
                if (current != on) {
                    pm.setComponentEnabledSetting(
                        component,
                        if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP,
                    )
                }
            }
        }
    }
}
