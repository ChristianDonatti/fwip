package io.github.christiandonatti.fwip.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.christiandonatti.fwip.ui.theme.Accent
import io.github.christiandonatti.fwip.ui.theme.FwipColors
import io.github.christiandonatti.fwip.ui.theme.Palette
import io.github.christiandonatti.fwip.ui.theme.ThemeMode

/** Look of the library: theme, accent colour, cover size, and whether the welcome was shown. */
class UiSettings private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("fwip_ui", Context.MODE_PRIVATE)

    var theme by mutableStateOf(runCatching { ThemeMode.valueOf(prefs.getString("theme", null) ?: "") }.getOrDefault(ThemeMode.Dark))
        private set
    var accent by mutableStateOf(runCatching { Accent.valueOf(prefs.getString("accent", null) ?: "") }.getOrDefault(Accent.Amber))
        private set
    /** Cover size in grids, changed by pinching: 1 = default. */
    var coverScale by mutableFloatStateOf(prefs.getFloat("coverScale", 1f))
        private set
    var welcomed by mutableStateOf(prefs.getBoolean("welcomed", false))
        private set

    fun updateTheme(value: ThemeMode) { theme = value; prefs.edit().putString("theme", value.name).apply() }
    fun updateAccent(value: Accent) { accent = value; prefs.edit().putString("accent", value.name).apply() }

    /** Live while the fingers move; [save] once they lift. */
    fun scaleCovers(factor: Float) { coverScale = (coverScale * factor).coerceIn(MIN_SCALE, MAX_SCALE) }
    fun saveCoverScale() { prefs.edit().putFloat("coverScale", coverScale).apply() }

    fun setWelcomed() { welcomed = true; prefs.edit().putBoolean("welcomed", true).apply() }

    /** Applies the saved look before the first frame, so a light theme never flashes dark. */
    fun applyNow(context: Context) {
        val light = when (theme) {
            ThemeMode.Dark -> false
            ThemeMode.Light -> true
            ThemeMode.System -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES
        }
        FwipColors.set(if (light) Palette.light(accent) else Palette.dark(accent), accent)
    }

    companion object {
        const val MIN_SCALE = 0.6f
        const val MAX_SCALE = 2.2f

        @Volatile private var instance: UiSettings? = null
        fun get(context: Context): UiSettings =
            instance ?: synchronized(this) { instance ?: UiSettings(context.applicationContext).also { instance = it } }
    }
}
