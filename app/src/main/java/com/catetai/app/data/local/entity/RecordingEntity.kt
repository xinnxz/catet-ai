package com.catetai.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class RecordingStatus {
    RECORDING,
    STOPPED,
    PROCESSING,
    TRANSCRIBED,
    SUMMARIZED,
    ERROR
}

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val audioFilePath: String,
    val durationMs: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val sampleRate: Int = 16000,
    val status: RecordingStatus = RecordingStatus.STOPPED,
    val customVocabulary: String? = null,
    val processingEngine: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
