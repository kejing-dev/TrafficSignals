package io.embodit.trafficsignals.domain

import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** QA q3-4, build.md "only short feedback sounds": peak <= -1 dBFS, RMS of the sounding part >= -20 dBFS at the default gain. */
class SoundSynthTest {
    @Test
    fun everyEffectIsLoudEnoughAndDoesNotClip() {
        for (fx in SoundSynth.Fx.entries) {
            val x = SoundSynth.render(fx)
            val peak = SoundSynth.peakDb(x)
            val rms = SoundSynth.soundingRmsDb(x)
            println("LOUD $fx peak=$peak rms=$rms")
            assertTrue("$fx peak $peak", peak <= -1f)
            assertTrue("$fx sounding rms $rms", rms >= -20f)
        }
    }

    @Test
    fun theDefaultPlaysAtFullSourceLevel() = assertEquals(1f, Volume.DEFAULT, 0f)

    @Test
    fun theSliderSnapsToThreeDetents() {
        assertEquals(0.33f, Volume.snap(0f), 0f)
        assertEquals(0.33f, Volume.snap(0.45f), 0f)
        assertEquals(0.66f, Volume.snap(0.55f), 0f)
        assertEquals(0.66f, Volume.snap(0.8f), 0f)
        assertEquals(1f, Volume.snap(0.9f), 0f)
        assertEquals("66%", Volume.percent(0.66f))
    }

    /** Renders the evidence: build/loudness/loudness.wav (16-bit PCM, mono), the sounding section at the default gain. */
    @Test
    fun rendersTheLoudnessEvidence() {
        val x = SoundSynth.soundingSection()
        val out = File("build/loudness/loudness.wav")
        out.parentFile.mkdirs()
        DataOutputStream(FileOutputStream(out)).use { d ->
            fun le32(v: Int) = d.writeInt(Integer.reverseBytes(v))
            fun le16(v: Int) = d.writeShort(java.lang.Short.reverseBytes(v.toShort()).toInt())
            d.writeBytes("RIFF"); le32(36 + x.size * 2); d.writeBytes("WAVEfmt "); le32(16); le16(1); le16(1)
            le32(SoundSynth.RATE); le32(SoundSynth.RATE * 2); le16(2); le16(16); d.writeBytes("data"); le32(x.size * 2)
            for (v in x) le16((v.coerceIn(-1f, 1f) * 32767).toInt())
        }
        assertTrue(SoundSynth.peakDb(x) <= -1f)
        assertTrue(SoundSynth.soundingRmsDb(x) >= -20f)
    }
}
