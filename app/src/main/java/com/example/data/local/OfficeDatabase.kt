package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DocumentEntity::class,
        DocumentVersionEntity::class,
        FontRecordEntity::class,
        TemplateEntity::class,
        ErrorLogEntity::class,
        SyncHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class OfficeDatabase : RoomDatabase() {
    abstract fun officeDao(): OfficeDao

    companion object {
        @Volatile
        private var INSTANCE: OfficeDatabase? = null

        fun getInstance(context: Context): OfficeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OfficeDatabase::class.java,
                    "van_phong_pro_office.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
