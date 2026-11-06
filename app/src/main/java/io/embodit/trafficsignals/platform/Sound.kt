package io.embodit.trafficsignals.platform

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import io.embodit.trafficsignals.domain.SoundSynth

/**
 * Plays the short feedback sounds of [SoundSynth]: right, wrong, a car pulling away. Only sounds that answer something you
 * or the cars did; no looping hum (profile B24). Source level near full scale, [volume] from the slider, mute key separate.
 * No whistle (the gestures have none).
 */
class Sound {
    private val rate = SoundSynth.RATE
    var enabled = true

    /** The slider (domain [io.embodit.trafficsignals.domain.Volume]); gain on top of the source level. */
    var volume = 1f

    private fun pcm(fx: SoundSynth.Fx): ShortArray {
        val x = SoundSynth.render(fx)
        return ShortArray(x.size) { i -> (x[i].coerceIn(-1f, 1f) * 32767).toInt().toShort() }
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
            track.setVolume(volume)
            track.setNotificationMarkerPosition(data.size)
            track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack) { runCatching { t.release() } }
                override fun onPeriodicNotification(t: AudioTrack) {}
            })
            track.play()
        }.onFailure { TsLog.w("SOUND play failed: $it") }
    }

    private val ding by lazy { pcm(SoundSynth.Fx.RIGHT) }
    private val doo by lazy { pcm(SoundSynth.Fx.WRONG) }
    private val engine by lazy { pcm(SoundSynth.Fx.CARS) }

    /** Builds the three samples off the main thread (they were lazy on the first answer's frame, build prompt 2026-11-05). */
    fun warm() {
        val t0 = android.os.SystemClock.elapsedRealtime()
        val n = ding.size + doo.size + engine.size
        TsLog.i("SOUND warm samples=$n ms=${android.os.SystemClock.elapsedRealtime() - t0}")
    }

    fun right() = play(ding)

    fun wrong() = play(doo)

    fun carsGo() = play(engine)
}
