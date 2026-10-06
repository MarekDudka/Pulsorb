// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Marek Dudka

package com.marek.pulsorb

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

/** Application context, set by MainActivity. */
internal lateinit var appContext: Context

actual suspend fun saveRecording(wav: ByteArray): String = withContext(Dispatchers.IO) {
    val name = "Pulsorb-${SimpleDateFormat("yyyyMMdd-HHmmss").format(Date())}.wav"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // Shared Music/Pulsorb folder – visible in file managers and music apps, no permission needed.
        val resolver = appContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/Pulsorb")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("cannot create file")
        try {
            val stream = resolver.openOutputStream(uri) ?: error("cannot open file")
            stream.use { it.write(wav) }
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            // Don't leave a half-written, hidden "pending" entry behind.
            resolver.delete(uri, null, null)
            throw e
        }
        "Music/Pulsorb/$name"
    } else {
        val dir = appContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            ?: error("storage not available")
        val file = File(dir, name)
        file.writeBytes(wav)
        file.absolutePath
    }
}
