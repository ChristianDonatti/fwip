package io.github.christiandonatti.fwip.book.scene

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tan

/**
 * The sound of a scene, synthesised as it plays: fire crackling, rain, wind, birds, waves,
 * crickets, a clock. Nothing is recorded or looped, so it never repeats and weighs nothing.
 * Plays at the media volume, under [volume], fading in and out.
 */
class AmbientSound {
    companion object {
        /** The one sound of the app: two readers, or a screen turned, never play over each other. */
        val shared = AmbientSound()
    }

    /** Who asked for the sound last; only they can stop it. */
    @Volatile private var owner: Any? = null

    fun play(by: Any, a: Ambience) { owner = by; play(a) }

    fun stop(by: Any) { if (owner === by) stop() }

    @Volatile var volume = 0.35f
    /** Lowered while the book is read aloud. */
    @Volatile var duck = false
    @Volatile private var ambience: Ambience? = null
    @Volatile private var running = false
    private var thread: Thread? = null

    fun play(a: Ambience) {
        ambience = a
        running = true
        // A thread still fading out picks the sound up again instead of a second one starting.
        if (thread?.isAlive != true) thread = Thread({ loop() }, "fwip-ambience").apply { start() }
    }

    fun stop() {
        running = false
    }

    private fun loop() {
        val rate = 22050
        val frames = 1024
        val minBuf = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(maxOf(minBuf, frames * 4 * 4))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }.getOrNull() ?: return
        val buf = FloatArray(frames)
        var current: Ambience? = null
        var gen: Generator? = null
        var gain = 0f
        var crossTo: Generator? = null
        var cross = 0f
        try {
            track.play()
            while (true) {
                val want = ambience
                if (want != current) {
                    // A new place: the old sound fades out while the new one fades in. If a change
                    // was already under way, what was coming in becomes what goes out, so there
                    // are never more than two sounds.
                    if (crossTo != null && cross > 0.5f) gen = crossTo
                    crossTo = want?.let { generatorFor(it, rate) }
                    cross = 0f
                    current = want
                }
                val target = if (!running) 0f else volume * (if (duck) 0.25f else 1f)
                for (i in 0 until frames) {
                    // Up gently, down quickly: a sound that was turned off should be gone.
                    gain += (target - gain) * (if (target < gain) 0.0006f else 0.00008f)
                    var s = gen?.next() ?: 0f
                    crossTo?.let { n ->
                        cross = (cross + 1f / (rate * 0.7f)).coerceAtMost(1f)
                        s = s * (1f - cross) + n.next() * cross
                        if (cross >= 1f) { gen = n; crossTo = null }
                    }
                    buf[i] = (s * gain).coerceIn(-1f, 1f)
                }
                track.write(buf, 0, frames, AudioTrack.WRITE_BLOCKING)
                if (!running && gain < 0.001f) break
            }
        } catch (_: Throwable) {
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }
}

// ---------------------------------------------------------------- Building blocks

private class Rng(seed: Long) {
    private var s = seed or 1L
    fun next(): Float { s = s xor (s shl 13); s = s xor (s ushr 7); s = s xor (s shl 17); return ((s ushr 40).toInt() and 0xFFFFFF) / 16777215f }
    fun white(): Float = next() * 2f - 1f
}

/** Brown (red) noise: deep, like a room or distant surf. */
private class Brown(private val r: Rng) { private var b = 0f; fun next(): Float { b = (b + 0.02f * r.white()) / 1.02f; return b * 3.5f } }

/** Pink noise (Paul Kellet's filter): natural hiss. */
private class Pink(private val r: Rng) {
    private var b0 = 0f; private var b1 = 0f; private var b2 = 0f; private var b3 = 0f; private var b4 = 0f; private var b5 = 0f; private var b6 = 0f
    fun next(): Float {
        val w = r.white()
        b0 = 0.99886f * b0 + w * 0.0555179f; b1 = 0.99332f * b1 + w * 0.0750759f; b2 = 0.96900f * b2 + w * 0.1538520f
        b3 = 0.86650f * b3 + w * 0.3104856f; b4 = 0.55000f * b4 + w * 0.5329522f; b5 = -0.7616f * b5 - w * 0.0168980f
        val p = b0 + b1 + b2 + b3 + b4 + b5 + b6 + w * 0.5362f
        b6 = w * 0.115926f
        return p * 0.11f
    }
}

