package com.catetai.app.presentation.detail.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.catetai.app.domain.model.ModelInfo

/**
 * Dialog konfirmasi sebelum mengunduh model AI Whisper ke penyimpanan lokal.
 *
 * Memberi tahu pengguna mengenai:
 * 1. Ukuran berkas (~574 MB untuk Whisper Large v3 Turbo Q5).
 * 2. Manfaat privasi: setelah diunduh sekali, seluruh proses transkripsi berjalan 100% offline di perangkat.
 */
@Composable
fun ModelDownloadConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val model = ModelInfo.DEFAULT
    val sizeMb = model.sizeBytes / (1024 * 1024)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Unduh Model AI Transkripsi",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Untuk mentranskrip rekaman suara secara offline di perangkat, Catet AI memerlukan model AI Whisper Large v3 Turbo.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "• Nama Model: ${model.name}\n" +
                            "• Ukuran Unduhan: ~$sizeMb MB\n" +
                            "• Keamanan: Model disimpan lokal di perangkat. Tidak ada suara yang dikirim ke server luar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Unduh Sekarang", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        },
        modifier = modifier
    )
}
