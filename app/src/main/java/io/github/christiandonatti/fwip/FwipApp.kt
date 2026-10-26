package io.github.christiandonatti.fwip

import android.app.Application
import androidx.annotation.StringRes

/** Application entry point; also gives code outside the UI access to translated text. */
class FwipApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        io.github.christiandonatti.fwip.data.Reports.install(this)
        io.github.christiandonatti.fwip.data.NewIssues.schedule(this)
    }

    companion object {
        lateinit var instance: FwipApp
            private set
    }
}

/** Translated text by id, usable anywhere (Compose screens recreate on language change). */
fun str(@StringRes id: Int, vararg args: Any): String = FwipApp.instance.getString(id, *args)
