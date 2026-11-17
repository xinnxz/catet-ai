package com.catetai.app.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Modul Hilt penyedia [CoroutineDispatcher] untuk seluruh layer aplikasi.
 *
 * Setiap dispatcher ditandai dengan qualifier ([IoDispatcher], [DefaultDispatcher])
 * agar Dagger bisa membedakan binding ketika ada dua atau lebih dispatcher yang di-inject.
 *
 * - [Dispatchers.IO]: Untuk operasi I/O (disk, database Room, jaringan, SharedPreferences).
 * - [Dispatchers.Default]: Untuk komputasi CPU-bound (TextRank/TF-IDF, pemrosesan audio buffer).
 */
@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
