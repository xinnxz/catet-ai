package com.catetai.app.presentation.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catetai.app.domain.model.TranscriptSegment
import com.catetai.app.presentation.home.util.RecordingFormatters
import com.catetai.app.presentation.theme.Dimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Komponen kartu baris untuk menampilkan satu segmen ucapan asli (verbatim).
 *
 * Menampilkan:
 * 1. Badge stempel waktu awal dan akhir segmen dalam format `[mm:ss - mm:ss]`.
 * 2. Teks transkrip asli persis seperti yang diucapkan oleh pembicara.
 * 3. Tombol salin ke papan klip (clipboard) dengan animasi konfirmasi ceklis centang.
 */
@Composable
fun TranscriptSegmentItem(
    segment: TranscriptSegment,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Badge penanda waktu (Timestamp pill)
                Box(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(Dimens.RadiusPill)
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    val timeStart = RecordingFormatters.formatDuration(segment.startMs)
                    val timeEnd = RecordingFormatters.formatDuration(segment.endMs)
                    Text(
                        text = "$timeStart - $timeEnd",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                // Tombol salin teks segmen
                IconButton(
                    onClick = {
                        val textToCopy = "[${RecordingFormatters.formatDuration(segment.startMs)}] ${segment.text}"
                        clipboardManager.setText(AnnotatedString(textToCopy))
                        isCopied = true
                        coroutineScope.launch {
                            delay(1500)
                            isCopied = false
                        }
                    },
                    modifier = Modifier.height(28.dp).width(28.dp)
                ) {
                    if (isCopied) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Tersalin",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = "Salin",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Teks transkrip asli verbatim
            Text(
                text = segment.text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    lineHeight = 22.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
