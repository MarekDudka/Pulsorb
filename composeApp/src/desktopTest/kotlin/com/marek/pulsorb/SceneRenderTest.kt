// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Marek Dudka

package com.marek.pulsorb

import androidx.compose.ui.graphics.Color
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** Renders the start-up scene offline (no sound card) and checks the mix is clean. */
class SceneRenderTest {
    @Test
    fun defaultSceneMixesWithoutClipping() {
        val sr = 44_100
        val voices = DEFAULT_SCENE.mapIndexed { i, p ->
            val c = CircleState(i + 1, Voice(p.sound, sr), Color.White, p.volume, yForBpm(p.bpm)).apply {
                angle = angleFor(p.frequency)
                reverb = p.reverb
                echo = p.echo
            }
            c.voice.delayStart(p.offsetBeats * 60.0 / SCENE_BPM)
            c.voice
        }
        // 30 beats at 120 BPM = 15 s, one full cycle of the melody; render two cycles.
        val pcm = ShortArray(30 * sr) { SoundEngine.mix(voices.sumOf { it.render() }) }

        // The soft limiter may catch rare peaks, but the mix must not be squashed.
        val nearMax = pcm.count { abs(it.toInt()) > 30_000 }
        assertTrue(nearMax < pcm.size / 2000, "mix is clipping: $nearMax samples near full scale")
        val loud = pcm.count { abs(it.toInt()) > 25_000 }
        assertTrue(loud < pcm.size / 50, "mix is over-compressed: $loud samples above -2.4 dB")
        val peak = pcm.maxOf { abs(it.toInt()) }
        assertTrue(peak > 8_000, "mix is too quiet (peak $peak)")

        // Leave the render for listening: composeApp/build/demo/default-scene.wav
        File("build/demo").apply { mkdirs() }.resolve("default-scene.wav").writeBytes(encodeWav(pcm, sr))
    }
}
