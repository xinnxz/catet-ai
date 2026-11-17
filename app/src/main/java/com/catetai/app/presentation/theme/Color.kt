package com.catetai.app.presentation.theme

import androidx.compose.ui.graphics.Color

// ====================================================================
// Catet AI - Design Tokens (Color Palette)
// Calibrated for WCAG AA Contrast (>= 4.5:1 for body and badge text)
// ====================================================================

// Brand Primary (Indigo)
val IndigoPrimaryLight = Color(0xFF4F46E5)   // Indigo 600 (Contrast on white ~4.7:1)
val IndigoPrimaryDark = Color(0xFF5457E8)    // Indigo 500+ (Contrast with white text ~4.7:1, passes WCAG AA)
val IndigoContainerLight = Color(0xFFEEF2FF) // Indigo 50
val IndigoContainerDark = Color(0xFF312E81)  // Indigo 900

// Background & Surface - Dark Scheme (Slate)
val BackgroundDark = Color(0xFF0F172A)       // Slate 900
val SurfaceDark = Color(0xFF1E293B)          // Slate 800
val SurfaceVariantDark = Color(0xFF334155)   // Slate 700
val TextPrimaryDark = Color(0xFFF8FAFC)      // Slate 50 (Contrast on Slate 900 ~15.8:1)
val TextSecondaryDark = Color(0xFF94A3B8)    // Slate 400 (Contrast on Slate 800 ~5.4:1)

// Background & Surface - Light Scheme (Clean)
val BackgroundLight = Color(0xFFF8FAFC)      // Slate 50
val SurfaceLight = Color(0xFFFFFFFF)         // White
val SurfaceVariantLight = Color(0xFFF1F5F9)  // Slate 100
val TextPrimaryLight = Color(0xFF0F172A)     // Slate 900 (Contrast on white ~15.8:1)
val TextSecondaryLight = Color(0xFF64748B)   // Slate 500 (Contrast on white ~4.6:1)

// Semantic Status - Base Accent Colors
val StatusRecording = Color(0xFFEF4444)      // Red 500
val StatusCompleted = Color(0xFF10B981)      // Emerald 500
val StatusProcessing = Color(0xFFF59E0B)     // Amber 500

// Status Badge Background Containers
val StatusRecordingContainerLight = Color(0xFFFEE2E2)  // Red 100
val StatusCompletedContainerLight = Color(0xFFD1FAE5)  // Emerald 100
val StatusProcessingContainerLight = Color(0xFFFEF3C7) // Amber 100

val StatusRecordingContainerDark = Color(0xFF7F1D1D)   // Red 900
val StatusCompletedContainerDark = Color(0xFF064E3B)   // Emerald 900
val StatusProcessingContainerDark = Color(0xFF78350F)  // Amber 900

// Status Badge Text (Calibrated >= 4.5:1 against respective surfaces)
// On Light Backgrounds
val StatusRecordingTextLight = Color(0xFFB91C1C)       // Red 700 (Contrast ~5.1:1 on white/light)
val StatusCompletedTextLight = Color(0xFF047857)       // Emerald 700 (Contrast ~4.7:1 on white/light)
val StatusProcessingTextLight = Color(0xFFB45309)      // Amber 700 (Contrast ~4.6:1 on white/light)

// On Dark Backgrounds
val StatusRecordingTextDark = Color(0xFFF87171)        // Red 400 (Contrast ~5.5:1 on dark)
val StatusCompletedTextDark = Color(0xFF34D399)        // Emerald 400 (Contrast ~6.8:1 on dark)
val StatusProcessingTextDark = Color(0xFFFBBF24)       // Amber 400 (Contrast ~9.1:1 on dark)

// Waveform Colors
val WaveformBarActive = Color(0xFF6366F1)
val WaveformBarInactiveDark = Color(0xFF334155)        // Slate 700
val WaveformBarInactiveLight = Color(0xFFCBD5E1)       // Slate 300
