// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Marek Dudka

package com.marek.pulsorb

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class WavTest {
    private fun ByteArray.le32(at: Int) = (0..3).sumOf { (this[at + it].toInt() and 0xFF) shl (8 * it) }
    private fun ByteArray.le16(at: Int) = (this[at].toInt() and 0xFF) or (this[at + 1].toInt() shl 8)
    private fun ByteArray.text(at: Int) = (at until at + 4).map { this[it].toInt().toChar() }.joinToString("")

    @Test
    fun headerDescribesMono16BitPcm() {
        val wav = encodeWav(ShortArray(100), 44_100)
        assertEquals("RIFF", wav.text(0))
        assertEquals(wav.size - 8, wav.le32(4))
        assertEquals("WAVE", wav.text(8))
        assertEquals("fmt ", wav.text(12))
        assertEquals(16, wav.le32(16))      // fmt chunk size
        assertEquals(1, wav.le16(20))       // PCM
        assertEquals(1, wav.le16(22))       // mono
        assertEquals(44_100, wav.le32(24))  // sample rate
        assertEquals(88_200, wav.le32(28))  // byte rate
        assertEquals(2, wav.le16(32))       // block align
        assertEquals(16, wav.le16(34))      // bits per sample
        assertEquals("data", wav.text(36))
        assertEquals(200, wav.le32(40))
        assertEquals(244, wav.size)
    }

    @Test
    fun samplesAreLittleEndianSigned() {
        val wav = encodeWav(shortArrayOf(1, -1, Short.MAX_VALUE, Short.MIN_VALUE), 8000)
        assertContentEquals(
            byteArrayOf(1, 0, -1, -1, -1, 0x7F, 0, -128),
            wav.copyOfRange(44, 52),
        )
    }

    @Test
    fun chunkedEncodingMatchesSingleArray() {
        val a = ShortArray(300) { (it * 7).toShort() }
        val b = ShortArray(212) { (-it * 3).toShort() }
        assertContentEquals(encodeWav(a + b, 44_100), encodeWav(listOf(a, b), 44_100))
    }

    @Test
    fun emptyRecordingIsAValidEmptyWav() {
        val wav = encodeWav(emptyList(), 44_100)
        assertEquals(44, wav.size)
        assertEquals(0, wav.le32(40))
    }
}
