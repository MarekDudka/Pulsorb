// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Marek Dudka

package com.marek.pulsorb

import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** Renders every built-in preset offline (no sound card) and checks each mix is clean. */
class SceneRenderTest {
    @Test
    fun builtInPresetsMixWithoutClipping() {
        val sr = 44_100
        File("build/demo").mkdirs()
        for (preset in BUILT_IN_PRESETS) {
            val voices = preset.circles.map { p ->
                Voice(DrumSound.valueOf(p.sound), sr).apply {
                    bpm = p.bpm; volume = p.volume.toDouble(); frequency = p.frequency
                    echo = p.echo.toDouble(); reverb = p.reverb.toDouble(); delayStart(p.startDelay)
                }
            }
            val pcm = ShortArray(30 * sr) { SoundEngine.mix(voices.sumOf { it.render() }) }

            // The soft limiter may catch rare peaks, but the mix must not be squashed or silent.
            val nearMax = pcm.count { abs(it.toInt()) > 30_000 }
            assertTrue(nearMax < pcm.size / 2000, "${preset.name} is clipping: $nearMax samples near full scale")
            val loud = pcm.count { abs(it.toInt()) > 25_000 }
            assertTrue(loud < pcm.size / 50, "${preset.name} is over-compressed: $loud samples above -2.4 dB")
            val peak = pcm.maxOf { abs(it.toInt()) }
            assertTrue(peak > 6_000, "${preset.name} is too quiet (peak $peak)")

            // Leave each render for listening: composeApp/build/demo/<preset>.wav
            File("build/demo", presetFileName(preset.name).replace(".json", ".wav")).writeBytes(encodeWav(pcm, sr))
        }
    }
}
