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
 */
@Composable
fun StatusBadge(
    status: RecordingStatus,
    modifier: Modifier = Modifier,
    isDark: Boolean = isSystemInDarkTheme()
) {
    val (backgroundColor, textColor, label) = when (status) {
        RecordingStatus.RECORDING -> if (isDark) {
            Triple(Color(0xFF7F1D1D), Color(0xFFFCA5A5), "Merekam")
        } else {
            Triple(Color(0xFFFEE2E2), Color(0xFFB91C1C), "Merekam")
        }
        RecordingStatus.STOPPED -> if (isDark) {
            Triple(Color(0xFF334155), Color(0xFFCBD5E1), "Tersimpan")
        } else {
            Triple(Color(0xFFF1F5F9), Color(0xFF475569), "Tersimpan")
        }
        RecordingStatus.TRUNCATED -> if (isDark) {
            Triple(Color(0xFF7C2D12), Color(0xFFFDBA74), "Dipulihkan")
        } else {
            Triple(Color(0xFFFFEDD5), Color(0xFFC2410C), "Dipulihkan")
        }
        RecordingStatus.PROCESSING -> if (isDark) {
            Triple(Color(0xFF78350F), Color(0xFFFDE68A), "Memproses")
        } else {
            Triple(Color(0xFFFEF3C7), Color(0xFFB45309), "Memproses")
        }
        RecordingStatus.TRANSCRIBED -> if (isDark) {
            Triple(Color(0xFF134E4A), Color(0xFF5EEAD4), "Ditranskrip")
        } else {
            Triple(Color(0xFFCCFBF1), Color(0xFF0F766E), "Ditranskrip")
        }
        RecordingStatus.SUMMARIZED -> if (isDark) {
            Triple(Color(0xFF064E3B), Color(0xFF6EE7B7), "Notulensi Siap")
        } else {
            Triple(Color(0xFFD1FAE5), Color(0xFF047857), "Notulensi Siap")
        }
        RecordingStatus.ERROR -> if (isDark) {
            Triple(Color(0xFF881337), Color(0xFFFDA4AF), "Gagal")
        } else {
            Triple(Color(0xFFFFE4E6), Color(0xFFBE123C), "Gagal")
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
