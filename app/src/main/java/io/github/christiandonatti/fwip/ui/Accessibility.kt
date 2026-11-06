package io.github.christiandonatti.fwip.ui

import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

/** What the system says about accessibility, so Fwip can follow it. */
object A11y {
    /** "Remove animations" is on: page turns become instant, without sliding or curling. */
    fun reduceMotion(context: Context): Boolean =
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)

    /** A screen reader (TalkBack and the like) is in use: pages are announced as they turn. */
    fun screenReader(context: Context): Boolean {
        val am = context.getSystemService(AccessibilityManager::class.java) ?: return false
        return am.isEnabled && am.isTouchExplorationEnabled
    }
}
