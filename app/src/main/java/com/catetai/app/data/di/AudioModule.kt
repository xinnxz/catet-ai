package com.catetai.app.data.di

import com.catetai.app.core.audio.AudioRecorder
import com.catetai.app.core.audio.AudioRecorderManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AudioModule {

    @Binds
    @Singleton
    abstract fun bindAudioRecorder(
        audioRecorderManager: AudioRecorderManager
    ): AudioRecorder
}
