package com.pocketmind.shared.domain.repository

import com.pocketmind.shared.domain.model.CustomCategory
import kotlinx.coroutines.flow.Flow

interface CustomCategoryRepository {
    fun observeAll(): Flow<List<CustomCategory>>
    suspend fun getById(id: String): CustomCategory?
    suspend fun save(category: CustomCategory)
    suspend fun delete(id: String)
}
