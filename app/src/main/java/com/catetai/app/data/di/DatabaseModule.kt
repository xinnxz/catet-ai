package com.catetai.app.data.di

import android.content.Context
import androidx.room.Room
import com.catetai.app.data.local.CatetAiDatabase
import com.catetai.app.data.local.dao.RecordingDao
import com.catetai.app.data.local.security.DatabaseKeyManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SupportFactory
import javax.inject.Singleton

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
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideRecordingDao(database: CatetAiDatabase): RecordingDao {
        return database.recordingDao()
    }
}
