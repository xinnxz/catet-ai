package com.catetai.app.presentation.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.catetai.app.domain.model.Recording
import com.catetai.app.domain.model.RecordingStatus
import com.catetai.app.presentation.detail.components.CustomVocabularyDialog
import com.catetai.app.presentation.detail.components.ModelDownloadConfirmDialog
import com.catetai.app.presentation.detail.components.TranscriptSegmentItem
import com.catetai.app.presentation.home.components.StatusBadge
import com.catetai.app.presentation.home.util.RecordingFormatters
import com.catetai.app.presentation.theme.Dimens

/**
 * Layar Detail Sesi Rapat (DetailScreen).
 *
 * Menampilkan:
 * 1. Informasi rekaman & status pemrosesan.
 * 2. Kontrol Transkripsi On-Device (Mulai / Ulang / Batalkan).
 * 3. Pengaturan Kosakata Kustom (Prompt Biasing) untuk istilah teknis / nama orang.
 * 4. Tab 1: **Transkrip Asli** (Teks verbatim ucapan pembicara dengan penanda waktu per segmen).
 * 5. Tab 2: **Ringkasan** (Ikhtisar dan tindakan tindak lanjut rapat - Fase 3).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    recordingId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(recordingId) {
        if (uiState.recording == null) {
            viewModel.processIntent(DetailIntent.LoadRecording(recordingId))
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is DetailEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                is DetailEffect.NavigateBack -> onNavigateBack()
            }
        }
    }

    DetailContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onIntent = viewModel::processIntent,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailContent(
    uiState: DetailUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onIntent: (DetailIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val recording = uiState.recording

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = recording?.title ?: "Detail Rekaman",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali"
                        )
                    }
                },
                actions = {
                    if (uiState.segments.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                val fullTranscript = uiState.segments.joinToString("\n\n") { segment ->
                                    val timeStart = RecordingFormatters.formatDuration(segment.startMs)
                                    val timeEnd = RecordingFormatters.formatDuration(segment.endMs)
                                    "[$timeStart - $timeEnd] ${segment.text}"
                                }
                                clipboardManager.setText(AnnotatedString(fullTranscript))
                            }
                        ) {
                            Text("Salin Semua")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        if (uiState.isLoading && recording == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (recording == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = uiState.errorMessage ?: "Rekaman tidak ditemukan",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 1. Kartu Metadata & Status
            MetadataCard(
                recording = recording,
                onEditVocabulary = { onIntent(DetailIntent.OpenVocabularyDialog) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // 2. Bilah Progres Pemrosesan & Kontrol Transkripsi
            TranscriptionControlSection(
                uiState = uiState,
                onIntent = onIntent,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Tab Navigasi: Tab 1 "Transkrip Asli" (Verbatim) & Tab 2 "Ringkasan"
            TabRow(
                selectedTabIndex = uiState.selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = uiState.selectedTab == DetailTab.TRANSCRIPT,
                    onClick = { onIntent(DetailIntent.SelectTab(DetailTab.TRANSCRIPT)) },
                    text = {
                        val countLabel = if (uiState.segments.isNotEmpty()) " (${uiState.segments.size})" else ""
                        Text("Transkrip Asli$countLabel", fontWeight = FontWeight.SemiBold)
                    }
                )
                Tab(
                    selected = uiState.selectedTab == DetailTab.SUMMARY,
                    onClick = { onIntent(DetailIntent.SelectTab(DetailTab.SUMMARY)) },
                    text = {
                        Text("Ringkasan (Fase 3)", fontWeight = FontWeight.SemiBold)
                    }
                )
            }

            // 4. Konten Sesuai Tab yang Dipilih
            when (uiState.selectedTab) {
                DetailTab.TRANSCRIPT -> {
                    TranscriptTabContent(
                        segments = uiState.segments,
                        isTranscribing = uiState.isTranscribing,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                DetailTab.SUMMARY -> {
                    SummaryTabContent(
                        recording = recording,
                        segmentCount = uiState.segments.size,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    // Dialog Pengaturan Kosakata Kustom (Prompt Biasing)
    if (uiState.showVocabularyDialog) {
        CustomVocabularyDialog(
            vocabularyInput = uiState.vocabularyInput,
            onInputChange = { onIntent(DetailIntent.UpdateVocabularyInput(it)) },
            onSave = { onIntent(DetailIntent.SaveVocabulary) },
            onDismiss = { onIntent(DetailIntent.DismissVocabularyDialog) }
        )
    }

    // Dialog Konfirmasi Unduh Model Whisper
    if (uiState.showModelDownloadDialog) {
        ModelDownloadConfirmDialog(
            onConfirm = { onIntent(DetailIntent.ConfirmModelDownload) },
            onDismiss = { onIntent(DetailIntent.DismissModelDownloadDialog) }
        )
    }
}

/**
 * Kartu ringkasan metadata audio sesi rapat.
 */
