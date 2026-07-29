package com.pocketmind.shared.domain.usecase

import com.pocketmind.shared.domain.model.CustomCategory
import com.pocketmind.shared.domain.repository.CustomCategoryRepository
import kotlinx.coroutines.flow.Flow

class ObserveCustomCategoriesUseCase(
    private val repository: CustomCategoryRepository,
) {
    operator fun invoke(): Flow<List<CustomCategory>> = repository.observeAll()
}

class SaveCustomCategoryUseCase(
    private val repository: CustomCategoryRepository,
) {
    suspend operator fun invoke(category: CustomCategory) = repository.save(category)
}

class DeleteCustomCategoryUseCase(
    private val repository: CustomCategoryRepository,
) {
    suspend operator fun invoke(id: String) = repository.delete(id)
}
