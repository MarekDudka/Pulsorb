// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Marek Dudka

package com.marek.pulsorb

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CircleStateTest {
    private fun circle(sound: DrumSound = DrumSound.KICK, x: Float = 0.5f, y: Float = 0.5f) =
        CircleState(1, Voice(sound, 44_100), androidx.compose.ui.graphics.Color.Red, x, y)

    private fun assertClose(expected: Double, actual: Double, tolerance: Double = 1e-6) =
        assertTrue(abs(expected - actual) <= tolerance * expected, "expected $expected but was $actual")

    @Test
    fun movingUpIncreasesTempo() {
        assertClose(40.0, circle(y = 0f).bpm)
        assertClose(240.0, circle(y = 1f).bpm)
        assertTrue(circle(y = 0.7f).bpm > circle(y = 0.3f).bpm)
    }

    @Test
    fun movingRightIncreasesVolume() {
        assertEquals(0.0, circle(x = 0f).volume)
        assertEquals(1.0, circle(x = 1f).volume)
    }

    @Test
    fun rotationSweeps60HzTo18kHz() {
        val c = circle()
        c.rotateBy(-100_000f)
        assertClose(MIN_FREQUENCY, c.frequency)
        c.rotateBy(100_000f)
        assertClose(MAX_FREQUENCY, c.frequency)
    }

    @Test
    fun eachSoundStartsAtItsDefaultPitch() {
        for (sound in DrumSound.entries) assertClose(sound.defaultFrequency, circle(sound).frequency, 1e-4)
    }

    @Test
    fun oneTurnIsAboutTwoOctaves() {
        val c = circle()
        val before = c.frequency
        c.rotateBy(360f)
        val octaves = kotlin.math.log2(c.frequency / before)
        assertTrue(octaves in 2.0..2.1, "one turn = $octaves octaves")
    }

    @Test
    fun positionIsClampedToTheScreen() {
        val c = circle(x = 0.9f, y = 0.1f)
        c.moveBy(5f, -5f)
        assertEquals(1f, c.x)
        assertEquals(0f, c.y)
    }

    @Test
    fun changesReachTheVoiceImmediately() {
        val c = circle()
        c.moveBy(0.3f, 0.2f)
        c.rotateBy(360f)
        c.echo = 0.4f
        c.reverb = 0.6f
        assertEquals(c.volume, c.voice.volume)
        assertEquals(c.bpm, c.voice.bpm)
        assertEquals(c.frequency, c.voice.frequency)
        assertEquals(0.4f.toDouble(), c.voice.echo)
        assertEquals(0.6f.toDouble(), c.voice.reverb)
    }

    /** Regression: a muted circle has no beats, so unmuting must not wait for one. */
    @Test
    fun unmutingAMutedCircleMakesItPlayAgain() {
        val c = circle(x = 0.8f)
        c.voice.bpm = 240.0
        c.muted = true
        assertTrue(c.voice.muted)
        repeat(44_100) { c.voice.render() }
        val beatsWhileMuted = c.voice.beat.value

        c.muted = false
        assertEquals(false, c.voice.muted)
        val out = DoubleArray(44_100) { c.voice.render() }
        assertTrue(c.voice.beat.value > beatsWhileMuted, "no beats after unmuting")
        assertTrue(out.maxOf { abs(it) } > 0.1, "silent after unmuting")
    }

    @Test
    fun labelContainsSoundAndNumber() {
        assertEquals("SNARE 1", circle(DrumSound.SNARE).label)
    }

    @Test
    fun frequencyIsFormattedCompactly() {
        assertEquals("60", formatHz(60.0))
        assertEquals("999", formatHz(999.4))
        assertEquals("1.2k", formatHz(1234.0))
        assertEquals("18.0k", formatHz(18_000.0))
    }
}
