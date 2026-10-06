// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Marek Dudka

package com.marek.pulsorb

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PresetsTest {
    @Test
    fun builtInPresetsAreValid() {
        assertTrue(BUILT_IN_PRESETS.size >= 5)
        assertEquals(BUILT_IN_PRESETS.size, BUILT_IN_PRESETS.map { it.name }.toSet().size, "duplicate names")
        for (p in BUILT_IN_PRESETS) {
            assertTrue(p.circles.isNotEmpty() && p.circles.size <= MAX_CIRCLES, p.name)
            assertTrue(p.description.isNotBlank(), "${p.name} has no description")
            // Already within every limit: sanitizing must not change anything.
            assertEquals(p, p.sanitized(), "${p.name} has out-of-range values")
        }
    }

    /** Position is sound (height = tempo, left-right = volume), so presets must be laid out with care. */
    @Test
    fun builtInPresetCirclesDoNotOverlap() {
        // Reference 900x700 window: 32 px margins, 80 px circles, the folded header at the top left.
        fun px(c: PresetCircle) = Pair(32 + c.volume * 836.0, 32 + (1 - yForBpm(c.bpm)) * 636.0)
        for (preset in BUILT_IN_PRESETS) {
            val pts = preset.circles.map(::px)
            for (i in pts.indices) {
                for (j in i + 1 until pts.size) {
                    val d = kotlin.math.hypot(pts[i].first - pts[j].first, pts[i].second - pts[j].second)
                    assertTrue(d >= 90, "${preset.name}: circles $i and $j overlap (${d.roundToInt()} px apart)")
                }
                val (x, y) = pts[i]
                assertFalse(x - 40 < 148 && y - 40 < 68, "${preset.name}: circle $i is under the header")
            }
        }
    }

    @Test
    fun builtInPresetsHaveUniqueFileNames() {
        val files = BUILT_IN_PRESETS.map { presetFileName(it.name) }
        assertEquals(files.size, files.toSet().size)
    }

    @Test
    fun loadedCirclesReproduceEveryPreset() {
        for (preset in BUILT_IN_PRESETS) for (p in preset.circles) {
            val c = CircleState(1, Voice(DrumSound.valueOf(p.sound), 44_100), Color.White, p.volume, yForBpm(p.bpm),
                angle = angleFor(p.frequency), echo = p.echo, reverb = p.reverb)
            assertTrue(abs(c.bpm - p.bpm) < 1e-3, "${preset.name}: ${c.bpm} BPM, expected ${p.bpm}")
            assertTrue(abs(c.frequency - p.frequency) / p.frequency < 1e-4, "${preset.name}: ${c.frequency} Hz")
            assertEquals(p.volume.toDouble(), c.voice.volume)
        }
    }

    @Test
    fun musicBoxBellsArePentatonicAndSnareIsOnTheBackbeat() {
        val box = BUILT_IN_PRESETS.first()
        assertEquals("Music Box", box.name)
        val pentatonic = setOf(9, 0, 2, 4, 7) // A C D E G
        for (p in box.circles.filter { it.sound == "BELL" }) {
            val semitone = (12 * log2(p.frequency / 440.0) + 9).roundToInt().mod(12)
            assertTrue(semitone in pentatonic, "${p.frequency} Hz is not in A minor pentatonic")
        }
        val snare = box.circles.single { it.sound == "SNARE" }
        assertEquals(0.5, snare.startDelay, 1e-9) // one beat at 120 BPM
    }

    @Test
    fun jsonRoundTripKeepsEverything() {
        for (p in BUILT_IN_PRESETS) assertEquals(p, decodePreset(encodePreset(p)))
    }

    @Test
    fun malformedOrOversizedFilesAreRejected() {
        assertNull(decodePreset("not json"))
        assertNull(decodePreset("{\"name\": 5}"))
        assertNull(decodePreset("[]"))
        val huge = """{"name":"x","circles":[],"pad":"${"a".repeat(MAX_PRESET_BYTES)}"}"""
        assertNull(decodePreset(huge))
    }

    @Test
    fun untrustedValuesAreClamped() {
        val text = """
            {"name": "  Evil\u0000\u001b[31m name that is far too long for the list  ",
             "circles": [
               {"sound": "KICK", "bpm": 99999, "volume": -3, "frequency": 1e9, "echo": 7, "reverb": -1, "startDelay": 1e12},
               {"sound": "LASER", "bpm": 120, "volume": 0.5, "frequency": 440},
               {"sound": "BELL", "bpm": 1, "volume": 2, "frequency": 1, "extra": "ignored"}
             ]}
        """.trimIndent()
        val p = assertNotNull(decodePreset(text))
        assertFalse(p.name.any { it.isISOControl() }, "control characters kept")
        assertTrue(p.name.length <= 40)
        assertEquals(listOf("KICK", "BELL"), p.circles.map { it.sound }, "unknown sound must be dropped")
        val kick = p.circles[0]
        assertEquals(MAX_BPM, kick.bpm)
        assertEquals(0f, kick.volume)
        assertEquals(MAX_FREQUENCY, kick.frequency)
        assertEquals(1f, kick.echo)
        assertEquals(0f, kick.reverb)
        assertEquals(10.0, kick.startDelay)
        val bell = p.circles[1]
        assertEquals(MIN_BPM, bell.bpm)
        assertEquals(1f, bell.volume)
        assertEquals(MIN_FREQUENCY, bell.frequency)
    }

    @Test
    fun notANumberBecomesADefault() {
        val p = Preset("n", circles = listOf(PresetCircle("SNARE", Double.NaN, Float.NaN, Double.NaN, startDelay = Double.NaN))).sanitized()
        val c = p.circles.single()
        assertEquals(120.0, c.bpm)
        assertEquals(0.5f, c.volume)
        assertEquals(DrumSound.SNARE.defaultFrequency, c.frequency)
        assertEquals(0.0, c.startDelay)
    }

    @Test
    fun tooManyCirclesAreCut() {
        val p = Preset("many", circles = List(50) { PresetCircle("KICK", 120.0, 0.5f, 60.0) }).sanitized()
        assertEquals(MAX_CIRCLES, p.circles.size)
    }

    @Test
    fun fileNamesCannotEscapeThePresetsFolder() {
        assertEquals("etc_passwd.json", presetFileName("../../etc/passwd"))
        assertEquals("My_Groove.json", presetFileName("My Groove!"))
        assertEquals("preset.json", presetFileName("   "))
        assertEquals("preset.json", presetFileName("ąęś"))
        assertTrue(isSafePresetFileName(presetFileName("C:\\Windows\\system32")))
        for (bad in listOf("../x.json", "a/b.json", ".json", "x.json.exe", "x.txt", "a\\b.json")) {
            assertFalse(isSafePresetFileName(bad), bad)
        }
    }

    /** Saving records each circle's time to its next hit; restoring must give exactly the same groove. */
    @Test
    fun savedTimingRestoresTheSameGroove() {
        val sr = 44_100
        fun pair() = listOf(
            Voice(DrumSound.KICK, sr).apply { bpm = 120.0 },
            Voice(DrumSound.BELL, sr).apply { bpm = 80.0; delayStart(0.3) },
        )
        val original = pair()
        repeat((1.234 * sr).toInt()) { original.forEach { it.render() } }
        val delays = original.map { it.secondsUntilNextHit }

        val restored = listOf(
            Voice(DrumSound.KICK, sr).apply { bpm = 120.0; delayStart(delays[0]) },
            Voice(DrumSound.BELL, sr).apply { bpm = 80.0; delayStart(delays[1]) },
        )
        fun hitTimes(voices: List<Voice>): List<List<Int>> {
            val hits = List(voices.size) { mutableListOf<Int>() }
            val last = voices.map { it.beat.value }.toMutableList()
            for (n in 0 until 3 * sr) voices.forEachIndexed { i, v ->
                v.render()
                if (v.beat.value != last[i]) { hits[i] += n; last[i] = v.beat.value }
            }
            return hits
        }
        assertEquals(hitTimes(original), hitTimes(restored))
    }
}
