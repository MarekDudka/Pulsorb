// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Marek Dudka

package com.marek.pulsorb

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VoiceTest {
    private val sr = 44_100

    private fun Voice.renderN(n: Int) = DoubleArray(n) { render() }

    private fun energy(x: DoubleArray, from: Int, to: Int) = (from until to).sumOf { x[it] * x[it] }

    /** A short constant "click" used as a deterministic sample instead of microphone audio. */
    private fun impulse(length: Int = 200) = FloatArray(length) { 0.5f }

    @Test
    fun beatsFollowTheTempo() {
        val v = Voice(DrumSound.KICK, sr).apply { bpm = 120.0 }
        v.renderN(2 * sr) // 2 s at 120 BPM: hits at 0, 0.5, 1.0, 1.5 s
        assertEquals(4L, v.beat.value)
    }

    @Test
    fun fasterTempoGivesMoreBeats() {
        val slow = Voice(DrumSound.KICK, sr).apply { bpm = 60.0 }
        val fast = Voice(DrumSound.KICK, sr).apply { bpm = 240.0 }
        slow.renderN(2 * sr)
        fast.renderN(2 * sr)
        assertEquals(2L, slow.beat.value)
        assertEquals(8L, fast.beat.value)
    }

    @Test
    fun mutedVoiceIsSilentAndDoesNotPulse() {
        val v = Voice(DrumSound.SNARE, sr).apply { muted = true }
        val out = v.renderN(sr)
        assertTrue(out.all { it == 0.0 }, "muted voice produced sound")
        assertEquals(0L, v.beat.value)
    }

    @Test
    fun higherVolumeIsLouder() {
        fun peak(volume: Double): Double {
            val v = Voice(DrumSound.SAMPLE, sr).apply { sample = impulse(4000); this.volume = volume; bpm = 40.0 }
            return v.renderN(sr).maxOf { abs(it) }
        }
        val quiet = peak(0.2)
        val loud = peak(0.8)
        assertTrue(loud > quiet * 3.5, "loud=$loud quiet=$quiet")
    }

    @Test
    fun frequencySetsThePitch() {
        // Count zero crossings of the kick body after the pitch drop has settled.
        fun crossings(freq: Double): Int {
            val v = Voice(DrumSound.KICK, sr).apply { frequency = freq; bpm = 40.0; volume = 1.0 }
            val out = v.renderN(sr / 4)
            val window = out.copyOfRange(sr / 10, sr / 5)
            return (1 until window.size).count { (window[it - 1] < 0) != (window[it] < 0) }
        }
        val low = crossings(100.0)
        val high = crossings(200.0)
        val ratio = high.toDouble() / low
        assertTrue(ratio in 1.8..2.2, "low=$low high=$high ratio=$ratio")
    }

    @Test
    fun bellRingsAtItsFrequency() {
        val v = Voice(DrumSound.BELL, sr).apply { frequency = 440.0; bpm = 40.0; volume = 1.0 }
        val out = v.renderN(sr)
        // Fundamental dominates after the overtone decays: ~440 Hz = ~88 zero crossings per 0.1 s.
        val window = out.copyOfRange(sr / 2, sr / 2 + sr / 10)
        val crossings = (1 until window.size).count { (window[it - 1] < 0) != (window[it] < 0) }
        assertTrue(crossings in 84..92, "crossings=$crossings")
        val endPeak = out.copyOfRange(out.size - sr / 20, out.size).maxOf { abs(it) }
        assertTrue(endPeak > 0.01, "bell should still ring after 1 s (peak $endPeak)")
    }

    @Test
    fun delayedStartShiftsTheFirstHit() {
        val v = Voice(DrumSound.KICK, sr).apply { bpm = 120.0 }
        v.delayStart(0.5)
        v.renderN(sr / 2)
        assertEquals(0L, v.beat.value)
        v.renderN(1)
        assertEquals(1L, v.beat.value)
    }

    @Test
    fun emptySampleVoiceIsSilentAndDoesNotPulse() {
        val v = Voice(DrumSound.SAMPLE, sr)
        assertTrue(v.renderN(sr).all { it == 0.0 })
        assertEquals(0L, v.beat.value)
    }

    @Test
    fun sampleSpeedFollowsFrequency() {
        fun playedLength(freq: Double): Int {
            val v = Voice(DrumSound.SAMPLE, sr).apply { sample = impulse(1000); frequency = freq; bpm = 40.0 }
            return v.renderN(sr).count { it != 0.0 }
        }
        // 440 Hz = original speed, 880 Hz = twice as fast, 220 Hz = half speed.
        assertTrue(playedLength(440.0) in 997..1000)
        assertTrue(playedLength(880.0) in 498..501)
        assertTrue(playedLength(220.0) in 1995..2000)
    }

    @Test
    fun echoRepeatsTheHitThreeQuartersOfABeatLater() {
        fun out(echo: Double): DoubleArray {
            val v = Voice(DrumSound.SAMPLE, sr).apply { sample = impulse(); bpm = 120.0; volume = 1.0; this.echo = echo }
            return v.renderN(2 * sr)
        }
        val hit = sr / 2                          // second beat, after the smoothing has settled
        val repeat = hit + (0.75 * sr / 2).toInt()
        val withEcho = energy(out(1.0), repeat, repeat + 200)
        val dry = energy(out(0.0), repeat, repeat + 200)
        assertEquals(0.0, dry)
        assertTrue(withEcho > 1.0, "echo energy $withEcho")
    }

    @Test
    fun reverbAddsATail() {
        fun tail(reverb: Double): Double {
            val v = Voice(DrumSound.SAMPLE, sr).apply { sample = impulse(); bpm = 120.0; volume = 1.0; this.reverb = reverb }
            val out = v.renderN(sr)
            return energy(out, sr / 2 + 400, sr / 2 + 8000)
        }
        assertEquals(0.0, tail(0.0))
        assertTrue(tail(1.0) > 0.01, "reverb tail ${tail(1.0)}")
    }

    @Test
    fun extremeSettingsStayFiniteAndBounded() {
        for (sound in DrumSound.entries) {
            for ((bpm, freq) in listOf(40.0 to MIN_FREQUENCY, 240.0 to MAX_FREQUENCY)) {
                val v = Voice(sound, sr).apply {
                    this.bpm = bpm; frequency = freq; volume = 1.0; echo = 1.0; reverb = 1.0
                    if (sound == DrumSound.SAMPLE) sample = FloatArray(3000) { if (it % 2 == 0) 0.9f else -0.9f }
                }
                val out = v.renderN(3 * sr)
                assertTrue(out.all { it.isFinite() }, "$sound at $bpm BPM / $freq Hz produced NaN or infinity")
                assertTrue(out.maxOf { abs(it) } < 10.0, "$sound at $bpm BPM / $freq Hz is unstable")
            }
        }
    }

    @Test
    fun removedVoiceFadesOutAndFinishes() {
        val v = Voice(DrumSound.HIHAT, sr).apply { bpm = 240.0; volume = 1.0 }
        v.renderN(sr / 2)
        v.removed = true
        val out = v.renderN(sr / 2)
        assertTrue(v.finished)
        assertTrue(abs(out.last()) < 1e-3)
    }
}
