// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Marek Dudka

package com.marek.pulsorb

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.properties.ReadWriteProperty
import kotlin.random.Random
import kotlin.reflect.KProperty

internal const val MIN_BPM = 40.0
internal const val MAX_BPM = 240.0
/** Four full turns sweep the whole 60 Hz – 18 kHz range (logarithmic, ~2 octaves per turn). */
private const val MAX_ANGLE = 1440f
internal const val MAX_CIRCLES = 12
private val MARGIN = 32.dp
private val RADIUS = 40.dp
private val PALETTE = listOf(
    Color(0xFF3FA9F5), Color(0xFF4CD964), Color(0xFFC86BFA), Color(0xFFFFB020),
    Color(0xFFFF5A7A), Color(0xFF2ED3C6), Color(0xFFB4E04A), Color(0xFFFF7F3F),
)

/** Circle height that gives [bpm]. */
internal fun yForBpm(bpm: Double): Float = ((bpm - MIN_BPM) / (MAX_BPM - MIN_BPM)).toFloat()

internal fun bpmForY(y: Float): Double = MIN_BPM + y * (MAX_BPM - MIN_BPM)

internal fun frequencyForAngle(angle: Float): Double =
    MIN_FREQUENCY * (MAX_FREQUENCY / MIN_FREQUENCY).pow((angle / MAX_ANGLE).toDouble())

internal fun angleFor(frequency: Double): Float =
    (MAX_ANGLE * ln(frequency / MIN_FREQUENCY) / ln(MAX_FREQUENCY / MIN_FREQUENCY)).toFloat()

/**
 * UI state of one circle. Position uses origin (0,0) bottom-left, 0..1 on both axes.
 * Every sound-related change is pushed to the [voice] immediately, so the audio thread hears it
 * right away – even for a muted circle that produces no beats.
 */
class CircleState(
    val id: Int,
    val voice: Voice,
    val color: Color,
    x: Float,
    y: Float,
    angle: Float = angleFor(voice.sound.defaultFrequency),
    muted: Boolean = false,
    echo: Float = 0f,
    reverb: Float = 0.15f,
) {
    var x by synced(x)
    var y by synced(y)
    var angle by synced(angle)                // degrees, clockwise positive
    var muted by synced(muted)
    var echo by synced(echo)                  // 0..1
    var reverb by synced(reverb)              // 0..1
    var recording by mutableStateOf(false)
    var hasSample by mutableStateOf(false)
    var recorder: SampleRecorder? = null
    val pulse = Animatable(0f)
    val isSample get() = voice.sound == DrumSound.SAMPLE

    val label get() = "${voice.sound.label} $id"
    val bpm get() = bpmForY(y)                                           // up    -> faster
    val volume get() = x.toDouble()                                      // right -> louder
    val frequency get() = frequencyForAngle(angle)                       // turn  -> higher pitch

    fun moveBy(dx: Float, dy: Float) {
        x = (x + dx).coerceIn(0f, 1f)
        y = (y + dy).coerceIn(0f, 1f)
    }

    fun rotateBy(deg: Float) {
        angle = (angle + deg).coerceIn(0f, MAX_ANGLE)
    }

    init {
        syncVoice()
    }

    private fun syncVoice() {
        voice.bpm = bpm
        voice.volume = volume
        voice.frequency = frequency
        voice.muted = muted
        voice.echo = echo.toDouble()
        voice.reverb = reverb.toDouble()
    }

    /** Compose state that also updates the audio voice whenever it is set. */
    private fun <T> synced(initial: T) = object : ReadWriteProperty<CircleState, T> {
        private val state = mutableStateOf(initial)
        override fun getValue(thisRef: CircleState, property: KProperty<*>): T = state.value
        override fun setValue(thisRef: CircleState, property: KProperty<*>, value: T) {
            state.value = value
            thisRef.syncVoice()
        }
    }
}

