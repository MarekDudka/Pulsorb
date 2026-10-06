// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Marek Dudka

package com.marek.pulsorb

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

const val MIN_FREQUENCY = 60.0
const val MAX_FREQUENCY = 18_000.0

enum class DrumSound(val label: String, val defaultFrequency: Double) {
    KICK("KICK", 60.0),
    SNARE("SNARE", 190.0),
    HIHAT("HAT", 420.0),
    /** Melodic bell/marimba tone – no pitch drop, long ring. */
    BELL("BELL", 440.0),
    /** Microphone recording; 440 Hz = original speed, the frequency scales the playback rate. */
    SAMPLE("SAMPLE", 440.0),
}

/**
 * One independent drum with its own beat clock. Parameters can be changed
 * from the UI thread at any time; [render] is called from the audio thread.
 */
class Voice(val sound: DrumSound, private val sampleRate: Int) {
    @Volatile var bpm: Double = 120.0
    @Volatile var volume: Double = 0.5          // 0..1
    @Volatile var frequency: Double = sound.defaultFrequency
    @Volatile var muted: Boolean = false
    /** Set when the voice is removed; it fades out and is then dropped by the engine. */
    @Volatile var removed: Boolean = false
    /** Echo amount 0..1 – tempo-synced (dotted eighth) delay, more amount = more repeats. */
    @Volatile var echo: Double = 0.0
    /** Reverb amount 0..1 – wet level of a small room/hall reverb. */
    @Volatile var reverb: Double = 0.0
    /** Recorded audio for [DrumSound.SAMPLE] voices (normalized -1..1). */
    @Volatile var sample: FloatArray? = null

    private val _beat = MutableStateFlow(0L)
    /** Incremented on every audible hit – used by the UI to animate. */
    val beat: StateFlow<Long> = _beat

    private var samplesUntilHit = 0
    private var sinceHit = Int.MAX_VALUE / 2
    private var phase = 0.0
    private var overtonePhase = 0.0
    private var samplePos = 0.0
    private val squarePhases = DoubleArray(HAT_RATIOS.size)
    private var hpPrevIn = 0.0
    private var hpPrevOut = 0.0
    private var smoothVolume = 0.0
    private var smoothEcho = 0.0
    private var smoothReverb = 0.0
    private val echoBuffer = FloatArray(sampleRate * 2)
    private var echoWrite = 0
    private val reverbFx = Reverb(sampleRate)

    fun render(): Double {
        if (samplesUntilHit <= 0) {
            samplesUntilHit = (60.0 / bpm * sampleRate).toInt()
            sinceHit = 0
            // A ringing bell keeps its phase so a new hit doesn't click.
            if (sound != DrumSound.BELL) phase = 0.0
            samplePos = 0.0
            if (!muted && (sound != DrumSound.SAMPLE || sample != null)) _beat.value++
        }
        samplesUntilHit--

        val t = sinceHit.toDouble() / sampleRate
        sinceHit++

        val raw = when (sound) {
            DrumSound.KICK -> {
                // Sine body with a fast pitch drop + small click.
                val pitch = frequency * (1.0 + 1.5 * exp(-t * 35.0))
                advance(pitch)
                sin(phase) * exp(-t * 7.0) + noise() * exp(-t * 80.0) * 0.35
            }
            DrumSound.SNARE -> {
                // Short tonal body + long noise "rattle".
                val pitch = frequency * (1.0 + 0.5 * exp(-t * 50.0))
                advance(pitch)
                sin(phase) * exp(-t * 20.0) * 0.6 + noise() * exp(-t * 14.0) * 0.7
            }
            DrumSound.HIHAT -> {
                // 808-style: inharmonic square waves + noise, high-passed, very short.
                var metal = 0.0
                for (i in HAT_RATIOS.indices) {
                    // Skip partials above Nyquist to avoid aliasing at high pitch.
                    val f = frequency * HAT_RATIOS[i]
                    if (f > nyquistLimit) continue
                    squarePhases[i] += f / sampleRate
                    if (squarePhases[i] >= 1.0) squarePhases[i] -= 1.0
                    metal += if (squarePhases[i] < 0.5) 1.0 else -1.0
                }
                val mix = metal / HAT_RATIOS.size * 0.6 + noise() * 0.6
                highPass(mix) * exp(-t * 45.0) * 1.5
            }
            DrumSound.BELL -> {
                // Fundamental + inharmonic marimba-like overtone + a soft mallet strike.
                advance(frequency)
                overtonePhase += 2.0 * PI * (frequency * 3.93).coerceAtMost(nyquistLimit) / sampleRate
                if (overtonePhase > 2.0 * PI) overtonePhase -= 2.0 * PI
                (sin(phase) * exp(-t * 3.0) +
                    sin(overtonePhase) * exp(-t * 14.0) * 0.35 +
                    noise() * exp(-t * 300.0) * 0.1) * 0.8
            }
            DrumSound.SAMPLE -> {
                // One-shot playback with linear interpolation; frequency sets the speed/pitch.
                val s = sample
                if (s == null || samplePos >= s.size - 1) 0.0 else {
                    val i = samplePos.toInt()
                    val frac = samplePos - i
                    samplePos += frequency / sound.defaultFrequency
                    s[i] * (1 - frac) + s[i + 1] * frac
                }
            }
        }

        smoothVolume += ((if (muted || removed) 0.0 else volume) - smoothVolume) * 0.001
        smoothEcho += (echo - smoothEcho) * 0.001
        smoothReverb += (reverb - smoothReverb) * 0.001
        val dry = raw * smoothVolume

        // Echo: delay of 3/4 beat with feedback; only the dry signal scaled by the amount enters the line.
        val delaySamples = (0.75 * 60.0 / bpm * sampleRate).toInt().coerceIn(1, echoBuffer.size - 1)
        var read = echoWrite - delaySamples
        if (read < 0) read += echoBuffer.size
        val delayed = echoBuffer[read].toDouble()
        echoBuffer[echoWrite] = (dry * smoothEcho + delayed * (0.25 + 0.4 * smoothEcho)).toFloat()
        if (++echoWrite == echoBuffer.size) echoWrite = 0
        val withEcho = dry + delayed

        // Reverb: always running so the tail decays naturally when the amount is lowered.
        return withEcho + reverbFx.process(withEcho * smoothReverb)
    }

    /** Delays this voice's first hit, e.g. to put a snare on the backbeat. Call before playback starts. */
    fun delayStart(seconds: Double) {
        samplesUntilHit = (seconds * sampleRate).roundToInt()
    }

    /** Time until the next hit; saved in presets to keep circles in time with each other. */
    val secondsUntilNextHit: Double get() = samplesUntilHit.coerceAtLeast(0).toDouble() / sampleRate

    /** True once a removed voice has faded to silence. */
    val finished: Boolean get() = removed && smoothVolume < 1e-4

    private val nyquistLimit = sampleRate * 0.45

    private fun advance(pitch: Double) {
        phase += 2.0 * PI * pitch.coerceAtMost(nyquistLimit) / sampleRate
        if (phase > 2.0 * PI) phase -= 2.0 * PI
    }

    private fun noise() = Random.nextDouble() * 2.0 - 1.0

    private fun highPass(x: Double): Double {
        val y = 0.85 * (hpPrevOut + x - hpPrevIn)
        hpPrevIn = x
        hpPrevOut = y
        return y
    }

    private companion object {
        val HAT_RATIOS = doubleArrayOf(1.0, 1.4471, 1.6170, 1.9265, 2.5028, 2.6637)
    }
}

/** Mixes all voices into a single platform audio output. */
class SoundEngine(val sampleRate: Int = 44_100) {

    /**
     * Copy-on-write list. Only the caller's (UI) thread replaces it; the audio thread just reads
     * a snapshot, so a voice added at the same moment can never be lost.
     */
    @Volatile private var voices: List<Voice> = emptyList()

    /** Voices currently in the mix, including ones still fading out after removal. */
    val voiceCount: Int get() = voices.size

    /**
     * Adds a voice. [configure] runs before the audio thread can see the voice, so tempo and
     * start delay are in place for its very first hit (important when loading a preset mid-play).
     */
    fun addVoice(sound: DrumSound, configure: Voice.() -> Unit = {}): Voice {
        val voice = Voice(sound, sampleRate).apply(configure)
        voices = voices.filterNot { it.finished } + voice
        return voice
    }

