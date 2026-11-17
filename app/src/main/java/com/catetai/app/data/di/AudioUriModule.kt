package com.catetai.app.data.di

import com.catetai.app.core.util.AndroidAudioUriReader
import com.catetai.app.core.util.AudioUriReader
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Modul Hilt untuk binding abstraksi [AudioUriReader].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AudioUriModule {

    @Binds
    @Singleton
    abstract fun bindAudioUriReader(
        androidAudioUriReader: AndroidAudioUriReader
    ): AudioUriReader
}
