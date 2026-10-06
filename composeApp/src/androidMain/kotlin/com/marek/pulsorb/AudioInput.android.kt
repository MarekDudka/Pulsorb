// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Marek Dudka

package com.marek.pulsorb

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

actual class AudioInput actual constructor(sampleRate: Int) {
    @SuppressLint("MissingPermission") // permission is requested via rememberMicPermission() before recording
    private val record = AudioRecord(
        MediaRecorder.AudioSource.MIC,
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) * 2,
    )

    actual fun start() {
        check(record.state == AudioRecord.STATE_INITIALIZED) { "microphone not available" }
        record.startRecording()
    }

    actual fun read(buffer: ShortArray, size: Int): Int = record.read(buffer, 0, size)

    actual fun stop() {
        if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
        record.release()
    }
}

@Composable
actual fun rememberMicPermission(): MicPermission {
    val context = LocalContext.current
    val pending = remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pending.value?.invoke(granted)
        pending.value = null
    }
    return remember(context, launcher) {
        MicPermission { onResult ->
            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                onResult(true)
            } else {
                pending.value = onResult
                launcher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
}
