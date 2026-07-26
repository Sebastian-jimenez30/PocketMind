package com.pocketmind.shared.domain.usecase

import com.pocketmind.shared.domain.model.FinancialAccount
import com.pocketmind.shared.domain.repository.FinancialAccountRepository
import kotlinx.coroutines.flow.Flow

class ObserveActiveFinancialAccountsUseCase(private val repository: FinancialAccountRepository) {
    operator fun invoke(): Flow<List<FinancialAccount>> = repository.observeActive()
}

class GetFinancialAccountUseCase(private val repository: FinancialAccountRepository) {
    suspend operator fun invoke(id: String): FinancialAccount? = repository.getById(id)
}

class SaveFinancialAccountUseCase(private val repository: FinancialAccountRepository) {
    suspend operator fun invoke(account: FinancialAccount) = repository.save(account)
}