@Composable
fun App() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        val engine = remember { SoundEngine() }
        val scope = rememberCoroutineScope()
        var playing by remember { mutableStateOf(false) }   // app starts silent
        var visualFx by remember { mutableStateOf(true) }
        var showAbout by remember { mutableStateOf(false) }
        var menuOpen by remember { mutableStateOf(false) }      // panels start folded

        val circles = remember { mutableStateListOf<CircleState>() }
        val particles = remember { ParticleSystem() }
        LaunchedEffect(Unit) {
            var last = withFrameNanos { it }
            while (true) withFrameNanos { now ->
                particles.update(((now - last) / 1e9f).coerceAtMost(0.05f))
                last = now
            }
        }
        var nextId by remember { mutableStateOf(1) }

        var message by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(message) {
            if (message != null) { delay(5000); message = null }
        }

        val micPermission = rememberMicPermission()

        fun startRecording(c: CircleState) {
            if (c.recording) return
            micPermission.request { granted ->
                if (!granted) {
                    message = "Microphone permission denied"
                    return@request
                }
                val recorder = SampleRecorder()
                c.recorder = recorder
                c.recording = true
                scope.launch {
                    val result = runCatching { recorder.record() }
                    c.recording = false
                    c.recorder = null
                    result
                        .onSuccess { sample ->
                            if (sample == null) message = "No sound detected – try again"
                            else { c.voice.sample = sample; c.hasSample = true }
                        }
                        .onFailure { message = "Microphone error: ${it.message}" }
                }
            }
        }

        fun stopRecording(c: CircleState) {
            c.recorder?.requestStop()
        }

        /** Adds a circle at a random spot; a new Sample circle starts recording right away. */
        fun addCircle(sound: DrumSound) {
            if (circles.size >= MAX_CIRCLES) return
            val id = nextId++
            val x = Random.nextFloat() * 0.6f + 0.2f
            val y = Random.nextFloat() * 0.6f + 0.2f
            val voice = engine.addVoice(sound) { bpm = bpmForY(y); volume = x.toDouble() }
            val circle = CircleState(id, voice, PALETTE[(id - 1) % PALETTE.size], x, y)
            circles += circle
            if (sound == DrumSound.SAMPLE) startRecording(circle)
        }

        /** Replaces all circles with the preset's. Sample circles come back empty (audio is never saved). */
        fun loadPreset(preset: Preset) {
            circles.toList().forEach { c ->
                stopRecording(c)
                circles.remove(c)
                engine.removeVoice(c.voice)
            }
            nextId = 1
            for (p in preset.sanitized().circles) {
                val sound = DrumSound.valueOf(p.sound)
                val id = nextId++
                val y = yForBpm(p.bpm)
                // Configure the voice fully before the audio thread sees it, so the groove starts in time.
                val voice = engine.addVoice(sound) {
                    bpm = p.bpm; volume = p.volume.toDouble(); frequency = p.frequency
                    muted = p.muted; echo = p.echo.toDouble(); reverb = p.reverb.toDouble()
                    delayStart(p.startDelay)
                }
                circles += CircleState(
                    id, voice, PALETTE[(id - 1) % PALETTE.size], p.volume, y,
                    angle = angleFor(p.frequency), muted = p.muted, echo = p.echo, reverb = p.reverb,
                )
            }
        }

        /** The current circles as a preset, including their timing relative to each other. */
        fun currentPreset(name: String) = Preset(
            name = name,
            circles = circles.map { c ->
                PresetCircle(
                    sound = c.voice.sound.name, bpm = c.bpm, volume = c.x, frequency = c.frequency,
                    echo = c.echo, reverb = c.reverb, muted = c.muted, startDelay = c.voice.secondsUntilNextHit,
                )
            },
        )

        fun removeCircle(c: CircleState) {
            stopRecording(c)
            circles.remove(c)
            engine.removeVoice(c.voice)
        }

        LaunchedEffect(Unit) { loadPreset(BUILT_IN_PRESETS.first()) }

        // Presets dialog: saved presets are read when it opens; file work runs off the UI thread.
        var showPresets by remember { mutableStateOf(false) }
        var savedPresets by remember { mutableStateOf(emptyList<SavedPreset>()) }
        suspend fun refreshSaved() {
            savedPresets = withContext(Dispatchers.Default) { runCatching { PresetStore.saved() }.getOrDefault(emptyList()) }
        }
        LaunchedEffect(showPresets) { if (showPresets) refreshSaved() }

        // Recording of the mixed output to a WAV file.
        var recordingOutput by remember { mutableStateOf(false) }
        var recordSeconds by remember { mutableStateOf(0) }
        LaunchedEffect(recordingOutput) {
            recordSeconds = 0
            while (recordingOutput) { delay(1000); recordSeconds++ }
        }

        fun startOutputRecording() {
            val take = engine.startRecording()
            recordingOutput = true
            scope.launch {
                val chunks = take.await()
                recordingOutput = false
                if (chunks.isEmpty()) return@launch
                message = runCatching { "Saved: ${saveRecording(encodeWav(chunks, engine.sampleRate))}" }
                    .getOrElse { "Saving failed: ${it.message}" }
            }
        }

        fun toggleOutputRecording() {
            if (recordingOutput) engine.stopRecording() else startOutputRecording()
        }

        // An audio device failure stops playback and is shown instead of crashing.
        val engineError by engine.error.collectAsState()
        LaunchedEffect(engineError) {
            engineError?.let { message = it; playing = false }
        }

        // Going to the background pauses playback (saving any recording in progress) and
        // always stops the microphone. The engine is stopped directly: Compose doesn't
        // recompose a stopped app, so waiting for `playing` to take effect would keep it playing.
        var pausedInBackground by remember { mutableStateOf(false) }
        LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
            circles.forEach { stopRecording(it) }
            if (pausesInBackground && playing) {
                engine.stopRecording()
                engine.stop()
                playing = false
                pausedInBackground = true
            }
        }
        LifecycleEventEffect(Lifecycle.Event.ON_START) {
            if (pausedInBackground) {
                pausedInBackground = false
                message = "Paused while in the background – press Play to continue"
            }
        }

        DisposableEffect(playing) {
            if (playing) engine.start(scope)
            onDispose { engine.stop() }
        }

        circles.forEach { c ->
            key(c.id) {
                // Pulse animation on every hit.
                val beat by c.voice.beat.collectAsState()
                LaunchedEffect(beat) {
                    if (beat == 0L) return@LaunchedEffect
                    if (!visualFx) return@LaunchedEffect
                    particles.burst(c.x, c.y, c.color, RADIUS.value, c.volume.toFloat())
                    // Soft rise and slow fall instead of a hard flash (photosensitivity-friendly).
                    c.pulse.animateTo(1f, tween(durationMillis = 70))
                    c.pulse.animateTo(0f, tween(durationMillis = 450, easing = FastOutSlowInEasing))
                }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF05060F))
                .pointerInput(circles) { handleGestures(circles) }
        ) {
            GlowBackground(circles)
            CirclesCanvas(circles, particles)

            Hud(
                circles,
                onRemove = ::removeCircle,
                onRecord = { if (it.recording) stopRecording(it) else startRecording(it) },
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp))

            Column(
                Modifier.align(Alignment.TopEnd).padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 40.dp),
                horizontalAlignment = Alignment.End,
            ) {
                // Always visible: transport and the menu toggle.
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (recordingOutput && !menuOpen) {
                        RecordButton(recordingOutput, recordSeconds, playing, ::toggleOutputRecording)
                    }
                    Button(onClick = {
                        if (playing) engine.stopRecording()
                        playing = !playing
                    }) { Text(if (playing) "Stop" else "Play") }
                    OutlinedButton(
                        onClick = { menuOpen = !menuOpen },
                        contentPadding = PaddingValues(horizontal = 14.dp),
                    ) { Text(if (menuOpen) "✕" else "☰", fontSize = 16.sp) }
                }

                // Collapsible, scrollable menu so every button stays reachable on small screens.
                AnimatedVisibility(menuOpen) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.35f),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Column(
                            Modifier.verticalScroll(rememberScrollState()).padding(8.dp),
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            RecordButton(recordingOutput, recordSeconds, playing, ::toggleOutputRecording)
                            OutlinedButton(
                                onClick = { visualFx = !visualFx },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            ) { Text(if (visualFx) "Visual FX: on" else "Visual FX: off", fontSize = 13.sp) }
                            for (sound in DrumSound.entries) {
                                OutlinedButton(
                                    onClick = { addCircle(sound) },
                                    enabled = circles.size < MAX_CIRCLES,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                ) { Text("+ ${sound.label.lowercase().replaceFirstChar { it.uppercase() }}", fontSize = 13.sp) }
                            }
                            OutlinedButton(
                                onClick = { showPresets = true },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            ) { Text("Presets", fontSize = 13.sp) }
                            TextButton(onClick = { showAbout = true }) { Text("About", fontSize = 13.sp) }
                        }
                    }
                }
            }

            message?.let {
                Text(
                    it,
                    color = Color(0xFFFFE082),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
                )
            }

            if (showAbout) AboutDialog(onDismiss = { showAbout = false })

            if (showPresets) {
                PresetsDialog(
                    builtIn = BUILT_IN_PRESETS,
                    saved = savedPresets,
                    onLoad = { preset ->
                        loadPreset(preset)
                        showPresets = false
                        message = "Loaded “${preset.name}”"
                    },
                    onSave = { name ->
                        val preset = currentPreset(name)
                        scope.launch {
                            message = runCatching {
                                withContext(Dispatchers.Default) { PresetStore.save(preset) }
                                "Saved preset “${preset.sanitized().name}”"
                            }.getOrElse { "Saving preset failed: ${it.message}" }
                            refreshSaved()
                        }
                    },
                    onDelete = { saved ->
                        scope.launch {
                            withContext(Dispatchers.Default) { runCatching { PresetStore.delete(saved.fileName) } }
                            refreshSaved()
                        }
                    },
                    onDismiss = { showPresets = false },
                )
            }

            Text(
                "Drag: move  •  Second finger / wheel: rotate  •  Tap: mute",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
            )
        }
    }
}

