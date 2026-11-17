package com.catetai.app.presentation.theme

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Memvalidasi bahwa pasangan warna badge status memenuhi standar aksesibilitas
 * WCAG 2.1 AA (rasio kontras minimal 4.5:1 untuk teks berukuran normal).
 *
 * Rumus luminansi relatif (relative luminance) mengikuti spesifikasi W3C:
 * https://www.w3.org/TR/WCAG21/#dfn-relative-luminance
 *
 * L = 0.2126 * R + 0.7152 * G + 0.0722 * B
 * di mana setiap komponen RGB dikonversi dari sRGB ke linear:
 *   jika C_sRGB <= 0.04045 → C_linear = C_sRGB / 12.92
 *   jika C_sRGB >  0.04045 → C_linear = ((C_sRGB + 0.055) / 1.055) ^ 2.4
 *
 * Rasio kontras = (L_lebih_terang + 0.05) / (L_lebih_gelap + 0.05)
 */
class WcagContrastTest {

    private fun srgbToLinear(component: Float): Double {
        val c = component.toDouble()
        return if (c <= 0.04045) c / 12.92
        else Math.pow((c + 0.055) / 1.055, 2.4)
    }

    private fun relativeLuminance(colorInt: Long): Double {
        val r = ((colorInt shr 16) and 0xFF) / 255f
        val g = ((colorInt shr 8) and 0xFF) / 255f
        val b = (colorInt and 0xFF) / 255f
        return 0.2126 * srgbToLinear(r) + 0.7152 * srgbToLinear(g) + 0.0722 * srgbToLinear(b)
    }

    private fun contrastRatio(foreground: Long, background: Long): Double {
        val lFg = relativeLuminance(foreground)
        val lBg = relativeLuminance(background)
        val lighter = maxOf(lFg, lBg)
        val darker = minOf(lFg, lBg)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun assertWcagAA(label: String, text: Long, background: Long) {
        val ratio = contrastRatio(text, background)
        assertTrue(
            "$label: rasio kontras %.2f:1 < 4.5:1 (WCAG AA gagal)".format(ratio),
            ratio >= 4.5
        )
    }

    // ===== Light Mode =====

    @Test
    fun `RECORDING badge light mode meets WCAG AA`() {
        // Red 700 teks di atas Red 100 latar
        assertWcagAA("RECORDING Light", 0xFFB91C1C, 0xFFFEE2E2)
    }

    @Test
    fun `STOPPED badge light mode meets WCAG AA`() {
        // Slate 600 teks di atas Slate 100 latar
        assertWcagAA("STOPPED Light", 0xFF475569, 0xFFF1F5F9)
    }

    @Test
    fun `PROCESSING badge light mode meets WCAG AA`() {
        // Amber 700 teks di atas Amber 100 latar
        assertWcagAA("PROCESSING Light", 0xFFB45309, 0xFFFEF3C7)
    }

    @Test
    fun `SUMMARIZED badge light mode meets WCAG AA`() {
        // Emerald 700 teks di atas Emerald 100 latar
        assertWcagAA("SUMMARIZED Light", 0xFF047857, 0xFFD1FAE5)
    }

    @Test
    fun `TRANSCRIBED badge light mode meets WCAG AA`() {
        // Teal 700 teks di atas Teal 100 latar
        assertWcagAA("TRANSCRIBED Light", 0xFF0F766E, 0xFFCCFBF1)
    }

    @Test
    fun `ERROR badge light mode meets WCAG AA`() {
        // Rose 700 teks di atas Rose 100 latar
        assertWcagAA("ERROR Light", 0xFFBE123C, 0xFFFFE4E6)
    }

    // ===== Dark Mode =====

    @Test
    fun `RECORDING badge dark mode meets WCAG AA`() {
        // Red 300 teks di atas Red 900 latar
        assertWcagAA("RECORDING Dark", 0xFFFCA5A5, 0xFF7F1D1D)
    }

    @Test
    fun `STOPPED badge dark mode meets WCAG AA`() {
        // Slate 300 teks di atas Slate 700 latar
        assertWcagAA("STOPPED Dark", 0xFFCBD5E1, 0xFF334155)
    }

    @Test
    fun `PROCESSING badge dark mode meets WCAG AA`() {
        // Amber 200 teks di atas Amber 900 latar
        assertWcagAA("PROCESSING Dark", 0xFFFDE68A, 0xFF78350F)
    }

    @Test
    fun `SUMMARIZED badge dark mode meets WCAG AA`() {
        // Emerald 300 teks di atas Emerald 900 latar
        assertWcagAA("SUMMARIZED Dark", 0xFF6EE7B7, 0xFF064E3B)
    }

    @Test
    fun `TRANSCRIBED badge dark mode meets WCAG AA`() {
        // Teal 300 teks di atas Teal 900 latar
        assertWcagAA("TRANSCRIBED Dark", 0xFF5EEAD4, 0xFF134E4A)
    }

    @Test
    fun `ERROR badge dark mode meets WCAG AA`() {
        // Rose 300 teks di atas Rose 900 latar
        assertWcagAA("ERROR Dark", 0xFFFDA4AF, 0xFF881337)
    }
}
