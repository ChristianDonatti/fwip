package io.github.christiandonatti.fwip.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/** A light "done" tick for actions that change something: marked read, added, bookmarked. */
fun View.tick() {
    performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK)
}
