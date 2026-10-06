// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Marek Dudka

package com.marek.pulsorb

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SoundEngineTest {
    @Test
    fun voicesCanBeAddedAndRemovedWhileStopped() {
        val engine = SoundEngine()
        val kick = engine.addVoice(DrumSound.KICK)
        engine.addVoice(DrumSound.SNARE)
        assertEquals(2, engine.voiceCount)
        engine.removeVoice(kick)
        assertEquals(1, engine.voiceCount)
        assertTrue(kick.removed)
    }

    @Test
    fun recordingWhileStoppedReturnsNothing() = runTest {
        val engine = SoundEngine()
        assertTrue(engine.startRecording().await().isEmpty())
    }

    @Test
    fun mixIsSoftClippedTo16Bit() {
        assertEquals(0, SoundEngine.mix(0.0).toInt())
        assertTrue(SoundEngine.mix(100.0) <= Short.MAX_VALUE)
        assertTrue(SoundEngine.mix(-100.0) >= -Short.MAX_VALUE)
        assertTrue(SoundEngine.mix(0.5) > SoundEngine.mix(0.25))
        assertTrue(SoundEngine.mix(-0.5) < 0)
    }
}
