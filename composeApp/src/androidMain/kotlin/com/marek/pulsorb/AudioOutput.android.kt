// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Marek Dudka

package com.marek.pulsorb

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

actual val pausesInBackground: Boolean = true

actual class AudioOutput actual constructor(private val sampleRate: Int) {
    private val track: AudioTrack = run {
        val minSize = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    actual fun start() = track.play()

    actual fun write(buffer: ShortArray, size: Int) {
        track.write(buffer, 0, size)
    }

    actual fun stop() {
        track.stop()
        track.release()
    }
}