/** State-variable filter: low, band and high pass at once, cutoff changeable per sample. */
private class Svf(private val rate: Int, var q: Float = 0.7f) {
    var low = 0f; var band = 0f; var high = 0f
    fun run(x: Float, fc: Float) {
        val f = 2f * sin(PI.toFloat() * (fc / rate).coerceAtMost(0.24f))
        low += f * band
        high = x - low - band / q
        band += f * high
    }
}

private class OnePole(private val rate: Int) {
    var y = 0f
    fun low(x: Float, fc: Float): Float { val a = 1f - exp(-2f * PI.toFloat() * fc / rate); y += a * (x - y); return y }
}

/** Slow random movement in 0..1, for gusts and swells. */
private class Drift(private val r: Rng, private val rate: Int, private val seconds: Float) {
    private var a = r.next(); private var b = r.next(); private var t = 0f
    fun next(): Float {
        t += 1f / (rate * seconds)
        if (t >= 1f) { t -= 1f; a = b; b = r.next() }
        val u = t * t * (3f - 2f * t)
        return a + (b - a) * u
    }
}

/** A short event (a crackle, a drop, a tick): decaying, filtered noise or tone. */
private class Burst(val samples: Int, val amp: Float, val freq: Float, val tone: Boolean, val decay: Float) {
    var i = 0
    var phase = 0f
}

private abstract class Generator(val rate: Int, seed: Long) {
    val rng = Rng(seed)
    private val bursts = ArrayList<Burst>()
    private val burstFilter = Svf(rate, 1.2f)
    abstract fun next(): Float

    fun chance(perSecond: Float) = rng.next() < perSecond / rate

    fun burst(ms: Float, amp: Float, freq: Float, tone: Boolean = false) {
        if (bursts.size < 48) bursts += Burst((ms * rate / 1000f).toInt().coerceAtLeast(8), amp, freq, tone, ms)
    }

    fun bursts(): Float {
        var s = 0f
        val it = bursts.iterator()
        while (it.hasNext()) {
            val b = it.next()
            val env = exp(-5f * b.i / b.samples)
            s += if (b.tone) {
                b.phase += 2f * PI.toFloat() * b.freq / rate
                sin(b.phase) * env * b.amp
            } else rng.white() * env * b.amp
            b.i++
            if (b.i >= b.samples) it.remove()
        }
        return s
    }

    fun filteredBursts(fc: Float): Float { burstFilter.run(bursts(), fc); return burstFilter.band + burstFilter.high * 0.3f }
}

private fun generatorFor(a: Ambience, rate: Int): Generator = when (a) {
    Ambience.Fire -> Fire(rate)
    Ambience.Rain -> Rain(rate)
    Ambience.Wind -> Wind(rate)
    Ambience.Park -> Park(rate)
    Ambience.Waves -> Waves(rate)
    Ambience.Night -> Night(rate)
    Ambience.Library -> Room(rate, pages = true)
    Ambience.Room -> Room(rate, pages = false)
}

// ---------------------------------------------------------------- The places

/** Logs burning: a low roar, soft hiss, and crackles that come in little clusters. */
private class Fire(rate: Int) : Generator(rate, 11) {
    private val brown = Brown(rng)
    private val pink = Pink(rng)
    private val lp = OnePole(rate)
    private val hiss = Svf(rate)
    private val flicker = Drift(rng, rate, 0.6f)
    private var cluster = 0
    override fun next(): Float {
        val f = flicker.next()
        val roar = lp.low(brown.next(), 180f) * (0.5f + 0.4f * f)
        hiss.run(pink.next(), 3500f)
        if (chance(4f + 10f * f)) cluster = 1 + (rng.next() * 5).toInt()
        if (cluster > 0 && chance(60f)) {
            cluster--
            burst(2f + rng.next() * 14f, 0.25f + rng.next() * 0.7f, 0f)
        }
        if (chance(0.25f)) burst(30f + rng.next() * 40f, 0.5f, 0f)
        return roar * 0.55f + hiss.high * 0.05f + filteredBursts(1500f + 2500f * f) * 0.5f
    }
}

