// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Marek Dudka

package com.marek.pulsorb

import androidx.compose.runtime.Composable
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.TargetDataLine

actual class AudioInput actual constructor(sampleRate: Int) {
    private val format = AudioFormat(sampleRate.toFloat(), 16, 1, true, false)
    private val line: TargetDataLine = AudioSystem.getTargetDataLine(format)
    private var bytes = ByteArray(0)

    actual fun start() {
        line.open(format)
        line.start()
    }

    actual fun read(buffer: ShortArray, size: Int): Int {
        if (bytes.size < size * 2) bytes = ByteArray(size * 2)
        val n = line.read(bytes, 0, size * 2) / 2
        for (i in 0 until n) {
            buffer[i] = ((bytes[2 * i + 1].toInt() shl 8) or (bytes[2 * i].toInt() and 0xFF)).toShort()
        }
        return n
    }

    actual fun stop() {
        line.stop()
        line.close()
    }
}

@Composable
actual fun rememberMicPermission(): MicPermission = MicPermission { it(true) }
