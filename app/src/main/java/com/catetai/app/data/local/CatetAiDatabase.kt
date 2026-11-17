package com.catetai.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.catetai.app.data.local.dao.RecordingDao
import com.catetai.app.data.local.entity.RecordingEntity

@Database(
    entities = [RecordingEntity::class],
    version = 1,
    exportSchema = false
)
abstract class CatetAiDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao

    companion object {
        const val DATABASE_NAME = "catet_ai_encrypted.db"
    }
}
