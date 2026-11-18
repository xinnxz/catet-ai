package com.catetai.app.data.di

import com.catetai.app.data.repository.RecordingRepositoryImpl
import com.catetai.app.data.repository.TranscriptRepositoryImpl
import com.catetai.app.domain.repository.RecordingRepository
import com.catetai.app.domain.repository.TranscriptRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Modul Hilt untuk binding interface repositori di domain layer
 * ke implementasi konkretnya di data layer.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindRecordingRepository(
        recordingRepositoryImpl: RecordingRepositoryImpl
    ): RecordingRepository

    @Binds
    @Singleton
    abstract fun bindTranscriptRepository(
        transcriptRepositoryImpl: TranscriptRepositoryImpl
    ): TranscriptRepository
}
