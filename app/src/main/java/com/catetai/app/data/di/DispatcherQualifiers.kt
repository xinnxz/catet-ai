package com.catetai.app.data.di

import javax.inject.Qualifier

/**
 * Qualifier Hilt untuk membedakan [kotlinx.coroutines.CoroutineDispatcher] yang di-inject.
 *
 * Tanpa qualifier, Dagger hanya melihat tipe [CoroutineDispatcher] dan tidak bisa
 * membedakan apakah yang diminta adalah Dispatchers.IO atau Dispatchers.Default.
 * Ketika nanti ada modul lain yang menyediakan Dispatchers.Default (misalnya untuk
 * komputasi CPU-bound seperti TextRank/TF-IDF), dua @Provides dengan tipe kembalian
 * sama tanpa qualifier akan menyebabkan error kompilasi Dagger [DuplicateBindings].
 */

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher
