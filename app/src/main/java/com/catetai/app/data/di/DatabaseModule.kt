package com.catetai.app.data.di

import android.content.Context
import androidx.room.Room
import com.catetai.app.data.local.CatetAiDatabase
import com.catetai.app.data.local.dao.RecordingDao
import com.catetai.app.data.local.dao.SummaryDao
import com.catetai.app.data.local.dao.TranscriptDao
import com.catetai.app.data.local.security.DatabaseKeyManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SupportFactory
import javax.inject.Singleton

/**
 * DatabaseModule — Modul Hilt yang menyediakan database Room terenkripsi dan semua DAO-nya.
 *
 * # Migrasi Eksplisit (.addMigrations)
 * Kita mendaftarkan [CatetAiDatabase.MIGRATION_2_3] secara eksplisit, bukan hanya
 * mengandalkan fallbackToDestructiveMigration(). Ini penting karena:
 * - Migrasi eksplisit dijalankan TERLEBIH DAHULU jika versi cocok (2 → 3).
 * - fallbackToDestructiveMigration() hanya digunakan sebagai jaring pengaman
 *   jika ada jalur migrasi yang tidak tercakup (misalnya upgrade dari v1 langsung ke v3).
 *
 * # Singleton
 * Database dan semua DAO diberi scope @Singleton — satu instance untuk seluruh
 * siklus hidup aplikasi. Ini adalah praktik standar Room + Hilt.
 *
 * # Enkripsi (SQLCipher)
 * SupportFactory menerima passphrase dari DatabaseKeyManager yang menyimpan kunci
 * di Android Keystore (hardware-backed jika tersedia). CatetAiDatabase sendiri tidak
 * tahu tentang enkripsi — ini adalah Separation of Concerns yang baik.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        keyManager: DatabaseKeyManager
    ): CatetAiDatabase {
        val passphrase = keyManager.getOrCreatePassphrase()
        val factory = SupportFactory(passphrase)

        return Room.databaseBuilder(
            context,
            CatetAiDatabase::class.java,
            CatetAiDatabase.DATABASE_NAME
        )
            .openHelperFactory(factory)
            // Migrasi eksplisit v2 → v3: menghapus tabel lama dan membuat skema baru.
            // Data lama (skema recorder v1/v2) tidak dipertahankan karena pivot produk.
            .addMigrations(CatetAiDatabase.MIGRATION_2_3)
            // Jaring pengaman: jika ada jalur migrasi lain yang tidak tercakup,
            // database dihapus dan dibuat ulang. Hanya berlaku selama development.
            // HAPUS baris ini sebelum rilis production pertama.
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideRecordingDao(database: CatetAiDatabase): RecordingDao =
        database.recordingDao()

    @Provides
    @Singleton
    fun provideTranscriptDao(database: CatetAiDatabase): TranscriptDao =
        database.transcriptDao()

    @Provides
    @Singleton
    fun provideSummaryDao(database: CatetAiDatabase): SummaryDao =
        database.summaryDao()
}
