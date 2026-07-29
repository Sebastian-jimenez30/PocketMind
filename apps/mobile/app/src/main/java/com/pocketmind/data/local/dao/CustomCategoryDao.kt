package com.pocketmind.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.pocketmind.data.local.entity.CustomCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomCategoryDao {
    @Query("SELECT * FROM custom_categories ORDER BY name ASC")
    fun observeAll(): Flow<List<CustomCategoryEntity>>

    @Query("SELECT * FROM custom_categories WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CustomCategoryEntity?

    @Upsert
    suspend fun upsert(category: CustomCategoryEntity)

    @Query("DELETE FROM custom_categories WHERE id = :id")
    suspend fun deleteById(id: String)
}
