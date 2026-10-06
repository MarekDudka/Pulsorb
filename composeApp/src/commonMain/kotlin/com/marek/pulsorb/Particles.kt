// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Marek Dudka

package com.marek.pulsorb

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Sparks emitted from a circle. The origin is the circle position in normalized plot coordinates,
 * the offset/velocity are in dp so particles look the same on any screen size.
 */
private class Particle(
    val nx: Float, val ny: Float,
    var ox: Float, var oy: Float,
    var vx: Float, var vy: Float,
    val color: Color,
    val size: Float,
    val maxLife: Float,
) {
    var life = maxLife
}

class ParticleSystem {
    private val particles = ArrayList<Particle>()

    /** Number of live particles. */
    val size: Int get() = particles.size

    /** Bumped every frame so the canvas that draws the particles is invalidated. */
    var frame by mutableLongStateOf(0L)
        private set

    fun burst(nx: Float, ny: Float, color: Color, startRadiusDp: Float, intensity: Float) {
        val count = (8 + 22 * intensity).toInt()
        repeat(count) {
            if (particles.size >= MAX_PARTICLES) particles.removeAt(0)
            val a = Random.nextFloat() * 2f * PI.toFloat()
            val speed = 90f + Random.nextFloat() * 260f * (0.5f + intensity)
            val dx = cos(a)
            val dy = sin(a)
            particles += Particle(
                nx = nx, ny = ny,
                ox = dx * startRadiusDp, oy = dy * startRadiusDp,
                vx = dx * speed, vy = dy * speed,
                color = lerp(color, Color.White, Random.nextFloat() * 0.6f),
                size = 1.5f + Random.nextFloat() * 3f,
                maxLife = 0.5f + Random.nextFloat() * 0.8f,
            )
        }
    }

    fun update(dt: Float) {
        if (particles.isEmpty()) return
        val drag = exp(-2.8f * dt)
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life -= dt
            if (p.life <= 0f) { it.remove(); continue }
            p.ox += p.vx * dt
            p.oy += p.vy * dt
            p.vx *= drag
            p.vy *= drag
        }
        frame++
    }

    /** [toPx] maps normalized plot coordinates to pixels. */
    fun draw(scope: DrawScope, toPx: (Float, Float) -> Offset) = with(scope) {
        frame // read so the draw block re-runs every frame
        val d = density
        for (p in particles) {
            val k = p.life / p.maxLife                  // 1 -> 0 over the lifetime
            val center = toPx(p.nx, p.ny) + Offset(p.ox * d, p.oy * d)
            val r = p.size * (0.4f + 0.6f * k) * d
            drawCircle(
                Brush.radialGradient(listOf(p.color.copy(alpha = 0.18f * k), Color.Transparent), center, r * 4f),
                radius = r * 4f,
                center = center,
            )
            drawCircle(p.color.copy(alpha = 0.7f * k), radius = r, center = center)
        }
    }

    companion object {
        const val MAX_PARTICLES = 800
    }
}

private fun lerp(a: Color, b: Color, t: Float) = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
)