@Composable
private fun MetadataCard(
    recording: Recording,
    onEditVocabulary: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
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
                Text(
                    text = RecordingFormatters.formatDateTime(recording.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                StatusBadge(status = recording.status)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Durasi: ${RecordingFormatters.formatDuration(recording.durationMs)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Ukuran: ${RecordingFormatters.formatFileSize(recording.fileSizeBytes)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Menampilkan kosakata kustom jika ada
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val vocab = recording.customVocabulary
                if (!vocab.isNullOrBlank()) {
                    Text(
                        text = "Kosakata: $vocab",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Text(
                        text = "Belum ada kosakata kustom",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }

                IconButton(
                    onClick = onEditVocabulary,
                    modifier = Modifier.height(28.dp).width(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Atur Kosakata",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.height(18.dp).width(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Bilah progres dan tombol aksi transkripsi / pembatalan / pengunduhan model.
 */
@Composable
private fun TranscriptionControlSection(
    uiState: DetailUiState,
    onIntent: (DetailIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            // 1. Indikator Pengunduhan Model AI
            AnimatedVisibility(visible = uiState.isModelDownloading) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    val percent = (uiState.modelDownloadProgress * 100).toInt().coerceIn(0, 100)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Mengunduh Model Whisper Turbo...",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "$percent%",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { uiState.modelDownloadProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 2. Indikator Transkripsi Suara & Tombol Batalkan
            AnimatedVisibility(visible = uiState.isTranscribing) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    val percent = (uiState.transcriptionProgress * 100).toInt().coerceIn(0, 100)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Mentranskrip audio on-device...",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "$percent%",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { uiState.transcriptionProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { onIntent(DetailIntent.CancelTranscription) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Batalkan",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Batalkan Transkripsi", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // 3. Tombol Eksekusi jika tidak sedang sibuk
            if (!uiState.isTranscribing && !uiState.isModelDownloading) {
                val isCompleted = uiState.recording?.status == RecordingStatus.COMPLETED
                val buttonText = if (isCompleted) "Transkripsi Ulang" else "Mulai Transkripsi"
                val buttonIcon = if (isCompleted) Icons.Default.Refresh else Icons.Default.PlayArrow

                Button(
                    onClick = { onIntent(DetailIntent.StartTranscription) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Dimens.RadiusPill)
                ) {
                    Icon(imageVector = buttonIcon, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = buttonText, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Tampilan konten Tab 1: Transkrip Asli (Verbatim).
 */
@Composable
private fun TranscriptTabContent(
    segments: List<com.catetai.app.domain.model.TranscriptSegment>,
    isTranscribing: Boolean,
    modifier: Modifier = Modifier
) {
    if (segments.isEmpty()) {
        Box(
            modifier = modifier.padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isTranscribing) "Sedang memproses suara..." else "Belum ada transkrip",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isTranscribing) {
                        "Teks transkrip verbatim akan muncul di sini secara real-time saat selesai dianalisis."
                    } else {
                        "Tekan tombol 'Mulai Transkripsi' di atas untuk mengubah rekaman suara menjadi teks asli lengkap dengan penanda waktu."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = segments,
                key = { it.id }
            ) { segment ->
                TranscriptSegmentItem(segment = segment)
            }
        }
    }
}

/**
 * Tampilan konten Tab 2: Ringkasan Rapat & Action Items (Fase 3).
 */
@Composable
private fun SummaryTabContent(
    recording: Recording,
    segmentCount: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Ringkasan Rapat Cerdas (Fase 3)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Fitur ringkasan eksekutif, analisis sentimen, keputusan penting, serta daftar tindak lanjut (action items) akan diproses menggunakan On-Device LLM (Gemini Nano) pada Fase 3.",
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 20.sp
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Statistik Transkrip",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Segmen Bicara", style = MaterialTheme.typography.bodyMedium)
                    Text("$segmentCount segmen", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Durasi Rapat", style = MaterialTheme.typography.bodyMedium)
                    Text(RecordingFormatters.formatDuration(recording.durationMs), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
