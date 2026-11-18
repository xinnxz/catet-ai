package com.catetai.app.presentation.detail.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Dialog untuk mengatur daftar kosakata kustom (Custom Vocabulary / Prompt Biasing).
 *
 * Sesuai arsitektur Whisper, memasukkan daftar kata kunci penting (nama orang, merek,
 * singkatan teknis, atau istilah medis/legal) sebagai prompt awal (initial prompt)
 * akan mengarahkan decoder model agar mengenali kata-kata khusus tersebut dengan akurat
 * daripada salah mengenali sebagai kata umum.
 */
@Composable
fun CustomVocabularyDialog(
    vocabularyInput: String,
    onInputChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Kosakata Kustom (Prompt Biasing)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Tuliskan istilah teknis, nama peserta rapat, singkatan, atau jargon yang sering diucapkan, dipisahkan dengan tanda koma.\n\nContoh: Catet AI, Jetpack Compose, Kotlin, Whisper, Standup Meeting",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = vocabularyInput,
                    onValueChange = onInputChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Daftar Istilah / Kosakata") },
                    placeholder = { Text("Kata 1, Kata 2, Istilah 3") },
                    minLines = 3,
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text("Simpan", fontWeight = FontWeight.Bold)
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
