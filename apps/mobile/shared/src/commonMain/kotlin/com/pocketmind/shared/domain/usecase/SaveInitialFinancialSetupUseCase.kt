package com.pocketmind.shared.domain.usecase

import com.pocketmind.shared.domain.model.FinancialSetup
import com.pocketmind.shared.domain.repository.FinancialSetupRepository

class SaveInitialFinancialSetupUseCase(
    private val repository: FinancialSetupRepository,
) {
    suspend operator fun invoke(setup: FinancialSetup) = repository.saveInitialSetup(setup)
}