@Composable
private fun RecordButton(recording: Boolean, seconds: Int, playing: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = playing,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (recording) Color(0xFFD32F2F) else Color(0xFF5A1F2A),
            contentColor = Color.White,
        ),
    ) {
        Text(if (recording) "■ ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}" else "● Record")
    }
}

/** Maps between normalized bottom-left coordinates and pixels inside the plot area. */
private class Plot(size: IntSize, density: Density) {
    val margin = with(density) { MARGIN.toPx() }
    val w = size.width - 2 * margin
    val h = size.height - 2 * margin
    fun toPx(nx: Float, ny: Float) = Offset(margin + nx * w, margin + (1f - ny) * h)
}

/**
 * Multi-touch handling: every finger is assigned to the circle it lands on, so all
 * circles can be dragged at the same time. A second finger on (or anywhere near) a circle
 * that already has one finger twists it. A tap toggles mute; the mouse wheel rotates.
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.handleGestures(
    circles: List<CircleState>,
) {
    val radius = RADIUS.toPx()
    awaitPointerEventScope {
        val owner = mutableMapOf<PointerId, CircleState>()
        val downAt = mutableMapOf<PointerId, Offset>()
        val dragged = mutableSetOf<PointerId>()

        fun circleAt(pos: Offset): CircleState? {
            val plot = Plot(size, this)
            // Topmost (last drawn) first.
            return circles.lastOrNull { (plot.toPx(it.x, it.y) - pos).getDistance() <= radius * 1.6f }
        }

        while (true) {
            val event = awaitPointerEvent()
            val plot = Plot(size, this)

            if (event.type == PointerEventType.Scroll) {
                val change = event.changes.first()
                circleAt(change.position)?.rotateBy(change.scrollDelta.y * 15f)
                event.changes.forEach { it.consume() }
                continue
            }

            for (c in event.changes) {
                // Ignore touches already handled by buttons on top (HUD, add buttons).
                if (!c.changedToDown() || c.isConsumed) continue
                val hit = circleAt(c.position)
                    // Second finger off-circle: pair it with the nearest circle held by one finger.
                    ?: owner.values.groupingBy { it }.eachCount().filterValues { it == 1 }.keys
                        .minByOrNull { (plot.toPx(it.x, it.y) - c.position).getDistance() }
                if (hit != null) {
                    owner[c.id] = hit
                    downAt[c.id] = c.position
                }
            }

            for (circle in circles) {
                val ptrs = event.changes.filter { owner[it.id] === circle && it.pressed && it.previousPressed }
                if (ptrs.isEmpty()) continue
                ptrs.forEach { p ->
                    if ((p.position - (downAt[p.id] ?: p.position)).getDistance() > viewConfiguration.touchSlop) {
                        dragged += p.id
                    }
                }
                if (ptrs.none { it.id in dragged }) continue

                if (ptrs.size == 1) {
                    val d = ptrs[0].position - ptrs[0].previousPosition
                    circle.moveBy(d.x / plot.w, -d.y / plot.h)
                } else {
                    // Twist: change of angle between the first two fingers.
                    val a = ptrs[0]
                    val b = ptrs[1]
                    val now = angleDeg(b.position - a.position)
                    val before = angleDeg(b.previousPosition - a.previousPosition)
                    var delta = now - before
                    if (delta > 180f) delta -= 360f
                    if (delta < -180f) delta += 360f
                    circle.rotateBy(delta)
                }
                ptrs.forEach { it.consume() }
            }

            for (c in event.changes) {
                if (!c.changedToUp()) continue
                val circle = owner.remove(c.id)
                if (circle != null && c.id !in dragged && !c.isConsumed && owner.values.none { it === circle }) {
                    circle.muted = !circle.muted
                }
                downAt.remove(c.id)
                dragged.remove(c.id)
            }
        }
    }
}

private fun angleDeg(v: Offset) = (atan2(v.y, v.x) * 180.0 / PI).toFloat()

@Composable
private fun CirclesCanvas(circles: List<CircleState>, particles: ParticleSystem) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)

    Canvas(Modifier.fillMaxSize()) {
        val plot = Plot(IntSize(size.width.toInt(), size.height.toInt()), this)
        particles.draw(this, plot::toPx)

        for (c in circles) {
            val pulse = c.pulse.value
            val center = plot.toPx(c.x, c.y)
            val baseRadius = RADIUS.toPx()
            val radius = baseRadius * (1f + 0.08f * pulse)
            // Brighter as the pitch goes up.
            val t = (c.angle / MAX_ANGLE).coerceIn(0f, 1f)
            val color = lerpColor(c.color, Color.White, t * 0.6f).copy(alpha = if (c.muted) 0.3f else 1f)

            drawCircle(
                color.copy(alpha = 0.2f * pulse),
                radius = baseRadius * (1f + 1.2f * (1f - pulse)),
                center = center,
                style = Stroke(width = 3.dp.toPx()),
            )
            // Soft neon halo + glossy core.
            drawCircle(
                Brush.radialGradient(
                    0f to color.copy(alpha = color.alpha * (0.45f + 0.12f * pulse)),
                    0.45f to color.copy(alpha = color.alpha * 0.18f),
                    1f to Color.Transparent,
                    center = center,
                    radius = radius * (2.4f + 0.3f * pulse),
                ),
                radius = radius * (2.4f + 0.3f * pulse),
                center = center,
            )
            drawCircle(
                Brush.radialGradient(
                    0f to lerpColor(color, Color.White, 0.75f).copy(alpha = color.alpha),
                    0.55f to color,
                    1f to lerpColor(color, Color.Black, 0.35f).copy(alpha = color.alpha),
                    center = center - Offset(radius * 0.3f, radius * 0.3f),
                    radius = radius * 1.4f,
                ),
                radius = radius,
                center = center,
            )
            drawCircle(Color.White.copy(alpha = color.alpha * 0.5f), radius = radius, center = center, style = Stroke(1.5.dp.toPx()))

            // Spokes make the rotation visible.
            rotate(c.angle, pivot = center) {
                for (i in 0 until 4) {
                    rotate(i * 90f, pivot = center) {
                        drawLine(
                            Color.White.copy(alpha = if (i == 0) 0.95f else 0.4f),
                            start = center,
                            end = center + Offset(0f, -radius * 0.85f),
                            strokeWidth = (if (i == 0) 4 else 2).dp.toPx(),
                        )
                    }
                }
            }

            if (c.recording) {
                drawCircle(Color(0xFFFF3B30), radius = radius * 1.15f, center = center, style = Stroke(width = 4.dp.toPx()))
            }
            val text = when {
                c.recording -> "● REC"
                c.isSample && !c.hasSample -> "${c.label} (empty)"
                else -> c.label
            }
            val label = textMeasurer.measure(text, labelStyle.copy(color = if (c.recording) Color(0xFFFF3B30) else Color.White))
            drawText(label, topLeft = center + Offset(-label.size.width / 2f, radius + 6.dp.toPx()))
        }
    }
}

/** Animated dark backdrop: drifting aurora blobs, twinkling stars and light from every circle that flashes on its beat. */
@Composable
private fun GlowBackground(circles: List<CircleState>) {
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time = (it - start) / 1e9f }
    }
    val stars = remember {
        val rnd = Random(42)
        List(90) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat() * 1.6f + 0.4f, rnd.nextFloat() * 6.28f) }
    }
    val aurora = remember {
        listOf(Color(0xFF6A2CFF), Color(0xFF00C2FF), Color(0xFFFF2E97), Color(0xFF00E6A0))
    }

    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val big = maxOf(w, h)

        drawRect(Brush.verticalGradient(listOf(Color(0xFF140A2E), Color(0xFF070B1E), Color(0xFF02030A))))

        // Slowly drifting aurora blobs.
        aurora.forEachIndexed { i, color ->
            val p = i * 1.7f
            val center = Offset(
                w * (0.5f + 0.38f * sin(time * 0.07f * (i + 1) + p)),
                h * (0.5f + 0.38f * cos(time * 0.05f * (i + 2) + p * 1.3f)),
            )
            val r = big * (0.45f + 0.08f * sin(time * 0.3f + p))
            drawCircle(
                Brush.radialGradient(listOf(color.copy(alpha = 0.22f), Color.Transparent), center, r),
                radius = r,
                center = center,
            )
        }

        // Twinkling stars.
        for (s in stars) {
            val a = 0.25f + 0.55f * (0.5f + 0.5f * sin(time * s[2] + s[3]))
            drawCircle(Color.White.copy(alpha = a), radius = s[2].dp.toPx() * 0.8f, center = Offset(s[0] * w, s[1] * h))
        }

        // Every circle lights up the scene around it, flashing with its beat.
        val plot = Plot(IntSize(w.toInt(), h.toInt()), this)
        for (c in circles) {
            if (c.muted) continue
            val pulse = c.pulse.value
            val center = plot.toPx(c.x, c.y)
            val r = big * (0.22f + 0.03f * pulse) * (0.6f + 0.6f * c.x)
            drawCircle(
                Brush.radialGradient(listOf(c.color.copy(alpha = 0.08f + 0.05f * pulse), Color.Transparent), center, r),
                radius = r,
                center = center,
                blendMode = BlendMode.Screen,
            )
        }

        // Vignette.
        drawRect(
            Brush.radialGradient(
                0.55f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.6f),
                center = Offset(w / 2, h / 2),
                radius = big * 0.75f,
            )
        )
    }
}

