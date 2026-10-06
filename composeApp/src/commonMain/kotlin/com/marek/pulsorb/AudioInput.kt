// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Marek Dudka

package com.marek.pulsorb

import androidx.compose.runtime.Composable

/** Platform microphone source for mono 16-bit PCM. [read] blocks and returns the number of samples read. */
expect class AudioInput(sampleRate: Int) {
    fun start()
    fun read(buffer: ShortArray, size: Int): Int
    fun stop()
}

fun interface MicPermission {
    fun request(onResult: (granted: Boolean) -> Unit)
}

@Composable
expect fun rememberMicPermission(): MicPermission
