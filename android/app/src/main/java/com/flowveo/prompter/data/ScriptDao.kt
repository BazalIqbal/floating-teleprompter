package com.flowveo.prompter.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ScriptDao {

    /** Most recently used first, then never-used scripts by last edit. */
    @Query("SELECT * FROM scripts ORDER BY lastUsedAt IS NULL, lastUsedAt DESC, updatedAt DESC")
    fun observeAll(): Flow<List<ScriptEntity>>

    @Query("SELECT * FROM scripts WHERE id = :id")
    fun observe(id: Long): Flow<ScriptEntity?>

    @Query("SELECT * FROM scripts WHERE id = :id")
    suspend fun get(id: Long): ScriptEntity?

    @Insert
    suspend fun insert(script: ScriptEntity): Long

    @Update
    suspend fun update(script: ScriptEntity)

    @Query("DELETE FROM scripts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE scripts SET lastUsedAt = :time WHERE id = :id")
    suspend fun markUsed(id: Long, time: Long)

    @Query("SELECT * FROM scripts ORDER BY createdAt")
    suspend fun getAll(): List<ScriptEntity>

    @Query("SELECT COUNT(*) FROM scripts")
    suspend fun count(): Int
}
