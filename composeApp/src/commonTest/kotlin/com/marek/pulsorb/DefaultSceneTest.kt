// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Marek Dudka

package com.marek.pulsorb

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultSceneTest {
    /** The scene as the app builds it: one circle per part. */
    private val circles = DEFAULT_SCENE.mapIndexed { i, p ->
        CircleState(i + 1, Voice(p.sound, 44_100), Color.White, p.volume, yForBpm(p.bpm)).apply {
            angle = angleFor(p.frequency)
            reverb = p.reverb
            echo = p.echo
        }
    }

    @Test
    fun circlesReproduceTheScenesTempoPitchAndVolume() {
        for ((c, p) in circles.zip(DEFAULT_SCENE)) {
            assertTrue(abs(c.bpm - p.bpm) < 1e-3, "${c.label}: ${c.bpm} BPM, expected ${p.bpm}")
            assertTrue(abs(c.frequency - p.frequency) / p.frequency < 1e-4, "${c.label}: ${c.frequency} Hz")
            assertEquals(p.volume.toDouble(), c.voice.volume)
        }
    }

    @Test
    fun sceneFitsOnScreenAndInTheCircleLimit() {
        assertTrue(DEFAULT_SCENE.size <= 12)
        for (c in circles) assertTrue(c.x in 0f..1f && c.y in 0f..1f, "${c.label} off screen")
    }

    @Test
    fun bellsPlayAMinorPentatonicNotes() {
        val pentatonic = setOf(9, 0, 2, 4, 7) // A C D E G as semitones from C
        for (p in DEFAULT_SCENE.filter { it.sound == DrumSound.BELL }) {
            val semitone = (12 * log2(p.frequency / 440.0) + 9).roundToInt().mod(12)
            assertTrue(semitone in pentatonic, "${p.frequency} Hz is not in A minor pentatonic")
        }
    }

    @Test
    fun loopsAlignToTheSceneTempo() {
        // Every loop length is a whole number of quarter beats, so the parts stay in time.
        for (p in DEFAULT_SCENE) {
            val quarterBeats = SCENE_BPM / p.bpm * 4
            assertTrue(abs(quarterBeats - quarterBeats.roundToInt()) < 1e-9, "${p.sound} at ${p.bpm} BPM drifts")
        }
    }

    @Test
    fun snareLandsOnTheBackbeat() {
        val snare = DEFAULT_SCENE.single { it.sound == DrumSound.SNARE }
        assertEquals(1.0, snare.offsetBeats)
        assertEquals(SCENE_BPM / 2, snare.bpm)
    }
}
