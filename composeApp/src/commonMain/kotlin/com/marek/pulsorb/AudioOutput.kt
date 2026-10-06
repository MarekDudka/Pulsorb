// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Marek Dudka

package com.marek.pulsorb

/**
 * Whether playback pauses when the app goes to the background. True on Android (phones are
 * pocketed, screens turned off); false on desktop, where a minimized player normally keeps playing.
 */
expect val pausesInBackground: Boolean

/** Platform audio sink for mono 16-bit PCM. [write] blocks until the samples are queued. */
expect class AudioOutput(sampleRate: Int) {
    fun start()
    fun write(buffer: ShortArray, size: Int)
    fun stop()
}
