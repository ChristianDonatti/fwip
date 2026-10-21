package io.github.christiandonatti.fwip.reader

import io.github.christiandonatti.fwip.R
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableSharedFlow

enum class TurnStyle(@androidx.annotation.StringRes private val labelRes: Int) {
    Curl(R.string.turn_curl), Flip(R.string.turn_flip), Slide(R.string.turn_slide);

    val label: String get() = str(labelRes)
}

enum class SpreadMode(@androidx.annotation.StringRes private val labelRes: Int) {
    Auto(R.string.spread_auto), Single(R.string.spread_single), Double(R.string.spread_double);

    val label: String get() = str(labelRes)
}

/** Reader preferences, kept as Compose state so the screen updates the moment one changes. */
class ReaderSettings(context: Context) {
    private val prefs = context.getSharedPreferences("fwip_reader", Context.MODE_PRIVATE)

    var mangaMode by mutableStateOf(prefs.getBoolean("manga", false))
        private set
    // Stored under a new key so everyone starts with the paper curl once it exists.
    var turnStyle by mutableStateOf(runCatching { TurnStyle.valueOf(prefs.getString("turn2", null) ?: "") }.getOrDefault(TurnStyle.Curl))
        private set
    var spreadMode by mutableStateOf(runCatching { SpreadMode.valueOf(prefs.getString("spread", null) ?: "") }.getOrDefault(SpreadMode.Auto))
        private set
    var volumeKeys by mutableStateOf(prefs.getBoolean("volume", true))
        private set
    var cropMargins by mutableStateOf(prefs.getBoolean("crop", true))
        private set

    fun updateManga(value: Boolean) { mangaMode = value; prefs.edit().putBoolean("manga", value).apply() }
    fun updateTurn(value: TurnStyle) { turnStyle = value; prefs.edit().putString("turn2", value.name).apply() }
    fun updateSpread(value: SpreadMode) { spreadMode = value; prefs.edit().putString("spread", value.name).apply() }
    fun updateVolume(value: Boolean) { volumeKeys = value; prefs.edit().putBoolean("volume", value).apply() }
    fun updateCrop(value: Boolean) { cropMargins = value; prefs.edit().putBoolean("crop", value).apply() }
}

/** Volume key presses forwarded from the activity while the reader is open. +1 = next, -1 = previous. */
object ReaderKeys {
    @Volatile var active: Boolean = false
    val events = MutableSharedFlow<Int>(extraBufferCapacity = 8)
}
