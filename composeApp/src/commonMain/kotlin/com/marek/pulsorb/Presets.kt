// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Marek Dudka

package com.marek.pulsorb

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One circle in a preset. Values are musical (BPM, volume, Hz) so presets survive UI changes. */
@Serializable
data class PresetCircle(
    val sound: String,
    val bpm: Double,
    val volume: Float,
    val frequency: Double,
    val echo: Float = 0f,
    val reverb: Float = 0.15f,
    val muted: Boolean = false,
    /** Seconds until this circle's first hit; keeps the circles of a groove in time with each other. */
    val startDelay: Double = 0.0,
)

@Serializable
data class Preset(
    val name: String,
    val description: String = "",
    val format: Int = 1,
    val circles: List<PresetCircle>,
)

internal const val MAX_PRESET_BYTES = 64 * 1024
private const val MAX_NAME = 40
private const val MAX_DESCRIPTION = 200
private const val MAX_START_DELAY = 10.0

private val json = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
    encodeDefaults = true
}

internal fun encodePreset(preset: Preset): String = json.encodeToString(Preset.serializer(), preset)

/**
 * Parses a preset file. Files are untrusted (users can copy them in), so this never throws:
 * oversized or malformed input gives null, and every value is clamped to what the app supports.
 */
internal fun decodePreset(text: String): Preset? {
    if (text.length > MAX_PRESET_BYTES) return null
    val raw = runCatching { json.decodeFromString(Preset.serializer(), text) }.getOrNull() ?: return null
    return raw.sanitized()
}

internal fun Preset.sanitized(): Preset = Preset(
    name = cleanText(name, MAX_NAME).ifBlank { "Preset" },
    description = cleanText(description, MAX_DESCRIPTION),
    format = 1,
    circles = circles.mapNotNull { it.sanitized() }.take(MAX_CIRCLES),
)

private fun PresetCircle.sanitized(): PresetCircle? {
    val s = DrumSound.entries.firstOrNull { it.name == sound } ?: return null
    return PresetCircle(
        sound = s.name,
        bpm = bpm.clamp(MIN_BPM, MAX_BPM, 120.0),
        volume = volume.toDouble().clamp(0.0, 1.0, 0.5).toFloat(),
        frequency = frequency.clamp(MIN_FREQUENCY, MAX_FREQUENCY, s.defaultFrequency),
        echo = echo.toDouble().clamp(0.0, 1.0, 0.0).toFloat(),
        reverb = reverb.toDouble().clamp(0.0, 1.0, 0.15).toFloat(),
        muted = muted,
        startDelay = startDelay.clamp(0.0, MAX_START_DELAY, 0.0),
    )
}

/** coerceIn that also maps NaN to a default (NaN.coerceIn() stays NaN). */
private fun Double.clamp(min: Double, max: Double, default: Double) = if (isNaN()) default else coerceIn(min, max)

private fun cleanText(s: String, max: Int) = s.filter { !it.isISOControl() }.trim().take(max)

/**
 * Safe file name for a preset: only letters, digits, '-' and '_', so a name like "../../x"
 * can never point outside the presets folder.
 */
internal fun presetFileName(name: String): String {
    val base = name.trim().map { if (it.isLetterOrDigit() && it.code < 128 || it == '-' || it == '_') it else '_' }
        .joinToString("").trim('_').take(MAX_NAME)
    return (base.ifBlank { "preset" }) + ".json"
}

private val SAFE_FILE = Regex("^[A-Za-z0-9_-]{1,$MAX_NAME}\\.json$")

internal fun isSafePresetFileName(fileName: String) = SAFE_FILE.matches(fileName)

/** Platform file access, always inside the app's own presets folder. */
internal expect object PresetFiles {
    fun list(): List<String>
    fun read(fileName: String): String?
    fun write(fileName: String, text: String)
    fun delete(fileName: String)
}

/** A preset saved by the user, with the file it lives in. */
internal class SavedPreset(val fileName: String, val preset: Preset)

internal object PresetStore {
    fun saved(): List<SavedPreset> = PresetFiles.list()
        .mapNotNull { file -> PresetFiles.read(file)?.let(::decodePreset)?.let { SavedPreset(file, it) } }
        .sortedBy { it.preset.name.lowercase() }

    /** Saves (or replaces) the preset; returns its file name. */
    fun save(preset: Preset): String {
        val clean = preset.sanitized()
        val file = presetFileName(clean.name)
        PresetFiles.write(file, encodePreset(clean))
        return file
    }

    fun delete(fileName: String) = PresetFiles.delete(fileName)
}

// ---------------------------------------------------------------------------------------------
// Built-in presets. All patterns and note choices are original; they use common genre grooves
// and scales, which are free for anyone to use – no existing songs or melodies.
// ---------------------------------------------------------------------------------------------

private class PresetBuilder(val baseBpm: Double) {
    val circles = mutableListOf<PresetCircle>()

    fun add(
        sound: DrumSound,
        bpm: Double,
        hz: Double,
        volume: Float,
        offsetBeats: Double = 0.0,
        reverb: Float = 0.15f,
        echo: Float = 0f,
    ) {
        circles += PresetCircle(sound.name, bpm, volume, hz, echo, reverb, startDelay = offsetBeats * 60.0 / baseBpm)
    }
}