/** Rain on glass and roof: hiss, a low patter, and countless tiny drops. */
private class Rain(rate: Int) : Generator(rate, 23) {
    private val pink = Pink(rng)
    private val brown = Brown(rng)
    private val bp = Svf(rate, 0.6f)
    private val lp = OnePole(rate)
    private val swell = Drift(rng, rate, 5f)
    override fun next(): Float {
        val s = 0.7f + 0.3f * swell.next()
        bp.run(pink.next(), 2800f)
        if (chance(70f * s)) burst(2f + rng.next() * 5f, 0.04f + rng.next() * 0.12f, 1500f + rng.next() * 4500f, tone = true)
        return bp.band * 0.45f * s + lp.low(brown.next(), 300f) * 0.18f + bursts()
    }
}

/** Wind on a mountain: gusts rising and falling, now and then a faint whistle. */
private class Wind(rate: Int) : Generator(rate, 37) {
    private val pink = Pink(rng)
    private val brown = Brown(rng)
    private val bp = Svf(rate, 1.1f)
    private val whistle = Svf(rate, 9f)
    private val gust = Drift(rng, rate, 3.5f)
    private val slow = Drift(rng, rate, 11f)
    override fun next(): Float {
        val g = gust.next() * 0.6f + slow.next() * 0.4f
        val n = pink.next() * 0.6f + brown.next() * 0.4f
        bp.run(n, 220f + 700f * g)
        whistle.run(n, 1200f + 600f * slow.next())
        return bp.band * (0.25f + 0.75f * g * g) * 0.9f + whistle.band * 0.04f * g
    }
}

/** A park: a soft breeze in the leaves and birds singing short phrases. */
private class Park(rate: Int) : Generator(rate, 41) {
    private val pink = Pink(rng)
    private val leaves = Svf(rate, 0.7f)
    private val breeze = Drift(rng, rate, 4f)
    private var notesLeft = 0
    private var noteSamples = 0
    private var noteI = 0
    private var gap = 0
    private var phase = 0f
    private var f0 = 3000f
    private var f1 = 4000f
    private var trill = 0f
    private var amp = 0.1f
    override fun next(): Float {
        val b = breeze.next()
        leaves.run(pink.next(), 2500f + 2000f * b)
        var bird = 0f
        if (notesLeft == 0 && chance(0.35f)) {
            // A new song: a few notes, all of one kind.
            notesLeft = 3 + (rng.next() * 6).toInt()
            f0 = 2200f + rng.next() * 2800f
            f1 = f0 * (0.7f + rng.next() * 0.8f)
            trill = if (rng.next() > 0.5f) 25f + rng.next() * 30f else 0f
            amp = 0.05f + rng.next() * 0.08f
            gap = 0
        }
        if (notesLeft > 0) {
            if (gap > 0) gap--
            else {
                if (noteI == 0) noteSamples = ((0.04f + rng.next() * 0.12f) * rate).toInt()
                val u = noteI.toFloat() / noteSamples
                val f = f0 + (f1 - f0) * u + if (trill > 0) sin(2f * PI.toFloat() * trill * noteI / rate) * 300f else 0f
                phase += 2f * PI.toFloat() * f / rate
                bird = sin(phase) * sin(PI.toFloat() * u) * amp
                noteI++
                if (noteI >= noteSamples) { noteI = 0; notesLeft--; gap = ((0.03f + rng.next() * 0.09f) * rate).toInt() }
            }
        }
        return leaves.band * 0.12f * (0.3f + b) + bird
    }
}

