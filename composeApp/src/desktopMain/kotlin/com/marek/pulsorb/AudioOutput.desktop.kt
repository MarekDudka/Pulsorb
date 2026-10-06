// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Marek Dudka

package com.marek.pulsorb

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

actual val pausesInBackground: Boolean = false

actual class AudioOutput actual constructor(private val sampleRate: Int) {
    private val format = AudioFormat(sampleRate.toFloat(), 16, 1, true, false)
    private val line: SourceDataLine = AudioSystem.getSourceDataLine(format)
    private var bytes = ByteArray(0)

    actual fun start() {
        line.open(format, sampleRate / 10 * 2) // ~100 ms buffer
        line.start()
    }

    actual fun write(buffer: ShortArray, size: Int) {
        if (bytes.size < size * 2) bytes = ByteArray(size * 2)
        for (i in 0 until size) {
            val s = buffer[i].toInt()
            bytes[2 * i] = (s and 0xFF).toByte()
            bytes[2 * i + 1] = (s shr 8 and 0xFF).toByte()
        }
        line.write(bytes, 0, size * 2)
    }

    actual fun stop() {
        line.stop()
        line.close()
    }
}
