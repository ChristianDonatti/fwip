package io.github.christiandonatti.fwip.remote

import io.github.christiandonatti.fwip.R
import io.github.christiandonatti.fwip.str
import io.github.christiandonatti.fwip.data.ComicMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** A series on Comic Vine (they call it a volume): "Batman (2016)". */
data class CvVolume(
    val id: Long,
    val name: String,
    val startYear: Int?,
    val publisher: String?,
    val issueCount: Int,
    val image: String?,
) {
    val label: String get() = if (startYear != null) "$name ($startYear)" else name
}

/** One issue of a volume, as listed (credits come separately). */
data class CvIssue(
    val id: Long,
    val number: String?,
    val name: String?,
    val coverDate: String?,
    val description: String?,
    val image: String?,
)

class ComicVineException(message: String, val rateLimited: Boolean = false) : IOException(message)

/**
 * The Comic Vine API (comicvine.gamespot.com), with the user's own free key. It asks clients to
 * stay around one request per second and 200 per hour for each kind of resource, so calls are
 * spaced out here and a rate-limit answer stops the work with a clear message.
 */
object ComicVine {
    private const val BASE = "https://comicvine.gamespot.com/api/"
    private const val USER_AGENT = "Fwip/0.3 (Android comic reader; https://github.com/ChristianDonatti/fwip)"

    private val http by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(40, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }
    private val spacing = Mutex()
    private var last = 0L

