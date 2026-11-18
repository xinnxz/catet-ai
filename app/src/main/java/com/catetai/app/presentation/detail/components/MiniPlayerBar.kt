package com.catetai.app.presentation.detail.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catetai.app.engine.player.PlayerState
import com.catetai.app.presentation.home.util.RecordingFormatters

private val PauseIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Pause",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        pathData = listOf(
            PathNode.MoveTo(6f, 19f),
            PathNode.HorizontalTo(10f),
            PathNode.VerticalTo(5f),
            PathNode.HorizontalTo(6f),
            PathNode.VerticalTo(19f),
            PathNode.Close,
            PathNode.MoveTo(14f, 5f),
            PathNode.VerticalTo(19f),
            PathNode.HorizontalTo(18f),
            PathNode.VerticalTo(5f),
            PathNode.HorizontalTo(14f),
            PathNode.Close
        ),
        fill = SolidColor(Color.White)
    ).build()
}

/**
 * MiniPlayerBar — Pemutar audio mengambang di bagian bawah DetailScreen.
 *
 * Komponen ini hanya tampil saat audio sudah siap diputar ([PlayerState.isReady] = true).
 * Menggunakan animasi slide-in dari bawah sehingga muncul secara mulus.
 *
 * # Fitur
 * 1. **Tombol Play/Pause** — Toggle pemutaran satu klik.
 * 2. **Seek Bar (Slider)** — Geser untuk lompat ke posisi mana saja dalam audio.
 * 3. **Penanda Waktu** — Tampilkan posisi saat ini / total durasi (`mm:ss / mm:ss`).
 * 4. **Kontrol Kecepatan** — Tombol teks untuk memilih 0.5x, 1x, 1.5x, 2x.
 *
 * @param playerState    State terkini dari [AudioPlayerManager].
 * @param onTogglePlay   Callback saat tombol play/pause ditekan.
 * @param onSeek         Callback saat posisi seek bar dilepas, memberikan nilai posisi baru (ms).
 * @param onSetSpeed     Callback saat kecepatan diubah.
 * @param modifier       Modifier standar Compose.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniPlayerBar(
    playerState: PlayerState,
    onTogglePlay: () -> Unit,
    onSeek: (Int) -> Unit,
    onSetSpeed: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = playerState.isReady,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it })
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .shadow(elevation = 8.dp, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {

                // ── Baris atas: Tombol Play/Pause + Info waktu + Kontrol kecepatan ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tombol Play / Pause
                    FilledIconButton(
                        onClick = onTogglePlay,
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = if (playerState.isPlaying) PauseIcon else Icons.Default.PlayArrow,
                            contentDescription = if (playerState.isPlaying) "Jeda" else "Putar",
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Penanda waktu: mm:ss / mm:ss
                    Text(
                        text = "${RecordingFormatters.formatDuration(playerState.currentPositionMs.toLong())} / " +
                                RecordingFormatters.formatDuration(playerState.durationMs.toLong()),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )

                    // Kontrol kecepatan pemutaran
                    SpeedSelector(
                        currentSpeed = playerState.playbackSpeed,
                        onSelectSpeed = onSetSpeed
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // ── Seek Bar (Slider) ──────────────────────────────────────────────
                val sliderValue = if (playerState.durationMs > 0) {
                    playerState.currentPositionMs.toFloat() / playerState.durationMs.toFloat()
                } else {
                    0f
                }

                Slider(
                    value = sliderValue,
                    onValueChange = { fraction ->
                        // Konversi nilai 0.0–1.0 ke milidetik aktual
                        val newPositionMs = (fraction * playerState.durationMs).toInt()
                        onSeek(newPositionMs)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    // Track lebih tipis agar tampilan lebih clean
                )
            }
        }
    }
}

/**
 * Komponen pemilih kecepatan pemutaran yang ringkas.
 *
 * Menampilkan baris tombol teks "0.5x", "1x", "1.5x", "2x".
 * Kecepatan yang aktif ditampilkan dengan warna Primary dan font Bold.
 */
@Composable
private fun SpeedSelector(
    currentSpeed: Float,
    onSelectSpeed: (Float) -> Unit
) {
    val speeds = listOf(0.5f, 1.0f, 1.5f, 2.0f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        speeds.forEach { speed ->
            val isSelected = currentSpeed == speed
            TextButton(
                onClick = { onSelectSpeed(speed) },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp)
            ) {
                Text(
                    text = if (speed == 1.0f) "1×" else "${speed}×",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
