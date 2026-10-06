// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Marek Dudka

package com.marek.pulsorb

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class ParticleSystemTest {
    @Test
    fun louderBeatsEmitMoreParticles() {
        val quiet = ParticleSystem().apply { burst(0.5f, 0.5f, Color.Blue, 40f, 0f) }
        val loud = ParticleSystem().apply { burst(0.5f, 0.5f, Color.Blue, 40f, 1f) }
        assertEquals(8, quiet.size)
        assertEquals(30, loud.size)
    }

    @Test
    fun particlesDisappearAfterTheirLifetime() {
        val p = ParticleSystem().apply { burst(0.5f, 0.5f, Color.Blue, 40f, 1f) }
        repeat(30) { p.update(0.05f) } // 1.5 s, longer than the 1.3 s maximum lifetime
        assertEquals(0, p.size)
    }

    @Test
    fun particleCountIsCapped() {
        val p = ParticleSystem()
        repeat(100) { p.burst(0.5f, 0.5f, Color.Blue, 40f, 1f) }
        assertEquals(ParticleSystem.MAX_PARTICLES, p.size)
    }
}
