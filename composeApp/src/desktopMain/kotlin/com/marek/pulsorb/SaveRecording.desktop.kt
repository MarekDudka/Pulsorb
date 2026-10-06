// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Marek Dudka

package com.marek.pulsorb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

actual suspend fun saveRecording(wav: ByteArray): String = withContext(Dispatchers.IO) {
    saveRecordingTo(File(System.getProperty("user.home"), "Music/Pulsorb"), wav).absolutePath
}

/** Writes the WAV into [dir] under a new timestamped name; never overwrites an existing file. */
internal fun saveRecordingTo(dir: File, wav: ByteArray, now: Date = Date()): File {
    dir.mkdirs()
    check(dir.isDirectory) { "cannot create folder $dir" }
    val base = "Pulsorb-${SimpleDateFormat("yyyyMMdd-HHmmss").format(now)}"
    var n = 1
    while (true) {
        val file = File(dir, if (n == 1) "$base.wav" else "$base-$n.wav")
        // createNewFile is atomic, so two saves in the same second can't pick the same name.
        if (file.createNewFile()) {
            file.writeBytes(wav)
            return file
        }
        n++
    }
}
