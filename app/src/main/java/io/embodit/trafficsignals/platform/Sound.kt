package io.embodit.trafficsignals.platform

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Tiny synthesized sounds (design `audio`): a soft "ding" for right, a low "doo" for wrong, and a car pulling away:
 * only sounds that answer something you or the cars did. No looping traffic hum since v0.1.2 (profile B24). Default volume
 * 40 %; the mute switch stops everything.
 * No whistle (the gestures have none). Peaks stay below -6 dBFS.
 */
class Sound {
    private val rate = 22050
    var enabled = true

    private fun pcm(seconds: Float, gen: (t: Float) -> Float): ShortArray {
        val n = (rate * seconds).toInt()
        return ShortArray(n) { i -> (gen(i / rate.toFloat()).coerceIn(-1f, 1f) * VOLUME * 32767).toInt().toShort() }
    }

    private fun play(data: ShortArray) {
        if (!enabled) return
        runCatching {
            val track =
                AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(data.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
            track.write(data, 0, data.size)
            track.setNotificationMarkerPosition(data.size)
            track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack) { runCatching { t.release() } }
                override fun onPeriodicNotification(t: AudioTrack) {}
            })
            track.play()
        }.onFailure { TsLog.w("SOUND play failed: $it") }
    }

    private val ding by lazy { pcm(0.6f) { t -> (sin(2 * PI * 1318.5 * t) * 0.6 + sin(2 * PI * 1975.5 * t) * 0.25).toFloat() * exp(-t * 7f) } }
    private val doo by lazy { pcm(0.45f) { t -> (sin(2 * PI * 196.0 * t) * 0.7 + sin(2 * PI * 294.0 * t) * 0.2).toFloat() * (if (t < 0.02f) t / 0.02f else exp(-(t - 0.02f) * 6f)) } }
    private val engine by lazy {
        pcm(1.2f) { t ->
            val f = 55 + 40 * t
            val env = if (t < 0.15f) t / 0.15f else exp(-(t - 0.15f) * 2.2f)
            ((sin(2 * PI * f * t) * 0.5 + sin(2 * PI * f * 2 * t) * 0.25)).toFloat() * env * 0.7f
        }
    }

    /** Builds the three samples off the main thread (they were lazy on the first answer's frame, build prompt 2026-11-05). */
    fun warm() {
        val t0 = android.os.SystemClock.elapsedRealtime()
        val n = ding.size + doo.size + engine.size
        TsLog.i("SOUND warm samples=$n ms=${android.os.SystemClock.elapsedRealtime() - t0}")
    }

    fun right() = play(ding)

    fun wrong() = play(doo)

    fun carsGo() = play(engine)

    companion object {
        /** Design default volume 40 %. */
        const val VOLUME = 0.4f
    }
}