    private suspend fun get(key: String, path: String, params: Map<String, String>): JSONObject {
        spacing.withLock {
            val wait = 1100 - (System.currentTimeMillis() - last)
            if (wait > 0) delay(wait)
            last = System.currentTimeMillis()
        }
        val url = okhttp3.HttpUrl.Builder()
            .scheme("https").host("comicvine.gamespot.com")
            .addPathSegments("api/" + path.trim('/') + "/")
            .addQueryParameter("api_key", key.trim())
            .addQueryParameter("format", "json")
            .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }
            .build()
        return withContext(Dispatchers.IO) {
            val req = okhttp3.Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
            http.newCall(req).execute().use { r ->
                if (r.code == 420 || r.code == 429) throw ComicVineException(str(R.string.cv_rate_limited), rateLimited = true)
                if (r.code == 401) throw ComicVineException(str(R.string.cv_bad_key))
                if (!r.isSuccessful) throw HttpException(r.code, str(R.string.error_http, r.code))
                val o = JSONObject(r.body?.string().orEmpty())
                when (o.optInt("status_code")) {
                    1 -> o
                    100 -> throw ComicVineException(str(R.string.cv_bad_key))
                    107 -> throw ComicVineException(str(R.string.cv_rate_limited), rateLimited = true)
                    101 -> throw ComicVineException(str(R.string.cv_not_found))
                    else -> throw ComicVineException(o.optString("error").ifBlank { "Comic Vine" })
                }
            }
        }
    }

    private fun JSONObject.text(k: String): String? = optString(k).takeIf { has(k) && !isNull(k) && it.isNotBlank() && it != "null" }

    private fun image(o: JSONObject?): String? =
        o?.optJSONObject("image")?.let { it.text("small_url") ?: it.text("thumb_url") ?: it.text("medium_url") }

    suspend fun searchVolumes(key: String, query: String): List<CvVolume> {
        val o = get(key, "search", mapOf(
            "query" to query,
            "resources" to "volume",
            "field_list" to "id,name,start_year,publisher,count_of_issues,image",
            "limit" to "20",
        ))
        val arr = o.optJSONArray("results") ?: JSONArray()
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map { v ->
            CvVolume(
                id = v.optLong("id"),
                name = v.text("name").orEmpty(),
                startYear = v.text("start_year")?.trim()?.toIntOrNull(),
                publisher = v.optJSONObject("publisher")?.text("name"),
                issueCount = v.optInt("count_of_issues"),
                image = image(v),
            )
        }
    }

    suspend fun volumeIssues(key: String, volumeId: Long): List<CvIssue> {
        val out = mutableListOf<CvIssue>()
        var offset = 0
        while (true) {
            val o = get(key, "issues", mapOf(
                "filter" to "volume:$volumeId",
                "field_list" to "id,issue_number,name,cover_date,description,deck,image",
                "sort" to "cover_date:asc",
                "limit" to "100",
                "offset" to offset.toString(),
            ))
            val arr = o.optJSONArray("results") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                out += CvIssue(
                    id = item.optLong("id"),
                    number = item.text("issue_number"),
                    name = item.text("name"),
                    coverDate = item.text("cover_date"),
                    description = (item.text("description") ?: item.text("deck"))?.let(::plainText),
                    image = image(item),
                )
            }
            offset += arr.length()
            val total = o.optInt("number_of_total_results")
            if (arr.length() == 0 || offset >= total || offset >= 1000) break
        }
        return out
    }

    /** Credits, story arcs and characters of one issue. */
    suspend fun issueCredits(key: String, issueId: Long): ComicMeta {
        val o = get(key, "issue/4000-$issueId", mapOf(
            "field_list" to "person_credits,story_arc_credits,character_credits",
        )).optJSONObject("results") ?: JSONObject()
        val people = o.optJSONArray("person_credits") ?: JSONArray()
        val roles = mutableMapOf<String, MutableList<String>>()
        for (i in 0 until people.length()) {
            val p = people.getJSONObject(i)
            val name = p.text("name") ?: continue
            for (role in p.optString("role").split(',').map { it.trim().lowercase() }) {
                val field = when {
                    role.startsWith("writer") || role == "plotter" || role == "scripter" -> "writer"
                    role.startsWith("pencil") || role == "artist" || role == "penciler" -> "penciller"
                    role.startsWith("inker") -> "inker"
                    role.startsWith("color") -> "colorist"
                    role.startsWith("letter") -> "letterer"
                    role.startsWith("cover") -> "coverArtist"
                    role.startsWith("editor") -> "editor"
                    else -> null
                } ?: continue
                roles.getOrPut(field) { mutableListOf() } += name
            }
        }
        fun names(arrName: String, max: Int) = o.optJSONArray(arrName)?.let { a ->
            (0 until a.length()).mapNotNull { a.getJSONObject(it).text("name") }.take(max).joinToString(", ").ifBlank { null }
        }
        fun role(k: String) = roles[k]?.distinct()?.joinToString(", ")
        return ComicMeta(
            writer = role("writer"),
            penciller = role("penciller"),
            inker = role("inker"),
            colorist = role("colorist"),
            letterer = role("letterer"),
            coverArtist = role("coverArtist"),
            editor = role("editor"),
            storyArc = names("story_arc_credits", 3),
            characters = names("character_credits", 12),
        )
    }

    /** Comic Vine descriptions are HTML; keep readable text only. */
    fun plainText(html: String): String =
        android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
            .let { if (it.length > 1500) it.take(1500).substringBeforeLast(' ') + "…" else it }

    /** "1", "001", "1.0" and "1" all name the same issue. */
    fun sameNumber(a: String?, b: String?): Boolean {
        if (a == null || b == null) return false
        val x = a.trim().toFloatOrNull()
        val y = b.trim().toFloatOrNull()
        return if (x != null && y != null) x == y else a.trim().equals(b.trim(), ignoreCase = true)
    }

    /** Pairs each comic (id to issue number) with the issue of the same number. */
    fun match(numbers: List<Pair<String, String?>>, issues: List<CvIssue>): Map<String, CvIssue> =
        numbers.mapNotNull { (id, n) -> issues.firstOrNull { sameNumber(it.number, n) }?.let { id to it } }.toMap()

    /** "The Amazing Spider-Man" and "amazing spider man" compare equal. */
    fun normalize(name: String): String =
        name.lowercase().removePrefix("the ").replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()

    /** What an issue listing gives us, as metadata. */
    fun metaFor(volume: CvVolume, issue: CvIssue): ComicMeta {
        val date = issue.coverDate?.split('-')
        return ComicMeta(
            title = issue.name,
            series = volume.name,
            number = issue.number?.let { io.github.christiandonatti.fwip.data.cleanNumber(it.trim()).takeIf { n -> n.isNotEmpty() } ?: it },
            volume = volume.startYear,
            count = volume.issueCount.takeIf { it > 0 },
            summary = issue.description,
            year = date?.getOrNull(0)?.toIntOrNull(),
            month = date?.getOrNull(1)?.toIntOrNull(),
            publisher = volume.publisher,
        )
    }
}
