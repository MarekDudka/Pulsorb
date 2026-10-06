// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Marek Dudka

package com.marek.pulsorb

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal actual object PresetFiles {
    /** Private app storage – no permission needed, removed with the app. */
    private val dir: File get() = File(appContext.filesDir, "presets")

    /** Resolves a file inside [dir], refusing anything that isn't a plain preset file name. */
    private fun file(fileName: String): File {
        require(isSafePresetFileName(fileName)) { "invalid preset file name" }
        return File(dir, fileName)
    }

    actual fun list(): List<String> =
        dir.listFiles { f -> f.isFile && isSafePresetFileName(f.name) && f.length() <= MAX_PRESET_BYTES }
            ?.map { it.name }?.sorted()?.take(200) ?: emptyList()

    actual fun read(fileName: String): String? {
        val f = file(fileName)
        if (!f.isFile || f.length() > MAX_PRESET_BYTES) return null
        return runCatching { f.readText() }.getOrNull()
    }

    actual fun write(fileName: String, text: String) {
        val target = file(fileName)
        dir.mkdirs()
        check(dir.isDirectory) { "cannot create presets folder" }
        // Write to a temporary file first so a crash never leaves a half-written preset.
        val tmp = File(dir, "$fileName.tmp")
        tmp.writeText(text)
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    actual fun delete(fileName: String) {
        file(fileName).delete()
    }
}
