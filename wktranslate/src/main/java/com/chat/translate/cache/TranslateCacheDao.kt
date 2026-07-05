package com.chat.translate.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TranslateCacheDao {
    @Query("SELECT * FROM translation_cache WHERE cacheKey = :cacheKey LIMIT 1")
    suspend fun findByKey(cacheKey: String): TranslateCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(entity: TranslateCacheEntity)

    @Query("UPDATE translation_cache SET hitCount = hitCount + 1, lastAccessAt = :now WHERE cacheKey = :cacheKey")
    suspend fun markHit(cacheKey: String, now: Long)

    @Query("SELECT COUNT(*) FROM translation_cache")
    suspend fun count(): Int

    @Query("DELETE FROM translation_cache")
    suspend fun clearAll()

    @Query("DELETE FROM translation_cache WHERE id IN (SELECT id FROM translation_cache ORDER BY hitCount ASC, lastAccessAt ASC LIMIT :deleteCount)")
    suspend fun deleteColdest(deleteCount: Int)
}
