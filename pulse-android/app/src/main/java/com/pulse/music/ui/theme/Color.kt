package com.pulse.music.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Brand
val PulseRed = Color(0xFFFF3B4E)
val PulseCoral = Color(0xFFFF6B5E)
val PulseRose = Color(0xFFD81B60)

// Layered dark surfaces (slightly cool so the red accent glows against them)
val DarkBackground = Color(0xFF09090C)
val SurfaceDark = Color(0xFF121217)
val SurfaceVariantDark = Color(0xFF1B1B22)
val SurfaceHigh = Color(0xFF24242D)
val OutlineSoft = Color(0x14FFFFFF)

val TextPrimary = Color(0xFFF5F5F7)
val TextSecondary = Color(0xFFA1A1AA)
val TextTertiary = Color(0xFF6B6B76)
val ErrorRed = Color(0xFFFF5A5F)

/** Signature brand gradient used on primary buttons, active states and highlights. */
val PulseGradient: Brush
    get() = Brush.linearGradient(listOf(PulseRed, PulseCoral))

val PulseGradientDeep: Brush
    get() = Brush.linearGradient(listOf(PulseRose, PulseRed))
