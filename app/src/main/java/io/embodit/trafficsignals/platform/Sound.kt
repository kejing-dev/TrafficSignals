package io.embodit.trafficsignals.platform

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Tiny synthesized sounds (design `audio`): a soft "ding" for right, a low "doo" for wrong, a car pulling away, and a
 * very quiet traffic hum while a level runs. Default volume 40 %; the mute switch (P1 / P2 top row) stops everything.
 * No whistle (the gestures have none). Peaks stay below -6 dBFS.
 */
class Sound {
    private val rate = 22050
    private var hum: AudioTrack? = null
    var enabled = true
        set(v) {
            field = v
            if (!v) stopHum()
        }

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

    fun right() = play(ding)

    fun wrong() = play(doo)

    fun carsGo() = play(engine)

    /** A quiet looping hum of distant traffic (brown-ish noise, ~-30 dBFS). */
    fun startHum() {
        if (!enabled || hum != null) return
        runCatching {
            var last = 0f
            val rnd = java.util.Random(3)
            val data = pcm(2f) { t ->
                last = (last + (rnd.nextFloat() * 2 - 1) * 0.02f) * 0.995f
                val fade = minOf(1f, t / 0.05f, (2f - t) / 0.05f)
                last * 0.9f * fade
            }
            val track =
                AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(data.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
            track.write(data, 0, data.size)
            track.setLoopPoints(0, data.size, -1)
            track.play()
            hum = track
        }.onFailure { TsLog.w("SOUND hum failed: $it") }
    }

    fun stopHum() {
        hum?.let { runCatching { it.stop(); it.release() } }
        hum = null
    }

    fun release() = stopHum()

    companion object {
        /** Design default volume 40 %. */
        const val VOLUME = 0.4f
    }
}
