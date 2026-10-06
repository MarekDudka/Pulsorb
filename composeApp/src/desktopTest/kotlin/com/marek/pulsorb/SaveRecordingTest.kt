// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Marek Dudka

package com.marek.pulsorb

import java.io.File
import java.nio.file.Files
import java.util.Date
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SaveRecordingTest {
    private val dir: File = Files.createTempDirectory("pulsorb-test").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun savesWavWithTimestampedName() {
        val wav = encodeWav(shortArrayOf(1, 2, 3), 44_100)
        val file = saveRecordingTo(File(dir, "Music/Pulsorb"), wav)
        assertTrue(file.name.matches(Regex("""Pulsorb-\d{8}-\d{6}\.wav""")), file.name)
        assertContentEquals(wav, file.readBytes())
    }

    @Test
    fun recordingsInTheSameSecondDoNotOverwriteEachOther() {
        val now = Date()
        val first = saveRecordingTo(dir, byteArrayOf(1), now)
        val second = saveRecordingTo(dir, byteArrayOf(2), now)
        val third = saveRecordingTo(dir, byteArrayOf(3), now)
        assertEquals(3, setOf(first, second, third).size)
        assertContentEquals(byteArrayOf(1), first.readBytes())
        assertContentEquals(byteArrayOf(2), second.readBytes())
        assertTrue(second.name.endsWith("-2.wav") && third.name.endsWith("-3.wav"))
    }

    @Test
    fun failsClearlyWhenFolderCannotBeCreated() {
        val blocker = File(dir, "file").apply { writeText("x") }
        assertFailsWith<IllegalStateException> { saveRecordingTo(File(blocker, "sub"), byteArrayOf(1)) }
    }
}
