package com.catetai.app.legacy.recorder.di

import com.catetai.app.legacy.recorder.AudioRecorder
import com.catetai.app.legacy.recorder.AudioRecorderManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LegacyAudioModule {

    @Binds
    @Singleton
    abstract fun bindAudioRecorder(
        audioRecorderManager: AudioRecorderManager
    ): AudioRecorder
}