    /** Fades the voice out; it is dropped from the list on a later add/remove/stop once silent. */
    fun removeVoice(voice: Voice) {
        voice.removed = true
        voices = if (job?.isActive == true) voices.filterNot { it.finished } else voices - voice
    }

    private var job: Job? = null

    private val _error = MutableStateFlow<String?>(null)
    /** Set when the audio device fails; playback stops instead of crashing the app. */
    val error: StateFlow<String?> = _error

    // Output recording: only the audio thread touches `take`; the UI only flips `recordRequested`.
    @Volatile private var recordRequested = false
    @Volatile private var recordResult: CompletableDeferred<List<ShortArray>>? = null
    private var take: ArrayList<ShortArray>? = null
    private var takeSamples = 0

    /**
     * Starts capturing the mixed output. The returned deferred completes with the recorded PCM
     * chunks once stopped (empty if the engine is not running).
     */
    fun startRecording(): Deferred<List<ShortArray>> {
        val result = CompletableDeferred<List<ShortArray>>()
        if (job?.isActive != true) {
            result.complete(emptyList())
            return result
        }
        recordResult = result
        recordRequested = true
        return result
    }

    fun stopRecording() {
        recordRequested = false
    }

    /** Audio thread only. */
    private fun finishTake() {
        val chunks = take ?: emptyList()
        take = null
        takeSamples = 0
        recordResult?.complete(chunks)
        recordResult = null
    }

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        _error.value = null
        job = scope.launch(Dispatchers.Default) {
            val buffer = ShortArray(BUFFER_SIZE)
            var out: AudioOutput? = null
            try {
                out = AudioOutput(sampleRate).also { it.start() }
                while (isActive) {
                    val current = voices
                    for (i in 0 until BUFFER_SIZE) {
                        var sum = 0.0
                        for (v in current) if (!v.finished) sum += v.render()
                        buffer[i] = mix(sum)
                    }
                    out.write(buffer, BUFFER_SIZE)
                    if (recordRequested) {
                        val t = take ?: ArrayList<ShortArray>().also { take = it }
                        t += buffer.copyOf()
                        takeSamples += BUFFER_SIZE
                        if (takeSamples >= MAX_RECORD_SECONDS * sampleRate) recordRequested = false
                    } else if (take != null) {
                        finishTake()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = "Audio error: ${e.message ?: e::class.simpleName}"
            } finally {
                runCatching { out?.stop() }
                recordRequested = false
                finishTake()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        voices = voices.filterNot { it.removed }
    }

    companion object {
        private const val BUFFER_SIZE = 512
        /** Longest output recording; 5 minutes is ~26 MB of PCM, safe even on low-memory phones. */
        const val MAX_RECORD_SECONDS = 5 * 60

        /** Soft-clips the summed voices into 16-bit PCM. */
        internal fun mix(sum: Double): Short = (tanh(sum * 1.2) * Short.MAX_VALUE).toInt().toShort()
    }
}

/** Compact Freeverb-style reverb: parallel damped comb filters followed by series all-pass filters. */
private class Reverb(sampleRate: Int) {
    private val scale = sampleRate / 44_100.0
    private val combs = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491).map { Comb((it * scale).toInt()) }.toTypedArray()
    private val allPasses = intArrayOf(556, 441, 341).map { AllPass((it * scale).toInt()) }.toTypedArray()

    fun process(x: Double): Double {
        val input = x * 0.2
        var out = 0.0
        for (c in combs) out += c.process(input)
        for (a in allPasses) out = a.process(out)
        return out * 0.6
    }

    private class Comb(size: Int) {
        private val buf = FloatArray(size)
        private var idx = 0
        private var store = 0.0

        fun process(x: Double): Double {
            val y = buf[idx].toDouble()
            store = y * (1 - DAMP) + store * DAMP
            buf[idx] = (x + store * FEEDBACK).toFloat()
            if (++idx == buf.size) idx = 0
            return y
        }

        companion object {
            const val FEEDBACK = 0.84
            const val DAMP = 0.25
        }
    }

    private class AllPass(size: Int) {
        private val buf = FloatArray(size)
        private var idx = 0

        fun process(x: Double): Double {
            val b = buf[idx].toDouble()
            buf[idx] = (x + b * 0.5).toFloat()
            if (++idx == buf.size) idx = 0
            return b - x
        }
    }
}
