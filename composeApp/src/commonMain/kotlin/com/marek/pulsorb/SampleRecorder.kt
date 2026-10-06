// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Marek Dudka

package com.marek.pulsorb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Records a short one-shot sample from the microphone and prepares it for drum playback. */
class SampleRecorder(private val sampleRate: Int = 44_100) {

    @Volatile private var stopRequested = false

    fun requestStop() {
        stopRequested = true
    }

    /** Records until [requestStop] or [maxSeconds]; returns null if nothing audible was captured. */
    suspend fun record(maxSeconds: Double = 2.0): FloatArray? = withContext(Dispatchers.Default) {
        val maxSamples = (maxSeconds * sampleRate).toInt()
        val data = ShortArray(maxSamples)
        val chunk = ShortArray(1024)
        var count = 0
        val input = AudioInput(sampleRate)
        try {
            input.start()
            while (isActive && !stopRequested && count < maxSamples) {
                val n = input.read(chunk, min(chunk.size, maxSamples - count))
                if (n < 0) break
                chunk.copyInto(data, count, 0, n)
                count += n
            }
        } finally {
            input.stop()
        }
        prepareSample(data, count, sampleRate)
    }
}

/**
 * Trims silence, normalizes and adds a short fade-out so the hit starts right on the beat.
 * Returns null when nothing audible was captured.
 */
internal fun prepareSample(data: ShortArray, count: Int, sampleRate: Int): FloatArray? {
    if (count == 0) return null
    val samples = FloatArray(count) { data[it] / 32768f }
    val peak = samples.maxOf { abs(it) }
    if (peak < 0.01f) return null

    val start = max(0, samples.indexOfFirst { abs(it) > peak * 0.1f } - sampleRate / 200)
    val end = samples.indexOfLast { abs(it) > peak * 0.02f } + 1
    val gain = 0.9f / peak
    val out = FloatArray(end - start) { samples[start + it] * gain }

    val fade = min(out.size, sampleRate / 100)
    for (i in 0 until fade) out[out.size - 1 - i] *= i.toFloat() / fade
    return out
}
