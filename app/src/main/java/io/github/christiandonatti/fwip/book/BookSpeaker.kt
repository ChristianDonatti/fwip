package io.github.christiandonatti.fwip.book

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.content.Content
import org.readium.r2.shared.publication.services.content.content
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * Reads the book aloud with the device's voice, a paragraph at a time, from where the reader is.
 * [current] is the paragraph being read (the reader lights it up and keeps it on screen).
 */
@OptIn(ExperimentalReadiumApi::class)
class BookSpeaker(context: Context, private val scope: CoroutineScope) {
    var ready by mutableStateOf(false)
        private set
    var playing by mutableStateOf(false)
        private set
    var current by mutableStateOf<Locator?>(null)
        private set
    /** When reading stops by itself (System.currentTimeMillis), or null. */
    var sleepAt by mutableStateOf<Long?>(null)
    /** Stop at the end of the chapter instead of at a time. */
    var sleepAtChapterEnd by mutableStateOf(false)

    private val waiting = ConcurrentHashMap<String, CancellableContinuation<Unit>>()
    private var job: Job? = null
    private var publication: Publication? = null
    private var skip = 0

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
    }.apply {
        setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { utteranceId?.let { waiting.remove(it)?.resume(Unit) } }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { utteranceId?.let { waiting.remove(it)?.resume(Unit) } }
            override fun onStop(utteranceId: String?, interrupted: Boolean) { utteranceId?.let { waiting.remove(it)?.resume(Unit) } }
        })
    }

    fun setRate(rate: Float) { tts.setSpeechRate(rate) }

    /** Starts reading at [from], in the book's language when the device has a voice for it. */
    fun play(publication: Publication, from: Locator, language: String?, rate: Float) {
        stop()
        this.publication = publication
        language?.let { runCatching { tts.setLanguage(Locale.forLanguageTag(it)) } }
        tts.setSpeechRate(rate)
        playing = true
        val startHref = from.href
        job = scope.launch {
            val iterator = publication.content(from)?.iterator() ?: run { playing = false; return@launch }
            var first = true
            while (isActive) {
                val element = when {
                    skip < 0 -> { skip = 0; iterator.previousOrNull()?.let { iterator.previousOrNull() ?: it } }
                    else -> iterator.nextOrNull()
                } ?: break
                val text = (element as? Content.TextualElement)?.text?.trim().orEmpty()
                if (text.isEmpty()) continue
                if (sleepAtChapterEnd && !first && element.locator.href != startHref) break
                first = false
                current = element.locator
                for (part in split(text)) {
                    if (!isActive) break
                    speak(part)
                    if (skip != 0) break
                }
                if (skip > 0) skip = 0
                sleepAt?.let { if (System.currentTimeMillis() >= it) { sleepAt = null; return@launch finish() } }
            }
            finish()
        }
    }

    private fun finish() {
        playing = false
    }

    /** Long paragraphs are cut at sentence ends, to stay under the voice's limit. */
    private fun split(text: String): List<String> {
        val max = (TextToSpeech.getMaxSpeechInputLength() - 100).coerceAtLeast(500)
        if (text.length <= max) return listOf(text)
        val parts = mutableListOf<String>()
        var rest = text
        while (rest.length > max) {
            val cut = rest.lastIndexOfAny(charArrayOf('.', '!', '?', ';'), max).takeIf { it > max / 3 } ?: rest.lastIndexOf(' ', max).takeIf { it > 0 } ?: max
            parts += rest.substring(0, cut + 1)
            rest = rest.substring(cut + 1).trimStart()
        }
        if (rest.isNotBlank()) parts += rest
        return parts
    }

    private suspend fun speak(text: String) = suspendCancellableCoroutine { cont ->
        val id = java.util.UUID.randomUUID().toString()
        waiting[id] = cont
        if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) waiting.remove(id)?.resume(Unit)
        cont.invokeOnCancellation { waiting.remove(id); tts.stop() }
    }

    fun pause() {
        job?.cancel()
        job = null
        tts.stop()
        playing = false
    }

    /** Picks up again at the paragraph that was being read. */
    fun resume(language: String?, rate: Float) {
        val p = publication ?: return
        val at = current ?: return
        play(p, at, language, rate)
    }

    fun next() { skip = 1; tts.stop() }
    fun previous() { skip = -1; tts.stop() }

    fun stop() {
        pause()
        current = null
    }

    fun release() {
        stop()
        runCatching { tts.shutdown() }
    }
}
