package io.github.christiandonatti.fwip.book.scene

import androidx.annotation.StringRes
import io.github.christiandonatti.fwip.R
import io.github.christiandonatti.fwip.str

/**
 * Places to read in: the book lies on a desk or is held up in front of a library, a fire,
 * mountains... Each one is drawn by the app and most also come as a photo (CC0, Poly Haven)
 * with the same moving touches on top. [cover] is the board colour that suits the place.
 */
enum class Scene(@StringRes private val labelRes: Int, val hasPhoto: Boolean, val cover: Long, val sound: Ambience) {
    Desk(R.string.scene_desk, true, 0xFF5B3A29, Ambience.Room),
    Library(R.string.scene_library, true, 0xFF3F2A22, Ambience.Library),
    Fireplace(R.string.scene_fireplace, true, 0xFF6B2E25, Ambience.Fire),
    Mountain(R.string.scene_mountain, true, 0xFF2F4A5A, Ambience.Wind),
    Park(R.string.scene_park, true, 0xFF34503A, Ambience.Park),
    Rain(R.string.scene_rain, true, 0xFF2B3446, Ambience.Rain),
    Beach(R.string.scene_beach, true, 0xFF7A4B3A, Ambience.Waves),
    Night(R.string.scene_night, true, 0xFF1F2A44, Ambience.Night);

    val label: String get() = str(labelRes)
}

/** The sound of each place, made on the fly (no recordings): see [AmbientSound]. */
enum class Ambience { Room, Library, Fire, Wind, Park, Rain, Waves, Night }
