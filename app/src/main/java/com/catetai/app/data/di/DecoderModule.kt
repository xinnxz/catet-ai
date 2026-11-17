package com.catetai.app.data.di

import com.catetai.app.engine.decoder.AudioDecoder
import com.catetai.app.engine.decoder.MediaCodecDecoder
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Modul Hilt untuk binding dependensi dekoder audio.
 * Menyediakan implementasi [MediaCodecDecoder] sebagai implementasi utama dari [AudioDecoder].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DecoderModule {

    @Binds
    @Singleton
    abstract fun bindAudioDecoder(
        mediaCodecDecoder: MediaCodecDecoder
    ): AudioDecoder
}