@Composable
private fun Hud(
    circles: List<CircleState>,
    onRemove: (CircleState) -> Unit,
    onRecord: (CircleState) -> Unit,
    modifier: Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(color = Color.Black.copy(alpha = 0.45f), shape = MaterialTheme.shapes.medium, modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            // Header doubles as the show/hide toggle for the circle list.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = 2.dp),
            ) {
                Text(if (expanded) "▾" else "▸", color = Color.White, fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Text(AppInfo.NAME, style = MaterialTheme.typography.titleMedium, color = Color.White)
                if (!expanded) {
                    Text("  (${circles.size})", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                }
            }
            if (!expanded) return@Column

            Spacer(Modifier.height(6.dp))
            val mono = FontFamily.Monospace
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                Text("         x,y       BPM  vol      Hz", fontFamily = mono, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                for (c in circles) {
                    key(c.id) {
                        val color = c.color.copy(alpha = if (c.muted) 0.4f else 1f)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val line = "${c.label.padEnd(8)} ${c.x.fmt()},${c.y.fmt()}  ${c.bpm.roundToInt().toString().padStart(3)}  " +
                                "${(c.volume * 100).roundToInt().toString().padStart(3)}%  ${formatHz(c.frequency).padStart(6)}"
                            Text(line, fontFamily = mono, color = color, fontSize = 12.sp)
                            if (c.isSample) {
                                SmallIconText(if (c.recording) "■" else "●", Color(0xFFFF3B30)) { onRecord(c) }
                            }
                            SmallIconText("✕", Color.White.copy(alpha = 0.7f)) { onRemove(c) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FxSlider("Echo", c.echo, color) { c.echo = it }
                            Spacer(Modifier.width(12.dp))
                            FxSlider("Reverb", c.reverb, color) { c.reverb = it }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallIconText(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text,
        color = color,
        fontSize = 14.sp,
        modifier = Modifier
            .padding(start = 8.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun FxSlider(label: String, value: Float, color: Color, onChange: (Float) -> Unit) {
    Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, modifier = Modifier.width(44.dp))
    Slider(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.width(110.dp).height(24.dp),
        colors = SliderDefaults.colors(
            thumbColor = color,
            activeTrackColor = color,
            inactiveTrackColor = Color.White.copy(alpha = 0.2f),
        ),
    )
}

internal fun formatHz(f: Double): String =
    if (f >= 1000) "${(f / 100).roundToInt() / 10.0}k" else "${f.roundToInt()}"

private fun Float.fmt(): String {
    val v = (this * 100).roundToInt()
    return "${v / 100}.${(v % 100).toString().padStart(2, '0')}"
}

private fun lerpColor(a: Color, b: Color, t: Float) = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
)
