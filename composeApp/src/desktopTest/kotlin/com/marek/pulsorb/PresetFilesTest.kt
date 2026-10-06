// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Marek Dudka

package com.marek.pulsorb

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PresetFilesTest {
    private lateinit var root: File
    private lateinit var originalDir: File

    @BeforeTest
    fun useTempDir() {
        root = Files.createTempDirectory("pulsorb-presets").toFile()
        originalDir = PresetFiles.dir
        PresetFiles.dir = File(root, "presets")
    }

    @AfterTest
    fun cleanUp() {
        PresetFiles.dir = originalDir
        root.deleteRecursively()
    }

    @Test
    fun saveListLoadAndDelete() {
        val groove = BUILT_IN_PRESETS[1].copy(name = "My Groove")
        val file = PresetStore.save(groove)
        assertEquals("My_Groove.json", file)

        val saved = PresetStore.saved().single()
        assertEquals(file, saved.fileName)
        assertEquals(groove.sanitized(), saved.preset)

        PresetStore.delete(file)
        assertTrue(PresetStore.saved().isEmpty())
    }

    @Test
    fun savingTheSameNameReplacesIt() {
        PresetStore.save(BUILT_IN_PRESETS[0].copy(name = "Mine"))
        PresetStore.save(BUILT_IN_PRESETS[2].copy(name = "Mine"))
        val saved = PresetStore.saved().single()
        assertEquals(BUILT_IN_PRESETS[2].circles, saved.preset.circles)
        assertEquals(listOf("Mine.json"), PresetFiles.dir.list()!!.toList(), "temporary files left behind")
    }

    @Test
    fun brokenAndForeignFilesAreSkipped() {
        PresetStore.save(BUILT_IN_PRESETS[0].copy(name = "Good"))
        File(PresetFiles.dir, "broken.json").writeText("{ this is not json")
        File(PresetFiles.dir, "huge.json").writeText("x".repeat(MAX_PRESET_BYTES + 1))
        File(PresetFiles.dir, "notes.txt").writeText("hello")
        File(PresetFiles.dir, "sub.json").mkdir()
        assertEquals(listOf("Good"), PresetStore.saved().map { it.preset.name })
    }

    @Test
    fun pathTraversalIsRefused() {
        val outside = File(root, "secret.json").apply { writeText("{}") }
        for (name in listOf("../secret.json", "/etc/passwd", "..\\secret.json")) {
            assertFailsWith<IllegalArgumentException> { PresetFiles.read(name) }
            assertFailsWith<IllegalArgumentException> { PresetFiles.write(name, "x") }
            assertFailsWith<IllegalArgumentException> { PresetFiles.delete(name) }
        }
        assertTrue(outside.exists())
    }

    @Test
    fun missingFolderMeansNoPresets() {
        assertTrue(PresetFiles.list().isEmpty())
        assertNull(PresetFiles.read("nothing.json"))
    }
}
