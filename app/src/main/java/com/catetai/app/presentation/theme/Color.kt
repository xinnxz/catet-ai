package com.catetai.app.presentation.theme

import androidx.compose.ui.graphics.Color

// ====================================================================
// Catet AI - Design Tokens (Color Palette)
// Carefully calibrated for WCAG AA Contrast (>= 4.5:1 for body text)
// ====================================================================

// Brand Primary (Indigo)
val IndigoPrimaryLight = Color(0xFF4F46E5)   // Indigo 600
val IndigoPrimaryDark = Color(0xFF6366F1)    // Indigo 500
val IndigoContainerLight = Color(0xFFEEF2FF) // Indigo 50
val IndigoContainerDark = Color(0xFF312E81)  // Indigo 900

// Background & Surface - Dark Scheme (Slate)
val BackgroundDark = Color(0xFF0F172A)       // Slate 900
val SurfaceDark = Color(0xFF1E293B)          // Slate 800
val SurfaceVariantDark = Color(0xFF334155)   // Slate 700
val TextPrimaryDark = Color(0xFFF8FAFC)      // Slate 50 (Contrast ~15.8:1)
val TextSecondaryDark = Color(0xFF94A3B8)    // Slate 400 (Contrast ~5.4:1)

// Background & Surface - Light Scheme (Clean)
val BackgroundLight = Color(0xFFF8FAFC)      // Slate 50
val SurfaceLight = Color(0xFFFFFFFF)         // White
val SurfaceVariantLight = Color(0xFFF1F5F9)  // Slate 100
val TextPrimaryLight = Color(0xFF0F172A)     // Slate 900 (Contrast ~15.8:1)
val TextSecondaryLight = Color(0xFF64748B)   // Slate 500 (Contrast ~4.6:1)

// Semantic Recording & AI Status Colors
val StatusRecording = Color(0xFFEF4444)      // Red 500 (Active Mic / Recording)
val StatusRecordingContainer = Color(0xFFFEE2E2) // Red 100
val StatusCompleted = Color(0xFF10B981)      // Emerald 500 (Finished / Local Encrypted)
val StatusProcessing = Color(0xFFF59E0B)     // Amber 500 (VAD / Whisper Running)

// Waveform Colors
val WaveformBarActive = Color(0xFF6366F1)    // Indigo
val WaveformBarInactiveDark = Color(0xFF334155) // Slate 700
val WaveformBarInactiveLight = Color(0xFFCBD5E1) // Slate 300
