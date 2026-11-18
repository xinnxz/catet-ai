package com.catetai.app.data.di

import com.catetai.app.engine.summarizer.ExtractiveSummarizer
import com.catetai.app.engine.summarizer.SummarizerEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Modul Hilt untuk binding dependensi perangkum transkrip.
 * Mengikat [ExtractiveSummarizer] sebagai implementasi utama [SummarizerEngine].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SummarizerModule {

    @Binds
    @Singleton
    abstract fun bindSummarizerEngine(
        extractiveSummarizer: ExtractiveSummarizer
    ): SummarizerEngine
}
