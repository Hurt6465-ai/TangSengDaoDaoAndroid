package com.chat.translate.cache

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TranslateCacheEntity::class], version = 1, exportSchema = false)
abstract class TranslateCacheDatabase : RoomDatabase() {
    abstract fun dao(): TranslateCacheDao

    companion object {
        @Volatile
        private var INSTANCE: TranslateCacheDatabase? = null

        fun get(context: Context): TranslateCacheDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TranslateCacheDatabase::class.java,
                    "wktranslate_cache.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
