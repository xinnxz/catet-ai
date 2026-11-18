package com.catetai.app.data.di

import android.content.Context
import com.catetai.app.core.crypto.AudioEncryptor
import com.catetai.app.core.crypto.TinkAudioEncryptor
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Modul Hilt untuk menyediakan dependensi kriptografi (Google Tink Streaming AEAD).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CryptoModule {

    @Binds
    @Singleton
    abstract fun bindAudioEncryptor(
        tinkAudioEncryptor: TinkAudioEncryptor
    ): AudioEncryptor

    companion object {
        private const val KEYSET_NAME = "catet_audio_streaming_aead_keyset"
        private const val PREF_FILE_NAME = "catet_audio_crypto_prefs"
        private const val MASTER_KEY_URI = "android-keystore://catet_audio_master_key"

        @Provides
        @Singleton
        fun provideStreamingAead(
            @ApplicationContext context: Context
        ): StreamingAead {
            StreamingAeadConfig.register()
            val keysetHandle = AndroidKeysetManager.Builder()
                .withSharedPref(context, KEYSET_NAME, PREF_FILE_NAME)
                .withKeyTemplate(KeyTemplates.get("AES256_GCM_HKDF_4KB"))
                .withMasterKeyUri(MASTER_KEY_URI)
                .build()
                .keysetHandle
            return keysetHandle.getPrimitive(StreamingAead::class.java)
        }
    }
}