private fun preset(name: String, description: String, baseBpm: Double, build: PresetBuilder.() -> Unit) =
    Preset(name, description, circles = PresetBuilder(baseBpm).apply(build).circles)

internal val BUILT_IN_PRESETS: List<Preset> = listOf(
    preset("Music Box", "120 BPM groove with four bells on A-minor pentatonic; the melody repeats every 30 beats.", 120.0) {
        add(DrumSound.KICK, 120.0, 60.0, 0.40f)
        add(DrumSound.SNARE, 60.0, 190.0, 0.22f, offsetBeats = 1.0, reverb = 0.25f)   // beats 2 and 4
        add(DrumSound.HIHAT, 120.0, 420.0, 0.14f, offsetBeats = 0.5)                  // off-beats
        add(DrumSound.BELL, 60.0, 440.00, 0.45f, reverb = 0.35f)                      // A4
        add(DrumSound.BELL, 80.0, 523.25, 0.32f, reverb = 0.35f)                      // C5
        add(DrumSound.BELL, 96.0, 659.25, 0.58f, reverb = 0.35f, echo = 0.25f)        // E5
        add(DrumSound.BELL, 160.0, 783.99, 0.18f, reverb = 0.45f)                     // G5
    },
    preset("Rock Beat", "Kick on 1 and 3, snare on 2 and 4, eighth-note hi-hat and a two-note bass line.", 110.0) {
        add(DrumSound.KICK, 55.0, 60.0, 0.55f)                                        // beats 1 and 3
        add(DrumSound.SNARE, 55.0, 200.0, 0.30f, offsetBeats = 1.0, reverb = 0.2f)    // beats 2 and 4
        add(DrumSound.HIHAT, 220.0, 450.0, 0.20f)                                     // eighth notes
        add(DrumSound.BELL, 110.0, 110.0, 0.36f, reverb = 0.1f)                       // A2 bass on every beat
        add(DrumSound.BELL, 73.33, 164.81, 0.42f, offsetBeats = 0.5, reverb = 0.1f)   // E3 every 1.5 beats
    },
    preset("House", "Four-on-the-floor kick, off-beat hats, a clap on 2 and 4 and C-minor stabs.", 124.0) {
        add(DrumSound.KICK, 124.0, 60.0, 0.48f)
        add(DrumSound.HIHAT, 124.0, 520.0, 0.16f, offsetBeats = 0.5)
        add(DrumSound.SNARE, 62.0, 240.0, 0.25f, offsetBeats = 1.0, reverb = 0.35f)
        add(DrumSound.BELL, 62.0, 261.63, 0.42f, offsetBeats = 1.5, reverb = 0.4f, echo = 0.3f)   // C4
        add(DrumSound.BELL, 82.67, 311.13, 0.55f, offsetBeats = 0.5, reverb = 0.4f, echo = 0.3f)  // Eb4
        add(DrumSound.BELL, 124.0, 392.00, 0.30f, offsetBeats = 0.75, reverb = 0.4f)              // G4
    },
    preset("Polyrhythm 3:4", "Two bells in a 3-against-4 cross-rhythm over a steady pulse, E-minor pentatonic.", 120.0) {
        add(DrumSound.KICK, 60.0, 62.0, 0.42f)
        add(DrumSound.BELL, 120.0, 329.63, 0.38f, reverb = 0.3f)                      // E4, 4 per bar
        add(DrumSound.BELL, 90.0, 493.88, 0.34f, reverb = 0.3f)                       // B4, 3 per bar
        add(DrumSound.BELL, 180.0, 587.33, 0.16f, reverb = 0.4f)                      // D5, 6 per bar
        add(DrumSound.HIHAT, 240.0, 600.0, 0.20f)
    },
    preset("Ambient Drift", "Slow D-major pentatonic bells at unrelated tempos with long reverb – always changing.", 60.0) {
        add(DrumSound.BELL, 40.0, 293.66, 0.62f, reverb = 0.7f, echo = 0.3f)          // D4
        add(DrumSound.BELL, 48.0, 369.99, 0.40f, offsetBeats = 0.7, reverb = 0.7f)    // F#4
        add(DrumSound.BELL, 57.0, 440.00, 0.18f, offsetBeats = 1.9, reverb = 0.7f, echo = 0.2f)  // A4
        add(DrumSound.BELL, 68.0, 493.88, 0.52f, offsetBeats = 3.1, reverb = 0.75f)   // B4
        add(DrumSound.BELL, 81.0, 587.33, 0.30f, offsetBeats = 4.6, reverb = 0.8f)    // D5
    },
    preset("Tom Circle", "Kicks tuned as toms in an interlocking pattern with a shaker-like hi-hat.", 100.0) {
        add(DrumSound.KICK, 100.0, 60.0, 0.45f)
        add(DrumSound.KICK, 50.0, 98.0, 0.28f, offsetBeats = 0.75, reverb = 0.2f)
        add(DrumSound.KICK, 66.67, 147.0, 0.50f, offsetBeats = 0.5, reverb = 0.2f)
        add(DrumSound.KICK, 133.33, 196.0, 0.20f, offsetBeats = 0.25, reverb = 0.25f)
        add(DrumSound.HIHAT, 200.0, 800.0, 0.10f, offsetBeats = 0.25)
    },
)
