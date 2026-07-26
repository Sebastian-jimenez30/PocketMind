package com.pocketmind.shared.domain.usecase

import com.pocketmind.shared.domain.repository.FinancialSetupRepository
import kotlinx.coroutines.flow.Flow

class ObserveFinancialSetupCompletedUseCase(
    private val repository: FinancialSetupRepository,
) {
    operator fun invoke(): Flow<Boolean> = repository.observeIsCompleted()
}
