// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Marek Dudka

package com.marek.pulsorb

/** Encodes mono 16-bit PCM as a WAV file. */
fun encodeWav(pcm: ShortArray, sampleRate: Int): ByteArray = encodeWav(listOf(pcm), sampleRate)

/** Encodes mono 16-bit PCM given as consecutive chunks, without first joining them (saves memory). */
fun encodeWav(chunks: List<ShortArray>, sampleRate: Int): ByteArray {
    val dataSize = chunks.sumOf { it.size } * 2
    val out = ByteArray(44 + dataSize)
    var pos = 0
    fun str(s: String) = s.forEach { out[pos++] = it.code.toByte() }
    fun int(v: Int) = repeat(4) { out[pos++] = (v shr (8 * it)).toByte() }
    fun short(v: Int) = repeat(2) { out[pos++] = (v shr (8 * it)).toByte() }

    str("RIFF"); int(36 + dataSize); str("WAVE")
    str("fmt "); int(16); short(1); short(1); int(sampleRate); int(sampleRate * 2); short(2); short(16)
    str("data"); int(dataSize)
    for (chunk in chunks) for (s in chunk) short(s.toInt())
    return out
}

/** Saves a WAV file to the platform's music folder and returns a human-readable location. */
expect suspend fun saveRecording(wav: ByteArray): String
