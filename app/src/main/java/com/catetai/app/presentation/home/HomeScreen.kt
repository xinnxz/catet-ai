package com.catetai.app.presentation.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.catetai.app.domain.model.Recording
import com.catetai.app.domain.model.RecordingStatus
import com.catetai.app.presentation.home.components.EmptyRecordingsView
import com.catetai.app.presentation.home.components.RecordingItemCard
import com.catetai.app.presentation.theme.CatetAITheme
import com.catetai.app.presentation.theme.Dimens

/**
 * Layar Utama (HomeScreen) Catet AI.
 *
 * Menampilkan:
 * 1. Daftar seluruh rekaman yang diimpor, diurutkan dari yang terbaru.
 * 2. Badge status semantik (Siap Diproses, Mempersiapkan, Mentranskrip, Merangkum, Selesai, Gagal).
 * 3. Fitur geser-untuk-menghapus (swipe-to-delete) yang diblokir saat pemrosesan aktif.
 * 4. Extended FAB "＋ Impor Rekaman" untuk memilih berkas dari Storage Access Framework (SAF).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToRecord: () -> Unit = {},
    onNavigateToDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Launcher pemilih berkas sistem (SAF OpenDocument) untuk format audio
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.processIntent(HomeIntent.ImportAudio(it)) }
    }

    // Menangani efek samping satu kali (one-shot effect) dari ViewModel
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is HomeEffect.NavigateToDetail -> onNavigateToDetail(effect.recordingId)
                is HomeEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    HomeContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onTriggerImport = {
            audioPickerLauncher.launch(
                arrayOf(
                    "audio/*",
                    "application/ogg",
                    "application/x-flac"
                )
            )
        },
        onNavigateToDetail = onNavigateToDetail,
        onDeleteRecording = { id ->
            viewModel.processIntent(HomeIntent.DeleteRecording(id))
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    uiState: HomeUiState,
    snackbarHostState: SnackbarHostState,
    onTriggerImport: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onDeleteRecording: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var recordingPendingDelete by remember { mutableStateOf<Recording?>(null) }

    // Dialog konfirmasi penghapusan rekaman
    if (recordingPendingDelete != null) {
        AlertDialog(
            onDismissRequest = { recordingPendingDelete = null },
            title = {
                Text(
                    text = "Hapus Rekaman?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Rekaman \"${recordingPendingDelete?.title}\" beserta berkas audio dan transkrip terkait akan dihapus secara permanen dari perangkat.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        recordingPendingDelete?.let { onDeleteRecording(it.id) }
                        recordingPendingDelete = null
                    }
                ) {
                    Text(
                        text = "Hapus",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { recordingPendingDelete = null }) {
                    Text("Batal")
                }
            }
        )
    }

    // Dialog loading indikator saat penyalinan berkas impor berlangsung (PRD AC-01.4 / F1-06)
    if (uiState.isImporting) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = {
                Text(
                    text = "Mengimpor Berkas Audio",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    if (uiState.importProgress > 0f) {
                        LinearProgressIndicator(
                            progress = { uiState.importProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Menyalin berkas (${(uiState.importProgress * 100).toInt()}%)...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Mempersiapkan penyalinan berkas audio...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Catet AI",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onTriggerImport,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Impor Rekaman"
                    )
                },
                text = {
                    Text(
                        text = "Impor Rekaman",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState.isLoading && uiState.recordings.isEmpty() -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                uiState.recordings.isEmpty() -> {
                    EmptyRecordingsView(
                        modifier = Modifier.fillMaxSize()
                    )
                }

                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            top = Dimens.PaddingSmall,
                            bottom = 88.dp // Menghindari tertutup FAB di kanan bawah
                        ),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = uiState.recordings,
                            key = { it.id }
                        ) { recording ->
                            RecordingItemCard(
                                recording = recording,
                                onClick = { onNavigateToDetail(recording.id) },
                                onRequestDelete = { recordingPendingDelete = recording },
                                modifier = Modifier
                                    .padding(
                                        horizontal = Dimens.PaddingMedium,
                                        vertical = Dimens.PaddingSmall
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Home Screen - Daftar Terisi", showBackground = true)
@Composable
private fun HomeScreenWithItemsPreview() {
    CatetAITheme {
        HomeContent(
            uiState = HomeUiState(
                recordings = listOf(
                    Recording(
                        id = "rec-1",
                        title = "Rapat Sinkronisasi Sprint 1",
                        originalFileName = "sync_sprint1.m4a",
                        audioFileName = "audio_1.enc",
                        durationMs = 1_935_000L,
                        fileSizeBytes = 61_920_000L,
                        status = RecordingStatus.COMPLETED,
                        importedAt = 1794907800000L,
                        createdAt = 1794907800000L
                    ),
                    Recording(
                        id = "rec-2",
                        title = "Diskusi Arsitektur NDK Whisper",
                        originalFileName = "ndk_whisper.m4a",
                        audioFileName = "audio_2.enc",
                        durationMs = 900_000L,
                        fileSizeBytes = 28_800_000L,
                        status = RecordingStatus.TRANSCRIBING,
                        importedAt = 1794821400000L,
                        createdAt = 1794821400000L
                    )
                ),
                isLoading = false
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onTriggerImport = {},
            onNavigateToDetail = {},
            onDeleteRecording = {}
        )
    }
}

@Preview(name = "Home Screen - Kosong", showBackground = true)
@Composable
private fun HomeScreenEmptyPreview() {
    CatetAITheme {
        HomeContent(
            uiState = HomeUiState(
                recordings = emptyList(),
                isLoading = false
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onTriggerImport = {},
            onNavigateToDetail = {},
            onDeleteRecording = {}
        )
    }
}

