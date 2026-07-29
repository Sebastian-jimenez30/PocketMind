package com.pocketmind.data.repository

import com.pocketmind.data.local.dao.CustomCategoryDao
import com.pocketmind.data.local.entity.CustomCategoryEntity
import com.pocketmind.shared.domain.model.CustomCategory
import com.pocketmind.shared.domain.repository.CustomCategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomCustomCategoryRepository @Inject constructor(
    private val dao: CustomCategoryDao,
) : CustomCategoryRepository {
    override fun observeAll(): Flow<List<CustomCategory>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getById(id: String): CustomCategory? =
        dao.getById(id)?.toDomain()

    override suspend fun save(category: CustomCategory) {
        dao.upsert(category.toEntity())
    }

    override suspend fun delete(id: String) {
        dao.deleteById(id)
    }
}

private fun CustomCategoryEntity.toDomain() = CustomCategory(
    id = id,
    name = name,
    createdAtEpochMillis = createdAtEpochMillis,
)

private fun CustomCategory.toEntity() = CustomCategoryEntity(
    id = id,
    name = name,
    createdAtEpochMillis = createdAtEpochMillis,
)
