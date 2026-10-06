// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Marek Dudka

package com.marek.pulsorb

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SamplePrepTest {
    private val sr = 44_100

    @Test
    fun silenceIsRejected() {
        assertNull(prepareSample(ShortArray(sr), sr, sr))
        assertNull(prepareSample(ShortArray(sr) { 100 }, sr, sr)) // below the audibility threshold
        assertNull(prepareSample(ShortArray(10), 0, sr))
    }

    @Test
    fun leadingAndTrailingSilenceIsTrimmed() {
        val data = ShortArray(2500) { if (it in 1000 until 1500) 10_000 else 0 }
        val out = assertNotNull(prepareSample(data, data.size, sr))
        // Keeps ~5 ms (220 samples) before the hit so the attack is not cut off.
        assertEquals(500 + sr / 200, out.size)
        assertEquals(0f, out.first())
    }

    @Test
    fun peakIsNormalizedAndEndFadesOut() {
        val data = ShortArray(5000) { if (it % 2 == 0) 3000 else -3000 }
        val out = assertNotNull(prepareSample(data, data.size, sr))
        val peak = out.maxOf { abs(it) }
        assertTrue(abs(peak - 0.9f) < 1e-4f, "peak $peak")
        assertEquals(0f, abs(out.last())) // faded to silence (may be -0.0)
    }

    @Test
    fun onlyRecordedSamplesAreUsed() {
        val data = ShortArray(1000) { if (it < 400) 8000 else Short.MAX_VALUE }
        val out = assertNotNull(prepareSample(data, 400, sr))
        assertEquals(400, out.size)
    }
}
