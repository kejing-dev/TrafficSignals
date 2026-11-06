package io.embodit.trafficsignals.domain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * The app's only sounds (design `audio`; no looping hum, profile B24): pure, so the loudness is tested on the JVM and the
 * evidence wav is rendered from the same numbers the app plays (qa/loudness-<version>.wav).
 *
 * Each effect is normalised at the source to its [Fx.peakDb] at gain 1. The gain the player applies is the volume slider
 * ([Volume]), default 100 %. Loudness is judged on the sounding part only (build.md: short feedback sounds).
 */
object SoundSynth {
    const val RATE = 22050

    enum class Fx(val peakDb: Float) { RIGHT(-1.5f), WRONG(-4f), CARS(-6f) }

    fun render(fx: Fx): FloatArray {
        val raw =
            when (fx) {
                Fx.RIGHT -> right()
                Fx.WRONG -> wrong()
                Fx.CARS -> cars()
            }
        var peak = 1e-6f
        for (v in raw) peak = max(peak, abs(v))
        val k = dbToLin(fx.peakDb) / peak
        return FloatArray(raw.size) { raw[it] * k }
    }

    private fun gen(seconds: Float, f: (t: Float, i: Int) -> Float) = FloatArray((RATE * seconds).toInt()) { i -> f(i / RATE.toFloat(), i) }

    private fun tone(hz: Double, t: Float) = sin(2 * PI * hz * t).toFloat()

    /** A short two-note chime: struck partials that decay at different rates, like a small bell. */
    private fun right() =
        gen(0.7f) { t, _ ->
            fun bell(t0: Float, f0: Double): Float {
                val u = t - t0
                if (u < 0f) return 0f
                val attack = min(1f, u / 0.004f)
                return attack * (tone(f0, u) * exp(-u * 4.2f) + 0.45f * tone(f0 * 2.0, u) * exp(-u * 7.5f) + 0.2f * tone(f0 * 3.01, u) * exp(-u * 12f))
            }
            0.55f * bell(0f, 880.0) + 0.75f * bell(0.11f, 1174.66)
        }

    /** A soft low double "dum-dum": warm harmonics, no buzz, closes gently. */
    private fun wrong() =
        gen(0.55f) { t, _ ->
            fun dum(t0: Float, f0: Double): Float {
                val u = t - t0
                if (u < 0f) return 0f
                val env = min(1f, u / 0.012f) * exp(-u * 8f)
                return env * (tone(f0, u) + 0.55f * tone(f0 * 2, u) + 0.28f * tone(f0 * 3, u) + 0.12f * tone(f0 * 4, u))
            }
            tanh(1.3f * (dum(0f, 233.08) + dum(0.17f, 196.0)))
        }

    /** A car pulling away: engine harmonics rising with the revs, a little filtered road noise, fades as it leaves. */
    private fun cars(): FloatArray {
        val rnd = java.util.Random(11)
        var low = 0f
        var phase = 0.0
        return gen(1.3f) { t, _ ->
            val hz = 78.0 + 70.0 * (1.0 - exp(-t * 2.4)) + 1.6 * sin(2 * PI * 7.0 * t)
            phase += 2 * PI * hz / RATE
            var s = 0f
            for (h in 1..7) s += (sin(phase * h).toFloat() / h) * (if (h % 2 == 0) 0.8f else 1f)
            low += 0.12f * ((rnd.nextFloat() * 2 - 1) - low)
            val env = min(1f, t / 0.09f) * (if (t < 0.65f) 1f else exp(-(t - 0.65f) * 3.6f))
            tanh(1.4f * (0.75f * s + 0.9f * low)) * env
        }
    }

    fun dbToLin(db: Float): Float = Math.pow(10.0, db / 20.0).toFloat()

    fun peakDb(x: FloatArray): Float {
        var p = 1e-9f
        for (v in x) p = max(p, abs(v))
        return (20 * log10(p.toDouble())).toFloat()
    }

    /** RMS of the sounding part: samples in 20 ms frames above -50 dBFS (build.md: short feedback sounds). */
    fun soundingRmsDb(x: FloatArray): Float {
        val frame = RATE / 50
        var sum = 0.0
        var n = 0
        var i = 0
        while (i < x.size) {
            val end = min(x.size, i + frame)
            var e = 0.0
            for (k in i until end) e += (x[k] * x[k]).toDouble()
            if (sqrt(e / (end - i)) > 0.00316) {
                sum += e
                n += end - i
            }
            i = end
        }
        return if (n == 0) -120f else (10 * log10(sum / n)).toFloat()
    }

    /** Every effect in a row with silence trimmed: what qa/loudness-<version>.wav holds. */
    fun soundingSection(): FloatArray {
        val parts = Fx.entries.map { render(it) }.flatMap { listOf(it, it) }
        val out = ArrayList<Float>()
        for (p in parts) {
            var last = p.size - 1
            while (last > 0 && abs(p[last]) < 0.00316f) last--
            for (k in 0..last) out += p[k]
        }
        return out.toFloatArray()
    }
}

/** The volume slider: three detents and the percent shown beside it; the mute key is separate. */
object Volume {
    val LEVELS = floatArrayOf(0.33f, 0.66f, 1f)
    const val DEFAULT = 1f

    fun snap(v: Float): Float = LEVELS.minByOrNull { abs(it - v) } ?: DEFAULT

    fun percent(v: Float): String = "${Math.round(v * 100)}%"
}
