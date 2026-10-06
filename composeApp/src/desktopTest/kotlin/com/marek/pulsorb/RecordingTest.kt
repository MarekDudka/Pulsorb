// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Marek Dudka

package com.marek.pulsorb

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioSystem
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Runs the real engine on the sound card (plays ~1 s of drums). */
class RecordingTest {
    @Test
    fun recordsMixedOutputAsAValidWav() = runBlocking {
        val engine = SoundEngine()
        engine.addVoice(DrumSound.KICK).apply { volume = 0.3; bpm = 240.0; echo = 0.5; reverb = 0.5 }
        engine.start(this)
        delay(100)
        val take = engine.startRecording()
        delay(1000)
        engine.stopRecording()
        val chunks = withTimeout(2000) { take.await() }
        engine.stop()
        assertNull(engine.error.value, "audio device failed")

        val samples = chunks.sumOf { it.size }
        assertTrue(samples > engine.sampleRate / 2, "expected ~1 s of audio, got $samples samples")
        assertTrue(chunks.any { c -> c.any { abs(it.toInt()) > 1000 } }, "recording is silent")

        // Decode with Java's own WAV reader to prove the file is valid.
        val wav = encodeWav(chunks, engine.sampleRate)
        AudioSystem.getAudioInputStream(ByteArrayInputStream(wav)).use { stream ->
            assertEquals(engine.sampleRate.toFloat(), stream.format.sampleRate)
            assertEquals(16, stream.format.sampleSizeInBits)
            assertEquals(1, stream.format.channels)
            assertEquals(samples.toLong(), stream.frameLength)
        }
    }

    @Test
    fun voiceAddedWhilePlayingIsHeard() = runBlocking {
        val engine = SoundEngine()
        val old = engine.addVoice(DrumSound.HIHAT).apply { volume = 0.2 }
        engine.start(this)
        delay(200)
        // Remove one voice and add another at the same time – the new one must survive.
        engine.removeVoice(old)
        val kick = engine.addVoice(DrumSound.KICK).apply { volume = 0.3; bpm = 240.0 }
        delay(600)
        engine.stop()
        assertTrue(kick.beat.value > 0, "new voice was never played")
        assertEquals(1, engine.voiceCount)
    }
}
