package io.github.christiandonatti.fwip.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.christiandonatti.fwip.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** A newer Fwip on GitHub. [test] for the always-fresh test build, otherwise a numbered release. */
data class AppUpdate(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val pageUrl: String,
    val test: Boolean,
)

/** Result of asking GitHub, shown when the check was started by hand. */
sealed interface UpdateCheck {
    data object Checking : UpdateCheck
    data object UpToDate : UpdateCheck
    data class Found(val update: AppUpdate) : UpdateCheck
    data class Failed(val message: String) : UpdateCheck
}

/**
 * Looks on GitHub for a newer Fwip. Official builds compare with the latest release; test builds
 * compare their build number with the current test build. Nothing is downloaded or installed
 * here: the download opens in the browser and Android asks before installing.
 */
object Updates {
    private const val REPO = "ChristianDonatti/fwip"
    private const val EVERY = 12 * 60 * 60 * 1000L

    /** A newer version found by the automatic check and not dismissed. */
    var available by mutableStateOf<AppUpdate?>(null)
        private set
    var manual by mutableStateOf<UpdateCheck?>(null)

    private val client by lazy {
        OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    }

    private fun prefs(context: Context) = context.getSharedPreferences("fwip_updates", Context.MODE_PRIVATE)

    fun autoEnabled(context: Context) = prefs(context).getBoolean("auto", true)
    fun setAuto(context: Context, on: Boolean) = prefs(context).edit().putBoolean("auto", on).apply()

    val official: Boolean get() = BuildConfig.OFFICIAL
    val currentVersion: String get() = if (official) BuildConfig.VERSION_NAME else "${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})"

    /** At most twice a day, quietly; a failure just waits for the next time. */
    suspend fun checkInBackground(context: Context) {
        val p = prefs(context)
        if (!p.getBoolean("auto", true)) return
        val now = System.currentTimeMillis()
        if (now - p.getLong("last", 0L) < EVERY) {
            // Still show what an earlier check found, unless it was dismissed or installed.
            p.getString("found", null)?.let(::fromJson)?.takeIf { isNewer(it) && it.version != p.getString("dismissed", null) }?.let { available = it }
            return
        }
        val found = runCatching { fetch() }.getOrNull() ?: return
        p.edit().putLong("last", now).putString("found", found.takeIf(::isNewer)?.let(::toJson)).apply()
        if (isNewer(found) && found.version != p.getString("dismissed", null)) available = found
    }

    /** "Check now" in the settings. */
    suspend fun checkNow(context: Context) {
        manual = UpdateCheck.Checking
        manual = runCatching { fetch() }.fold(
            onSuccess = { u ->
                prefs(context).edit().putLong("last", System.currentTimeMillis()).apply()
                if (isNewer(u)) {
                    available = u
                    UpdateCheck.Found(u)
                } else UpdateCheck.UpToDate
            },
            onFailure = { UpdateCheck.Failed(it.message ?: it.javaClass.simpleName) },
        )
    }

    fun dismiss(context: Context) {
        available?.let { prefs(context).edit().putString("dismissed", it.version).apply() }
        available = null
    }

    private fun isNewer(u: AppUpdate): Boolean =
        if (u.test) (u.version.toIntOrNull() ?: 0) > BuildConfig.VERSION_CODE
        else compareVersions(u.version, BuildConfig.VERSION_NAME) > 0

    private suspend fun fetch(): AppUpdate = withContext(Dispatchers.IO) {
        val path = if (official) "releases/latest" else "releases/tags/teste"
        val request = Request.Builder()
            .url("https://api.github.com/repos/$REPO/$path")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Fwip/${BuildConfig.VERSION_NAME}")
            .build()
        client.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException("GitHub: HTTP ${r.code}")
            val o = JSONObject(r.body!!.string())
            val assets = o.optJSONArray("assets")
            val apk = (0 until (assets?.length() ?: 0)).map { assets!!.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") }
                ?: throw java.io.IOException("GitHub: no APK in the release")
            val version = if (official) {
                o.optString("tag_name").removePrefix("v")
            } else {
                // "Versão de teste (build 123)"
                Regex("build (\\d+)").find(o.optString("name"))?.groupValues?.get(1) ?: "0"
            }
            AppUpdate(
                version = version,
                notes = o.optString("body").trim(),
                apkUrl = apk.optString("browser_download_url"),
                pageUrl = o.optString("html_url"),
                test = !official,
            )
        }
    }

    /** 0.10.0 is newer than 0.9.2. */
    fun compareVersions(a: String, b: String): Int {
        val x = a.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.', '-').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val c = (x.getOrElse(i) { 0 }).compareTo(y.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }

    private fun toJson(u: AppUpdate) = JSONObject()
        .put("version", u.version).put("notes", u.notes).put("apk", u.apkUrl).put("page", u.pageUrl).put("test", u.test).toString()

    private fun fromJson(s: String): AppUpdate? = runCatching {
        val o = JSONObject(s)
        AppUpdate(o.getString("version"), o.optString("notes"), o.getString("apk"), o.optString("page"), o.optBoolean("test"))
    }.getOrNull()
}
