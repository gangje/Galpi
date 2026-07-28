package com.galpi.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoIndexDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PhotoIndexEntity)

    @Query("SELECT mediaId FROM photo_index")
    suspend fun indexedIds(): List<Long>

    @Query("SELECT COUNT(*) FROM photo_index")
    fun countFlow(): Flow<Int>

    @Query("SELECT * FROM photo_index")
    suspend fun all(): List<PhotoIndexEntity>

    @Query("DELETE FROM photo_index WHERE mediaId IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}
