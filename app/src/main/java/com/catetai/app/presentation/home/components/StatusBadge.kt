package com.catetai.app.presentation.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.catetai.app.domain.model.RecordingStatus
import com.catetai.app.presentation.theme.Dimens

/**
 * Badge status rekaman dengan warna semantik yang kontras (memenuhi standar WCAG AA >= 4.5:1).
 *
 * Setiap status memiliki warna latar dan teks yang berbeda agar pengguna
 * dapat dengan mudah membedakan kondisi setiap rekaman dalam daftar:
 * - **Biru** → Siap diproses (IMPORTED)
 * - **Kuning** → Sedang diproses (DECODING / TRANSCRIBING / SUMMARIZING)
 * - **Hijau** → Selesai (COMPLETED)
 * - **Merah** → Gagal (FAILED)
 * - **Oranye** → Terganggu / interrupted (INTERRUPTED)
 */
@Composable
fun StatusBadge(
    status: RecordingStatus,
    modifier: Modifier = Modifier,
    isDark: Boolean = isSystemInDarkTheme()
) {
    val (backgroundColor, textColor, label) = when (status) {
        RecordingStatus.IMPORTED -> if (isDark) {
            Triple(Color(0xFF1E3A5F), Color(0xFF93C5FD), "Siap Diproses")
        } else {
            Triple(Color(0xFFDBEAFE), Color(0xFF1D4ED8), "Siap Diproses")
        }
        RecordingStatus.DECODING -> if (isDark) {
            Triple(Color(0xFF78350F), Color(0xFFFDE68A), "Mempersiapkan...")
        } else {
            Triple(Color(0xFFFEF3C7), Color(0xFFB45309), "Mempersiapkan...")
        }
        RecordingStatus.TRANSCRIBING -> if (isDark) {
            Triple(Color(0xFF78350F), Color(0xFFFDE68A), "Mentranskrip...")
        } else {
            Triple(Color(0xFFFEF3C7), Color(0xFFB45309), "Mentranskrip...")
        }
        RecordingStatus.SUMMARIZING -> if (isDark) {
            Triple(Color(0xFF78350F), Color(0xFFFDE68A), "Merangkum...")
        } else {
            Triple(Color(0xFFFEF3C7), Color(0xFFB45309), "Merangkum...")
        }
        RecordingStatus.COMPLETED -> if (isDark) {
            Triple(Color(0xFF064E3B), Color(0xFF6EE7B7), "Selesai")
        } else {
            Triple(Color(0xFFD1FAE5), Color(0xFF047857), "Selesai")
        }
        RecordingStatus.FAILED -> if (isDark) {
            Triple(Color(0xFF881337), Color(0xFFFDA4AF), "Gagal")
        } else {
            Triple(Color(0xFFFFE4E6), Color(0xFFBE123C), "Gagal")
        }
        RecordingStatus.INTERRUPTED -> if (isDark) {
            Triple(Color(0xFF7C2D12), Color(0xFFFDBA74), "Terganggu")
        } else {
            Triple(Color(0xFFFFEDD5), Color(0xFFC2410C), "Terganggu")
        }
    }

    Box(
        modifier = modifier
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(Dimens.RadiusPill)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}
