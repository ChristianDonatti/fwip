package io.github.christiandonatti.fwip.reader

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableSharedFlow

enum class TurnStyle(val label: String) { Flip("Virar"), Slide("Deslizar") }

enum class SpreadMode(val label: String) { Auto("Automático"), Single("Uma página"), Double("Página dupla") }

/** Reader preferences, kept as Compose state so the screen updates the moment one changes. */
class ReaderSettings(context: Context) {
    private val prefs = context.getSharedPreferences("fwip_reader", Context.MODE_PRIVATE)

    var mangaMode by mutableStateOf(prefs.getBoolean("manga", false))
        private set
    var turnStyle by mutableStateOf(runCatching { TurnStyle.valueOf(prefs.getString("turn", null) ?: "") }.getOrDefault(TurnStyle.Flip))
        private set
    var spreadMode by mutableStateOf(runCatching { SpreadMode.valueOf(prefs.getString("spread", null) ?: "") }.getOrDefault(SpreadMode.Auto))
        private set
    var volumeKeys by mutableStateOf(prefs.getBoolean("volume", true))
        private set

    fun updateManga(value: Boolean) { mangaMode = value; prefs.edit().putBoolean("manga", value).apply() }
    fun updateTurn(value: TurnStyle) { turnStyle = value; prefs.edit().putString("turn", value.name).apply() }
    fun updateSpread(value: SpreadMode) { spreadMode = value; prefs.edit().putString("spread", value.name).apply() }
    fun updateVolume(value: Boolean) { volumeKeys = value; prefs.edit().putBoolean("volume", value).apply() }
}

/** Volume key presses forwarded from the activity while the reader is open. +1 = next, -1 = previous. */
object ReaderKeys {
    @Volatile var active: Boolean = false
    val events = MutableSharedFlow<Int>(extraBufferCapacity = 8)
}