/** The sea: each wave builds, breaks, and washes back over the sand. */
private class Waves(rate: Int) : Generator(rate, 53) {
    private val pink = Pink(rng)
    private val brown = Brown(rng)
    private val lp = Svf(rate, 0.7f)
    private val under = OnePole(rate)
    private var t = 0f
    private var length = 9f
    private var rise = 3f
    override fun next(): Float {
        t += 1f / rate
        if (t > length) { t = 0f; length = 7f + rng.next() * 5f; rise = 2f + rng.next() * 2f }
        val e = if (t < rise) (t / rise).let { it * it } * 0.6f else 0.6f + 0.4f * exp(-(t - rise) * 1.2f) - (t - rise) / (length - rise) * 0.5f
        val env = e.coerceIn(0.05f, 1f)
        lp.run(pink.next(), 300f + 2600f * env)
        return lp.low * 0.9f * env + under.low(brown.next(), 120f) * 0.25f
    }
}

/** A summer night: crickets chirping in their own rhythms, a breeze, sometimes an owl. */
private class Night(rate: Int) : Generator(rate, 61) {
    private class Cricket(val freq: Float, val pulses: Int, val period: Float, val amp: Float, var t: Float)
    private val crickets = List(5) { Cricket(4000f + it * 230f, 3 + it % 2, 0.55f + it * 0.17f, 0.03f + it * 0.008f, it * 0.13f) }
    private val pink = Pink(rng)
    private val bp = Svf(rate, 1f)
    private val breeze = Drift(rng, rate, 6f)
    private var owl = -1f
    private var phase = FloatArray(6)
    override fun next(): Float {
        var s = 0f
        for ((i, c) in crickets.withIndex()) {
            c.t += 1f / rate
            if (c.t > c.period) c.t -= c.period
            val pulse = (c.t / 0.033f).toInt()
            if (pulse < c.pulses) {
                val u = (c.t % 0.033f) / 0.022f
                if (u < 1f) {
                    phase[i] += 2f * PI.toFloat() * c.freq / rate
                    s += sin(phase[i]) * sin(PI.toFloat() * u) * c.amp
                }
            }
        }
        bp.run(pink.next(), 400f + 300f * breeze.next())
        if (owl < 0f && chance(0.03f)) owl = 0f
        if (owl >= 0f) {
            owl += 1f / rate
            val note = when { owl < 0.45f -> owl / 0.45f; owl in 0.7f..1.5f -> (owl - 0.7f) / 0.8f; else -> -1f }
            if (note >= 0f) {
                phase[5] += 2f * PI.toFloat() * (380f - 30f * note) / rate
                s += sin(phase[5]) * sin(PI.toFloat() * note) * 0.07f
            }
            if (owl > 1.6f) owl = -1f
        }
        return s + bp.band * 0.06f * breeze.next()
    }
}

/** A quiet room: its faint hum, a clock, and in the library a page turned somewhere now and then. */
private class Room(rate: Int, private val pages: Boolean) : Generator(rate, 71) {
    private val brown = Brown(rng)
    private val lp = OnePole(rate)
    private var tick = 0f
    private var tock = false
    private var rustle = 0
    override fun next(): Float {
        tick += 1f / rate
        if (tick >= 1f) {
            tick -= 1f
            tock = !tock
            burst(4f, 0.18f, if (tock) 2100f else 2600f, tone = true)
            burst(3f, 0.06f, 0f)
        }
        var r = 0f
        if (pages && rustle == 0 && chance(0.05f)) rustle = (0.35f * rate).toInt()
        if (rustle > 0) {
            val u = 1f - rustle.toFloat() / (0.35f * rate)
            r = rng.white() * sin(PI.toFloat() * u) * 0.05f * (0.6f + 0.4f * sin(u * 40f))
            rustle--
        }
        return lp.low(brown.next(), 90f) * 0.18f + bursts() * 0.6f + r
    }
}
